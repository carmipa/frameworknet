/**
 * INVENTARIO DA SUPERFICIE DO SITE — extraido do codigo-fonte, nunca escrito a mao.
 *
 * PROPOSITO DE NEGOCIO: a varredura so vale o que vale a lista que ela percorre.
 * Lista escrita de memoria testa o que o autor lembra, e o defeito mora
 * exatamente no que ele esqueceu. Este script le os `@Path` das resources Java e
 * os templates Qute e produz `scripts/inventario.json`: paginas, APIs, abas,
 * campos, modais, gatilhos htmx, links internos e arquivos JS. A varredura
 * (`varredura.mjs`) consome esse arquivo — nao decide nada sozinha.
 *
 * INVARIANTES DO DOMINIO:
 *  - toda rota anotada em `presentation/*Resource.java` aparece no inventario,
 *    classificada (pagina navegavel · API · download · mutante);
 *  - `{#include}` e resolvido recursivamente, senao os campos e abas que moram
 *    em partials sumiriam do inventario da pagina que os renderiza;
 *  - rota que MUDA ESTADO (logout, sincronizar dataset, limpar console) nasce na
 *    lista `mutantes` e nunca na de navegacao: visitar uma delas no meio da
 *    varredura muda o que todas as telas seguintes mostram, e o instrumento
 *    passa a medir outra coisa;
 *  - contagem zero em qualquer categoria principal e tratada como falha do
 *    extrator, nao como "o projeto nao tem isso".
 *
 * COMPORTAMENTO EM CASO DE FALHA: sai com codigo 2 (NAO VERIFICOU) quando nao
 * acha a raiz do projeto, quando nenhuma resource e encontrada ou quando alguma
 * categoria obrigatoria vem vazia. Nunca grava um inventario parcial em silencio:
 * inventario pela metade faz a varredura seguinte reportar "tudo certo" sobre o
 * que ela nem olhou.
 *
 * USO:  node scripts/inventario.mjs
 */
import { readdirSync, statSync, readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join, relative, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const AQUI = dirname(fileURLToPath(import.meta.url));
const RAIZ = join(AQUI, '..');
const JAVA = join(RAIZ, 'src/main/java');
const TEMPLATES = join(RAIZ, 'src/main/resources/templates');
const ESTATICOS = join(RAIZ, 'src/main/resources/META-INF/resources');
const SAIDA = join(AQUI, 'inventario.json');

/** Rotas que alteram estado do servidor ou encerram a sessao da varredura. */
const MUTANTE = /\/(sair|logout|limpar|sincronizar|callback|ingest)\b/;
/** Rotas que devolvem arquivo, nao tela: navegar nelas so baixa bytes. */
const DOWNLOAD = /\/(export|exportar)\b|\.(csv|zip|pdf|json|png|webmanifest)$/;

/**
 * Percorre um diretorio recursivamente devolvendo os arquivos com a extensao dada.
 *
 * PROPOSITO: alcancar todo arquivo de codigo sem depender de glob externo.
 * INVARIANTE: nunca segue para fora do diretorio recebido.
 * FALHA: diretorio inexistente devolve lista vazia (o chamador e quem decide se
 * lista vazia e aceitavel — aqui sozinha ela nao aborta nada).
 */
function arquivos(dir, ext) {
  let achados = [];
  let entradas;
  try {
    entradas = readdirSync(dir);
  } catch {
    return achados;
  }
  for (const nome of entradas) {
    const caminho = join(dir, nome);
    if (statSync(caminho).isDirectory()) {
      achados = achados.concat(arquivos(caminho, ext));
    } else if (nome.endsWith(ext)) {
      achados.push(caminho);
    }
  }
  return achados;
}

const barra = (p) => p.replace(/\\/g, '/');
const normalizarRota = (base, sufixo) => ('/' + `${base}/${sufixo}`.split('/').filter(Boolean).join('/')) || '/';

// ---------------------------------------------------------------------------
// 1. ROTAS — lidas das anotacoes JAX-RS
// ---------------------------------------------------------------------------

/**
 * Extrai as rotas HTTP de uma resource JAX-RS.
 *
 * PROPOSITO DE NEGOCIO: e a lista de portas de entrada do sistema. Toda tela e
 * toda API do site nasce de um `@Path`; o que nao esta aqui nao existe para o
 * usuario, e o que esta e nunca foi visitado e area cega.
 *
 * INVARIANTES: a rota final e a concatenacao do `@Path` da classe com o do
 * metodo; um metodo sem verbo HTTP e ignorado (nao e endpoint); o `@Produces`
 * do metodo tem precedencia sobre o da classe.
 *
 * COMPORTAMENTO EM CASO DE FALHA: arquivo sem `@Path` de classe devolve lista
 * vazia — nao lanca. Rota com `{parametro}` de caminho e marcada `parametrizada`
 * e fica fora da navegacao automatica (nao ha valor honesto para inventar).
 *
 * @param {string} fonte conteudo do arquivo .java
 * @param {string} arquivo caminho relativo, so para rastreio no relatorio
 * @returns {Array<object>} rotas com metodo, caminho, tipo e origem
 */
function rotasDaResource(fonte, arquivo) {
  const classe = fonte.match(/@Path\("([^"]*)"\)\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*public\s+class/);
  if (!classe) { return []; }
  const basePath = classe[1];
  const produzClasse = (fonte.match(/@Produces\(([^)]*)\)\s*public\s+class/) || [])[1] || '';

  // As duas formas convivem no projeto: `@Location(...)` importado e
  // `@io.quarkus.qute.Location(...)` qualificado. Casar so uma delas deixaria
  // paginas inteiras (BGP, SSH) sem template e, portanto, sem inventario.
  const locais = [...fonte.matchAll(/@(?:io\.quarkus\.qute\.)?Location\("([^"]+)"\)\s*\n\s*Template\s+(\w+)/g)]
    .map((m) => ({ template: m[1], campo: m[2] }));
  const naoPartial = locais.filter((l) => !l.template.includes('/partials/'));

  /**
   * Descobre qual template um metodo renderiza, seguindo um salto de helper.
   *
   * PROPOSITO: varios resources deste projeto nao devolvem o Template direto —
   * passam por `render(vm)` ou `comSubMenu(template, slug)`. Parar no corpo do
   * metodo publico deixaria `/analise`, `/calculadora` e `/resolucao-problemas`
   * sem superficie inventariada, que e justamente onde ficam as abas e os campos.
   *
   * INVARIANTE: so um salto de helper privado — cadeia mais longa cai no
   * fallback, e fallback so vale quando a classe tem UM unico template de pagina
   * (ai nao ha como errar de alvo).
   *
   * FALHA: nao conseguindo decidir, devolve null; a pagina entra no inventario
   * sem superficie e o portao final acusa a lacuna em vez de inventar mapeamento.
   */
  function templateDoCorpo(corpo) {
    const direto = locais.find((l) => new RegExp(`\\b${l.campo}\\b`).test(corpo));
    if (direto) { return direto.template; }
    for (const chamada of new Set([...corpo.matchAll(/\b(\w+)\s*\(/g)].map((m) => m[1]))) {
      const helper = fonte.match(new RegExp(`private\\s+[\\w<>,.\\s\\[\\]]+\\s+${chamada}\\s*\\([^)]*\\)\\s*\\{`));
      if (!helper) { continue; }
      const trecho = fonte.slice(helper.index, helper.index + 900);
      const achado = locais.find((l) => new RegExp(`\\b${l.campo}\\b`).test(trecho));
      if (achado) { return achado.template; }
    }
    return naoPartial.length === 1 ? naoPartial[0].template : null;
  }

  const rotas = [];
  // Grupo CONTIGUO de anotacoes seguido da assinatura do metodo publico. Quebrar
  // o arquivo por anotacao isolada separaria `@GET` do `@Produces` logo abaixo e
  // classificaria toda pagina HTML como API — foi exatamente o que aconteceu na
  // primeira versao deste extrator, e o portao de categoria vazia denunciou.
  // Varias anotacoes na MESMA linha tambem contam ("@GET @Path(...) @Produces(...)"): so com uma por linha, a
  // pagina /laboratorios/aneis-e-rede ficou fora do inventario e de toda varredura (CONT-S2, 22/10/2026).
  const bloco = /((?:^[ \t]*(?:@[\w.]+(?:\((?:[^()]|\([^()]*\))*\))?[ \t]*)+\r?\n)+)[ \t]*public\s+[\w<>,.\s[\]]+?\s+(\w+)\s*\(/gm;
  for (const achado of fonte.matchAll(bloco)) {
    const anotacoes = achado[1];
    const metodoJava = achado[2];
    const verbo = (anotacoes.match(/@(GET|POST|PUT|DELETE|HEAD)\b/) || [])[1];
    if (!verbo) { continue; }
    const sufixo = (anotacoes.match(/@Path\("([^"]*)"\)/) || [])[1] || '';
    const produz = (anotacoes.match(/@Produces\(([^)]*)\)/) || [])[1] || produzClasse;
    // Corpo aproximado: do fim da assinatura ate a proxima anotacao de metodo.
    const inicio = achado.index + achado[0].length;
    const resto = fonte.slice(inicio);
    const fim = resto.search(/\n[ \t]*@(?:GET|POST|PUT|DELETE|HEAD|Path)\b/);
    const corpo = fim === -1 ? resto : resto.slice(0, fim);
    const template = templateDoCorpo(corpo);

    const caminho = normalizarRota(basePath, sufixo);
    const html = /TEXT_HTML/.test(produz);
    const parametrizada = /[{}]/.test(caminho);

    let tipo;
    if (MUTANTE.test(caminho)) { tipo = 'mutante'; }
    else if (DOWNLOAD.test(caminho)) { tipo = 'download'; }
    else if (verbo === 'GET' && html && !caminho.includes('/api/')) { tipo = 'pagina'; }
    else { tipo = 'api'; }

    rotas.push({ metodo: verbo, caminho, tipo, parametrizada, template, origem: `${barra(arquivo)}#${metodoJava}` });
  }
  return rotas;
}

// ---------------------------------------------------------------------------
// 2. TEMPLATES — resolvidos com os includes, para nao perder partial
// ---------------------------------------------------------------------------

const cacheTemplates = new Map();

/**
 * Le um template Qute com todos os `{#include}` ja embutidos.
 *
 * PROPOSITO: a pagina que o usuario ve e a soma do template com seus partials.
 * Inventariar so o arquivo de topo perderia campos, abas e botoes inteiros — as
 * abas da Analise Didatica e da Calculadora moram todas em partials.
 *
 * INVARIANTE: um mesmo template nunca e expandido duas vezes na mesma cadeia
 * (protege de include circular travando o extrator).
 *
 * FALHA: include apontando para arquivo inexistente vira um comentario
 * `<!-- include-ausente: X -->` no texto expandido e entra em `avisos`; nao
 * interrompe a extracao, porque o resto da pagina ainda precisa ser inventariado.
 *
 * @param {string} rel caminho do template relativo a pasta templates
 * @param {Set<string>} vistos cadeia de includes ja abertos
 * @param {string[]} avisos acumulador de includes quebrados
 * @returns {string} HTML expandido
 */
function templateExpandido(rel, vistos = new Set(), avisos = []) {
  if (vistos.has(rel)) { return ''; }
  vistos.add(rel);
  if (cacheTemplates.has(rel) && vistos.size === 1) { return cacheTemplates.get(rel); }

  let texto;
  try {
    texto = readFileSync(join(TEMPLATES, rel), 'utf8');
  } catch {
    avisos.push(`include-ausente: ${rel}`);
    return `<!-- include-ausente: ${rel} -->`;
  }

  const expandido = texto.replace(/\{#include\s+([^\s}]+)[^}]*\}/g, (_, alvo) =>
    templateExpandido(alvo, new Set(vistos), avisos));

  if (vistos.size === 1) { cacheTemplates.set(rel, expandido); }
  return expandido;
}

const todos = (html, re) => [...html.matchAll(re)].map((m) => m[1]);
const unicos = (lista) => [...new Set(lista)];

/**
 * Inventaria a superficie interativa de um template ja expandido.
 *
 * PROPOSITO DE NEGOCIO: e o contrato do que a pagina deve entregar ao navegador.
 * A varredura compara isto com o que a pagina realmente renderizou — aba
 * declarada no template e ausente na tela e defeito, e sem esta lista ninguem
 * perceberia.
 *
 * INVARIANTES: nada e inferido do nome do arquivo, tudo vem do markup; campo
 * `type=hidden` fica de fora (nao e superficie de uso); link externo nao entra
 * em `links` (a varredura so testa o proprio site).
 *
 * FALHA: template vazio devolve todas as listas vazias — quem decide se isso e
 * aceitavel e o portao final, que reprova o inventario inteiro se as categorias
 * principais zerarem.
 *
 * @param {string} html template com includes resolvidos
 * @returns {object} abas, campos, modais, gatilhos, botoes, links e formularios
 */
function superficieDoTemplate(html) {
  const atrib = (attrs, nome) => (attrs.match(new RegExp(`\\b${nome}="([^"]*)"`)) || [])[1] || null;
  const condicional = mapaCondicional(html);

  // Nem todo controle deste projeto tem `id`. O DataGrid identifica os seus por
  // `data-grid-*`, as abas por `data-tab`. Exigir `id` deixaria a busca, os
  // filtros, a paginacao e os exports de /portas e /protocolos fora da varredura
  // — o inventario diria "0 campos" numa pagina que e feita de campos.
  const seletorDe = (attrs) => {
    const id = atrib(attrs, 'id');
    if (id) { return `#${CSS_ESCAPE(id)}`; }
    const dado = attrs.match(/\b(data-(?:grid|tab|aed|pdv|summary)[\w-]*)="([^"]*)"/);
    if (dado) { return `[${dado[1]}="${dado[2]}"]`; }
    const nome = atrib(attrs, 'name');
    if (nome) { return `[name="${nome}"]`; }
    const hx = attrs.match(/\bhx-(get|post)="([^"]*)"/);
    if (hx) { return `[hx-${hx[1]}="${hx[2]}"]`; }
    return null;
  };

  const campos = [...html.matchAll(/<(input|select|textarea)\b([^>]*)>/gi)]
    .map((m) => ({
      tag: m[1].toLowerCase(),
      seletor: seletorDe(m[2]),
      name: atrib(m[2], 'name'),
      type: atrib(m[2], 'type') || (m[1].toLowerCase() === 'input' ? 'text' : m[1].toLowerCase()),
      classe: atrib(m[2], 'class') || '',
      condicional: condicional(m.index),
    }))
    .filter((c) => c.type !== 'hidden' && c.seletor);

  const gatilhos = [...html.matchAll(/<(button|a)\b([^>]*)>/gi)]
    .map((m) => ({
      tag: m[1].toLowerCase(),
      seletor: seletorDe(m[2]),
      rotulo: atrib(m[2], 'title') || atrib(m[2], 'aria-label') || '',
      htmx: (m[2].match(/\bhx-(?:get|post)="([^"]*)"/) || [])[1] || null,
      condicional: condicional(m.index),
    }))
    .filter((g) => g.seletor);

  return {
    abas: unicos(todos(html, /\bdata-tab="([^"]+)"/g)),
    campos: dedup(campos, (c) => c.seletor),
    // A classe precisa ser exatamente `modal`. Com `\bmodal\b`, `modal-body` e
    // `modal-content` tambem casavam, e a varredura media o CORPO do modal como
    // se fosse o modal: acusava "fora da tela, minusculo 0x0" em /portas e
    // /protocolos — dois achados que nunca existiram.
    modais: unicos([
      ...todos(html, /<div[^>]*class="(?:[^"]*\s)?modal(?:\s[^"]*)?"[^>]*id="([^"]+)"/g),
      ...todos(html, /<div[^>]*id="([^"]+)"[^>]*class="(?:[^"]*\s)?modal(?:\s[^"]*)?"/g),
    ]).map((id) => `#${id}`),
    htmx: unicos(todos(html, /\bhx-(?:get|post)="([^"]+)"/g)),
    gatilhos: dedup(gatilhos, (g) => g.seletor),
    links: unicos(todos(html, /\bhref="(\/[^"#][^"]*)"/g))
      .filter((h) => !/\.(png|ico|css|js|webmanifest)(\?|$)/.test(h)),
    formularios: [...html.matchAll(/<form\b([^>]*)>/gi)].map(([, attrs]) => ({
      action: atrib(attrs, 'action') || '',
      method: (atrib(attrs, 'method') || 'get').toLowerCase(),
    })),
  };
}

/**
 * Diz, para cada posicao do template, se ela esta dentro de um bloco condicional.
 *
 * PROPOSITO DE NEGOCIO: separar "o elemento sumiu da tela" (defeito) de "o
 * elemento so aparece quando ha resultado" (comportamento correto). Sem esta
 * distincao a varredura acusaria como defeito o `#adminKey` de `{#if
 * contingencia}`, os campos de resultado de `{#if resultado}` e cada linha de
 * `{#for}` — enterrando o achado verdadeiro num monte de ruido.
 *
 * INVARIANTES: `{#else}`, `{#elseif}` e `{#is}` NAO abrem bloco novo (sao ramos
 * do bloco corrente); o fechamento anonimo `{/}` fecha o topo da pilha, que e a
 * regra do Qute; so `if`, `for`, `each` e `when` tornam a regiao condicional —
 * `{#insert}` estrutura a pagina, nao a condiciona.
 *
 * COMPORTAMENTO EM CASO DE FALHA: template desbalanceado (fecha mais do que
 * abre) nao lanca — a pilha simplesmente esvazia e dali para frente as posicoes
 * contam como incondicionais, que e o lado seguro: no maximo se reporta um
 * achado a mais, nunca se esconde um.
 *
 * @param {string} html template ja expandido
 * @returns {(indice:number)=>boolean}
 */
function mapaCondicional(html) {
  const CONDICIONAIS = new Set(['if', 'for', 'each', 'when']);
  const RAMOS = new Set(['else', 'elseif', 'is']);
  const faixas = [];
  const pilha = [];
  const tags = /\{#([a-zA-Z]+)[^}]*\}|\{\/([a-zA-Z]*)\}/g;
  for (const m of html.matchAll(tags)) {
    const abre = m[1];
    if (abre) {
      if (RAMOS.has(abre)) { continue; }
      pilha.push({ nome: abre, inicio: m.index });
      continue;
    }
    const topo = pilha.pop();
    if (!topo) { continue; }
    if (CONDICIONAIS.has(topo.nome)) { faixas.push([topo.inicio, m.index + m[0].length]); }
  }
  return (indice) => faixas.some(([a, b]) => indice >= a && indice <= b);
}

/** Escapa `:` e `.` para o id virar seletor CSS valido em querySelector. */
const CSS_ESCAPE = (id) => id.replace(/([:.])/g, '\\$1');

/** Mantem o primeiro de cada chave, preservando a ordem do documento. */
function dedup(lista, chave) {
  const vistos = new Set();
  return lista.filter((item) => {
    const k = chave(item);
    if (vistos.has(k)) { return false; }
    vistos.add(k);
    return true;
  });
}

// ---------------------------------------------------------------------------
// 3. MONTAGEM
// ---------------------------------------------------------------------------

const fontesJava = arquivos(JAVA, 'Resource.java');
if (fontesJava.length === 0) {
  console.error('  Nenhuma resource JAX-RS encontrada em src/main/java — inventario nao verificado.');
  process.exit(2);
}

const rotas = [];
const avisos = [];
for (const arquivo of fontesJava) {
  rotas.push(...rotasDaResource(readFileSync(arquivo, 'utf8'), relative(RAIZ, arquivo)));
}

// Pagina = rota navegavel. Query-strings declaradas nos templates viram variantes
// da mesma pagina (ex.: ?aba=reversa), porque sao telas diferentes de verdade.
const paginas = rotas.filter((r) => r.tipo === 'pagina' && !r.parametrizada);
const porTemplate = new Map();
for (const p of paginas) {
  if (p.template) { porTemplate.set(p.template, p.caminho); }
}

// Aprofundamentos de protocolo genericos vivem sob UMA rota parametrizada
// (/protocolos/{slug}), invisivel ao scanner de @Path literais. Sao paginas
// publicas de verdade — e a fonte unica dos slugs e o registro do dominio, o
// mesmo que alimenta o sub-menu e o sitemap. Aqui os slugs sao EXTRAIDOS desse
// codigo (nao escritos de memoria) para que a varredura cubra cada aprofundamento.
const REGISTRO_APROF = join(RAIZ, 'src/main/java/org/framework/net/protocolos/domain/AprofundamentoProtocolo.java');
const paginasAprofundamento = [];
if (existsSync(REGISTRO_APROF)) {
  const fonteRegistro = readFileSync(REGISTRO_APROF, 'utf8');
  const slugs = new Set();
  // generico("slug", ...) e new AprofundamentoProtocolo("slug", ...)
  for (const m of fonteRegistro.matchAll(/(?:generico|new\s+AprofundamentoProtocolo)\s*\(\s*"([a-z0-9-]+)"/g)) {
    slugs.add(m[1]);
  }
  for (const slug of slugs) { paginasAprofundamento.push('/protocolos/' + slug); }
} else {
  avisos.push('inventario: registro de aprofundamentos nao encontrado em ' + REGISTRO_APROF);
}

// Aprofundamentos de PORTAS (Anatomia + familias) — mesma logica, registro proprio.
const REGISTRO_PORTAS = join(RAIZ, 'src/main/java/org/framework/net/portas/domain/PortaAprofundamento.java');
if (existsSync(REGISTRO_PORTAS)) {
  const fonte = readFileSync(REGISTRO_PORTAS, 'utf8');
  for (const m of fonte.matchAll(/new\s+PortaAprofundamento\s*\(\s*"([a-z0-9-]+)"/g)) {
    paginasAprofundamento.push('/portas/' + m[1]);
  }
} else {
  avisos.push('inventario: registro de aprofundamentos de portas nao encontrado em ' + REGISTRO_PORTAS);
}

// Aprofundamentos de CERTIFICADOS (X.509/PKI) — mesma logica, registro proprio.
const REGISTRO_CERT = join(RAIZ, 'src/main/java/org/framework/net/certificados/domain/CertificadoAprofundamento.java');
if (existsSync(REGISTRO_CERT)) {
  const fonte = readFileSync(REGISTRO_CERT, 'utf8');
  for (const m of fonte.matchAll(/new\s+CertificadoAprofundamento\s*\(\s*"([a-z0-9-]+)"/g)) {
    paginasAprofundamento.push('/certificados/' + m[1]);
  }
} else {
  avisos.push('inventario: registro de aprofundamentos de certificados nao encontrado em ' + REGISTRO_CERT);
}

// Modulos flat (Camadas, Ferramentas, Criptografia, Wi-Fi): mesma logica.
const REGISTROS_FLAT = [
  ['camadas', 'CamadaAprofundamento'],
  ['ferramentas', 'FerramentasAprofundamento'],
  ['criptografia', 'CriptografiaAprofundamento'],
  ['wifi', 'WifiAprofundamento'],
];
for (const [mod, classe] of REGISTROS_FLAT) {
  const reg = join(RAIZ, `src/main/java/org/framework/net/${mod}/domain/${classe}.java`);
  if (existsSync(reg)) {
    const fonte = readFileSync(reg, 'utf8');
    for (const m of fonte.matchAll(new RegExp(`new\\s+${classe}\\s*\\(\\s*"([a-z0-9-]+)"`, 'g'))) {
      paginasAprofundamento.push(`/${mod}/` + m[1]);
    }
  } else {
    avisos.push('inventario: registro de aprofundamentos nao encontrado em ' + reg);
  }
}

const superficie = {};
for (const [template, rota] of porTemplate) {
  superficie[rota] = { template, ...superficieDoTemplate(templateExpandido(template, new Set(), avisos)) };
}

// Variantes com query-string: colhidas dos links internos que os proprios
// templates publicam. Sao telas que existem e que ninguem visita por engano.
const variantes = unicos(
  Object.values(superficie)
    .flatMap((s) => s.links)
    .filter((l) => l.includes('?'))
    // Link com `{` no meio e expressao Qute ainda por renderizar (ex.:
    // `?replay={item.id}`), nao URL. Navegar nele testaria uma rota que nao
    // existe e produziria um 404 inventado pelo proprio instrumento.
    .filter((l) => !/[{}]/.test(l))
    .map((l) => l.replace(/&amp;/g, '&')),
).filter((l) => paginas.some((p) => p.caminho === l.split('?')[0]));

const jsDoSite = arquivos(ESTATICOS, '.js')
  .map((a) => barra(relative(ESTATICOS, a)))
  .filter((a) => !a.endsWith('.min.js'))
  .sort();

const inventario = {
  base: 'http://127.0.0.1:8081',
  paginas: unicos([...paginas.map((p) => p.caminho), ...paginasAprofundamento]).sort(),
  variantes: variantes.sort(),
  apis: rotas.filter((r) => r.tipo === 'api').map((r) => ({ metodo: r.metodo, caminho: r.caminho, origem: r.origem })),
  downloads: unicos(rotas.filter((r) => r.tipo === 'download').map((r) => r.caminho)).sort(),
  mutantes: unicos(rotas.filter((r) => r.tipo === 'mutante').map((r) => r.caminho)).sort(),
  parametrizadas: unicos(rotas.filter((r) => r.parametrizada).map((r) => r.caminho)).sort(),
  superficie,
  js: jsDoSite,
  avisos: unicos(avisos),
};

const totalAbas = Object.values(superficie).reduce((n, s) => n + s.abas.length, 0);
const totalCampos = Object.values(superficie).reduce((n, s) => n + s.campos.length, 0);
const totalGatilhos = Object.values(superficie).reduce((n, s) => n + s.gatilhos.length, 0);
const totalLinks = unicos(Object.values(superficie).flatMap((s) => s.links)).length;

// Portao: categoria principal vazia significa extrator quebrado, nao projeto vazio.
const vazias = Object.entries({
  paginas: inventario.paginas.length,
  apis: inventario.apis.length,
  abas: totalAbas,
  campos: totalCampos,
  gatilhos: totalGatilhos,
  links: totalLinks,
}).filter(([, n]) => n === 0);

console.log('\n  INVENTARIO DA SUPERFICIE — Framework de Redes\n  ' + '-'.repeat(52));
console.log(`  paginas navegaveis .... ${inventario.paginas.length}`);
console.log(`  variantes (?query) .... ${inventario.variantes.length}`);
console.log(`  APIs .................. ${inventario.apis.length}`);
console.log(`  downloads (fora) ...... ${inventario.downloads.length}`);
console.log(`  mutantes (fora) ....... ${inventario.mutantes.length}`);
console.log(`  parametrizadas (fora) . ${inventario.parametrizadas.length}`);
console.log(`  abas .................. ${totalAbas}`);
console.log(`  campos ................ ${totalCampos}`);
console.log(`  gatilhos .............. ${totalGatilhos}`);
console.log(`  modais ................ ${Object.values(superficie).reduce((n, s) => n + s.modais.length, 0)}`);
console.log(`  links internos ........ ${totalLinks}`);
console.log(`  arquivos JS ........... ${inventario.js.length}`);
if (inventario.avisos.length) {
  console.log('\n  AVISOS:');
  inventario.avisos.forEach((a) => console.log(`    ${a}`));
}

if (vazias.length) {
  console.error(`\n  NAO VERIFICADO: categoria(s) vazia(s) — ${vazias.map(([k]) => k).join(', ')}.`);
  console.error('  Extrator provavelmente quebrado. Inventario NAO foi gravado.');
  process.exit(2);
}

writeFileSync(SAIDA, JSON.stringify(inventario, null, 2), 'utf8');
console.log(`\n  gravado em ${barra(relative(RAIZ, SAIDA))}\n`);
