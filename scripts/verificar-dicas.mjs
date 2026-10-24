/**
 * Catraca das dicas: todo título, rótulo, campo, botão e cabeçalho de coluna tem tooltip acessível.
 *
 * PROPOSITO DE NEGOCIO: em 22/10/2026 as ~1.400 dicas do site passaram para o tooltip canonico (aria-describedby
 * permanente, foco, toque, Esc), mas ainda ha elementos do escopo sem texto de dica nenhum — sobretudo titulos e
 * cabecalhos de coluna do conteudo didatico. Escrever esse texto e trabalho de conteudo. Ate la, esta catraca
 * impede que a divida CRESCA: elemento novo sem dica reprova; o que ja estava na linha de base passa.
 *
 * DOIS UNIVERSOS (24/10/2026). Ate 23/10 a catraca so via o estado inicial de cada pagina; o que mora em aba oculta
 * e em painel de resultado nunca era medido. Desde 24/10 ela tambem mede, por pagina:
 *  - cada aba (role=tab, data-bs-toggle=tab/pill), clicada em ordem de documento, inclusive aba dentro de aba;
 *  - o painel de resultado de UM calculo realista nas calculadoras (lista ACOES abaixo);
 *  - desde 25/10, a etapa de laboratorio que so aparece avancando a experiencia (tipo 'etapa' em ACOES: as opcoes de
 *    "Preveja antes de avancar" criadas pelo JS), com a identidade prefixada por "etapa:<nome>".
 * Isso nao e divida "nova": e universo novo. Fica numa secao separada da linha de base (SECAO_AMPLIADA), com a
 * identidade prefixada pelo estado ("aba:vlan|th|...", "resultado:dividir|h3|..."), e e contado a parte.
 * Estado que depende de servico externo (DNS real, geolocalizacao) ou de dado ao vivo NAO e medido e aparece
 * listado como "nao medido" com o motivo (NAO_MEDIDOS) — nunca conta como resolvido.
 *
 * INVARIANTES:
 *  - o instrumento e a varredura do componente canonico (scripts/dica-varredura.js, copia sem edicao);
 *  - a linha de base guarda IDENTIDADE (pagina + estado + etiqueta + problema + texto do elemento), nunca so contagem;
 *  - um elemento pertence a UM estado so: o primeiro em que ficou visivel (estado inicial, depois abas, depois
 *    resultados, nessa ordem); elemento ja visto nao e recontado no estado seguinte;
 *  - a catraca so desce: --gravar apenas REMOVE da linha de base o que foi resolvido (intersecao), nunca acrescenta;
 *    o universo ampliado so entra uma vez, por --registrar-universo-ampliado, e so se a secao ainda nao existir;
 *  - pagina ou estado que nao pode ser medido mantem as identidades dele na linha de base (nao vira "resolvido");
 *  - o Google Tradutor fica bloqueado (carga em volume manda a rede para o captcha) e qualquer pedido do navegador
 *    para fora do servidor medido e abortado e contado (a medicao nunca toca servico externo).
 *
 * COMPORTAMENTO EM CASO DE FALHA: sai 2 (NAO VERIFICOU) quando o servidor nao responde, o inventario nao existe, uma
 * pagina ou estado nao pode ser medido, ou a calibracao falha (o instrumento nao acusou o botao sem dica injetado no
 * estado inicial ou dentro de aba oculta, ou acusou o botao com dica canonica); sai 1 quando ha elemento NOVO sem
 * dica em qualquer dos dois universos; sai 0 quando nada piorou.
 *
 * USO:  node scripts/verificar-dicas.mjs [http://localhost:8081] [--gravar] [--so=/rota1,/rota2] [--json=saida.json]
 *       node scripts/verificar-dicas.mjs [url] --registrar-universo-ampliado   (uma vez: grava a secao ampliada)
 */
import { chromium } from 'playwright';
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const AQUI = dirname(fileURLToPath(import.meta.url));
const ARGS = process.argv.slice(2);
const BASE = ARGS.find((a) => a.startsWith('http')) || 'http://localhost:8081';
const ORIGEM = new URL(BASE).origin;
const GRAVAR = ARGS.includes('--gravar');
const REGISTRAR = ARGS.includes('--registrar-universo-ampliado');
const SO = (ARGS.find((a) => a.startsWith('--so=')) || '').slice(5).split(',').filter(Boolean);
const SAIDA_JSON = (ARGS.find((a) => a.startsWith('--json=')) || '').slice(7);
const LINHA_DE_BASE = join(AQUI, 'dicas-linha-base.json');
const SECAO_AMPLIADA = 'universoAmpliado24out';
const VARREDURA = readFileSync(join(AQUI, 'dica-varredura.js'), 'utf8').replace(/if \(typeof module[\s\S]*$/, '');
const GOOGLE = /^https?:\/\/([a-z0-9-]+\.)*(google\.com|googleapis\.com|gstatic\.com)\//i;
const ABAS = '[role=tab], [data-bs-toggle=tab], [data-bs-toggle=pill]';

/**
 * Um calculo realista por painel de resultado. Os campos ja vem com exemplo na maioria das telas; onde o campo vem
 * vazio (so placeholder), o valor preenchido e um exemplo didatico privado ou de documentacao (RFC 5737/3849).
 * botao: nome acessivel do botao (o rotulo visivel; o texto da dica nao entra no nome).
 * tipo: prefixo do estado na identidade ('resultado' quando omitido; 'etapa' para passo de laboratorio).
 * cliques: quantas vezes clicar no botao antes de medir (1 quando omitido); acumula com a acao anterior da mesma pagina.
 */
const ACOES = {
  '/calculadora': [
    { estado: 'dividir', aba: 'dividir', botao: /^Calcular/ },
    { estado: 'vlan', aba: 'vlan', botao: /^Gerar plano/ },
    { estado: 'agregar-sumarizar', aba: 'agregar', botao: /^Sumarizar/ },
    { estado: 'agregar-comparar', aba: 'agregar', botao: /^Comparar/ },
    { estado: 'faixa', aba: 'faixa', botao: /^Converter/ },
  ],
  '/analise': [
    { estado: 'cidr', aba: 'cidr', preencher: [['#ip', '172.16.0.10'], ['#cidr', '24']], botao: /^Executar/ },
    { estado: 'mask', aba: 'mask', preencher: [['#mask_decimal', '255.255.255.0']], botao: /^Executar/ },
    { estado: 'wildcard', aba: 'wildcard', preencher: [['#ip', '172.16.8.8'], ['#wildcard_mask', '0.0.15.255']], botao: /^Executar/ },
    { estado: 'autoip', aba: 'autoip', preencher: [['#ip', '169.254.10.20']], botao: /^Executar/ },
    { estado: 'ipv6', aba: 'ipv6', preencher: [['#ipv6', '2001:db8::1']], botao: /^Executar/ },
    { estado: 'comparador', aba: 'comparador', preencher: [['#ip', '172.19.0.10']], botao: /^Executar/ },
    { estado: 'calculadora', aba: 'calculadora', botao: /^Calcular/ },
  ],
  '/ipv6': [
    { estado: 'dividir', aba: 'dividir', botao: /^Dividir/ },
    { estado: 'eui64', aba: 'eui64', botao: /^Derivar/ },
    { estado: 'ula', aba: 'ula', botao: /^Gerar ULA/ },
    { estado: 'sumarizar', aba: 'sumarizar', botao: /^Sumarizar/ },
    { estado: 'faixa', aba: 'faixa', botao: /^Converter/ },
    { estado: 'vlan', aba: 'vlan', botao: /^Planejar/ },
  ],
  '/ipv6/analise': [
    { estado: 'analise', aba: 'analise', botao: /^Analisar/ },
    { estado: 'comparador', aba: 'comparador', botao: /^Comparar/ },
    { estado: 'nibbles', aba: 'nibbles', botao: /^Analisar/ },
  ],
  '/ipv6/resolucao': [
    { estado: 'projetar', aba: 'projetar', botao: /^Projetar/ },
    { estado: 'engenharia', aba: 'engenharia', botao: /^Analisar/ },
  ],
  '/resolucao-problemas': [
    {
      estado: 'cenario',
      preencher: [['#base-network-ip', '172.19.0.0'], ['input[name=loc_name]', 'Matriz'], ['input[name=loc_hosts]', '400']],
      botao: /^Calcular/,
    },
  ],
  '/trafego': [
    { estado: 'decodificador', aba: 'decodificador', botao: /^Decodificar/ },
    { estado: 'encapsulamento', aba: 'encapsulamento', botao: /^Encapsular/ },
    { estado: 'handshake', aba: 'handshake', botao: /^Passo a passo/ },
    { estado: 'anomalias', aba: 'anomalias', botao: /^Passo a passo/ },
    { estado: 'construtor', aba: 'construtor', botao: /^Gerar pacote/ },
    { estado: 'lab', aba: 'lab', botao: /^Consultas DNS/ },
  ],
  '/seguranca': [
    { estado: 'acl', aba: 'acl', botao: /^Testar regra/ },
    { estado: 'tls', aba: 'tls', botao: /^Inspecionar/ },
    { estado: 'estado', aba: 'estado', botao: /^Navegação de saída/ },
    { estado: 'handshake', aba: 'handshake', botao: /^Handshake completo/ },
    { estado: 'fluxo', aba: 'fluxo', botao: /^Fluxo permitido/ },
    { estado: 'topologia', aba: 'topologia', botao: /^Testar fluxo/ },
  ],
  // Laboratorio: as opcoes de "Preveja antes de avancar" so existem nas etapas com pergunta (UDP = 2a etapa, switch =
  // 6a). Os cliques sao cumulativos na mesma pagina: 1 clique em "Proxima" leva a etapa 2; mais 4, a etapa 6.
  '/laboratorios/camadas': [
    { tipo: 'etapa', estado: 'udp-pergunta', botao: /^Próxima/, cliques: 1 },
    { tipo: 'etapa', estado: 'switch-pergunta', botao: /^Próxima/, cliques: 4 },
  ],
  '/diagnostico': [
    { estado: 'ping', aba: 'ping', botao: /^Disparar Ping/ },
    { estado: 'dns', aba: 'dns', botao: /^Resolver Nome/ },
    { estado: 'traceroute', aba: 'traceroute', botao: /^Traçar Rota/ },
    { estado: 'sweep', aba: 'sweep', botao: /^Varrer Faixa/ },
    { estado: 'scan', aba: 'scan', botao: /^Varrer Portas/ },
    { estado: 'spoofing', aba: 'spoofing', botao: /^Simular Spoofing/ },
  ],
};

/** Estados que a catraca NAO mede, com o motivo. Aparecem na saida e na linha de base; nunca contam como resolvidos. */
const NAO_MEDIDOS = {
  '/analise': { 'resultado:dominio': 'consulta DNS real a resolvedor externo' },
  '/ipv6/analise': { 'resultado:dominio': 'consulta DNS real (AAAA) a resolvedor externo' },
  '/trafego': { 'resultado:aovivo': 'leitura ao vivo com temporizador; o conteudo muda a cada leitura' },
  '/seguranca': { 'resultado:canvas': 'a tela de desenho comeca vazia; testar fluxo exige montar topologia a mao' },
  '/localizacao': { 'resultado:geoip/privacidade/cep': 'geolocalizacao, CEP e mapa consultam servicos externos' },
  '/informacoes': { 'resultado:consulta': 'geolocalizacao do IP consulta servico externo' },
};

if (!existsSync(join(AQUI, 'inventario.json'))) {
  console.log('NAO VERIFICOU: scripts/inventario.json ausente (rode node scripts/inventario.mjs)');
  process.exit(2);
}
const inventario = JSON.parse(readFileSync(join(AQUI, 'inventario.json'), 'utf8')).paginas;
const paginas = SO.length ? inventario.filter((r) => SO.includes(r)) : inventario;

let externosAbortados = 0;
const navegador = await chromium.launch();
async function abrir(rota) {
  const contexto = await navegador.newContext({ viewport: { width: 1440, height: 900 } });
  await contexto.route(GOOGLE, (r) => r.abort());
  await contexto.route((url) => url.origin !== ORIGEM && !GOOGLE.test(url.href) && /^https?:/.test(url.href), (r) => {
    externosAbortados++;
    return r.abort();
  });
  const pagina = await contexto.newPage();
  let emVoo = 0;
  pagina.on('request', () => { emVoo++; });
  const fim = () => { emVoo = Math.max(0, emVoo - 1); };
  pagina.on('requestfinished', fim);
  pagina.on('requestfailed', fim);
  pagina.aguardarRede = async () => {
    const limite = Date.now() + 8000;
    let quietoDesde = 0;
    while (Date.now() < limite) {
      await pagina.waitForTimeout(100);
      if (emVoo === 0) {
        if (!quietoDesde) { quietoDesde = Date.now(); }
        if (Date.now() - quietoDesde >= 300) { break; }
      } else { quietoDesde = 0; }
    }
    await pagina.waitForTimeout(250);
  };
  const resp = await pagina.goto(BASE + rota, { waitUntil: 'load', timeout: 45000 });
  await pagina.waitForTimeout(400);
  return { contexto, pagina, status: resp ? resp.status() : 0 };
}

/**
 * Varre o que esta visivel e ainda nao foi atribuido a estado nenhum. O conjunto window.__dicaVistos guarda os
 * elementos ja medidos (por referencia, nao por texto): a raiz entregue a varredura canonica so devolve os de fora
 * dele, e no fim todo elemento do escopo visivel agora entra no conjunto.
 */
const varrer = (pagina) => pagina.evaluate((src) => {
  eval(src); // eslint-disable-line no-eval
  const vistos = window.__dicaVistos || (window.__dicaVistos = new Set());
  const visivel = (el) => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
  const raiz = { querySelectorAll: (s) => [...document.querySelectorAll(s)].filter((el) => !vistos.has(el)) };
  const achados = varreduraDica(raiz);
  const escopo = 'input:not([type=hidden]), select, textarea, button, [role=button], [role=menuitem], [role=tab], '
    + '[role=switch], label, th, h1, h2, h3, h4, h5, h6, [role=columnheader]';
  document.querySelectorAll(escopo).forEach((el) => { if (visivel(el)) { vistos.add(el); } });
  // Numero no texto do elemento vira "#": a Academia sorteia valores a cada carga, e a identidade nao pode mudar
  // com o sorteio (senao o mesmo elemento conta como "novo" e "resolvido" ao mesmo tempo).
  return achados.map((f) => `${f.tag || '-'}|${f.problema.replace(/\d+/g, '#')}|${(f.elemento || '').replace(/\s+/g, ' ').replace(/\d+/g, '#').trim()}`);
}, VARREDURA);

/** Nome estavel da aba: a chave de dados que o site usa para ela; na falta, o alvo, o id ou o texto. */
const nomeDaAba = (h) => h.evaluate((t) => t.getAttribute('data-tab') || t.getAttribute('data-calc-tab')
  || t.getAttribute('data-anom') || (t.getAttribute('aria-controls') || '').replace(/^painel-/, '')
  || (t.getAttribute('data-bs-target') || t.getAttribute('href') || '').replace(/^#/, '') || t.id
  || (t.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 30));

/**
 * Abas visiveis agora que ainda nao tinham sido vistas, em ordem de documento; ficam marcadas como vistas. No primeiro
 * nivel sao todas as abas visiveis; depois de um clique, so as que o clique REVELOU (aba dentro de aba) — as irmas ja
 * estavam marcadas, entao nao viram "filhas" da aba clicada.
 */
async function abasNovas(pagina) {
  const lista = [];
  for (const h of await pagina.$$(ABAS)) {
    if (!(await h.isVisible())) { continue; }
    const vista = await h.evaluate((t) => t.hasAttribute('data-dica-aba-vista'));
    if (!vista) { lista.push(h); }
  }
  for (const h of lista) { await h.evaluate((t) => t.setAttribute('data-dica-aba-vista', '')); }
  return lista;
}

/** Clica cada aba (e as abas que ela revela) e mede o que apareceu. Devolve { 'aba:x': [...] }. */
async function medirAbas(pagina, prefixo = '', saida = {}) {
  for (const h of await abasNovas(pagina)) {
    if (!(await h.isVisible())) { continue; }
    const nome = prefixo + (await nomeDaAba(h));
    await h.click();
    await pagina.aguardarRede();
    saida['aba:' + nome] = await varrer(pagina);
    await medirAbas(pagina, nome + '>', saida);
  }
  return saida;
}

async function clicarAba(pagina, chave) {
  const h = pagina.locator(`[role=tab][data-tab="${chave}"], [role=tab][data-calc-tab="${chave}"]`).filter({ visible: true }).first();
  await h.click();
  await pagina.aguardarRede();
}

/** Executa um calculo realista e mede o painel de resultado. */
async function medirAcao(pagina, a) {
  if (a.aba) { await clicarAba(pagina, a.aba); }
  for (const [sel, valor] of a.preencher || []) {
    // "#x" aceita tambem name="x": as abas da Analise repetem o mesmo campo (name="ip") em cada formulario.
    const alvo = /^#[\w-]+$/.test(sel) ? `${sel}, [name="${sel.slice(1)}"]` : sel;
    await pagina.locator(alvo).filter({ visible: true }).first().fill(valor, { timeout: 8000 });
  }
  for (let i = 0; i < (a.cliques || 1); i++) {
    await pagina.getByRole('button', { name: a.botao }).filter({ visible: true }).first().click({ timeout: 8000 });
    await pagina.aguardarRede();
  }
  return varrer(pagina);
}

/**
 * Identidade de cada elemento sem dica. Nas licoes da Academia o exercicio e sorteado a cada carga (outro rotulo,
 * outro valor): ali a identidade e a CLASSE — etiqueta + problema + ordinal — e a catraca compara quantos de cada
 * classe ha (A4: ocorrencia OU classe rastreavel). No resto do site, o texto do elemento identifica.
 */
function identidades(rota, lista, estado = '') {
  const pre = estado ? estado + '|' : '';
  if (!rota.startsWith('/academia/')) {
    return [...new Set(lista)].sort().map((x) => pre + x);
  }
  const porClasse = {};
  return lista.map((x) => {
    const classe = x.split('|').slice(0, 2).join('|');
    porClasse[classe] = (porClasse[classe] || 0) + 1;
    return `${pre}${classe}|#${porClasse[classe]}`;
  }).sort();
}

/** Mede a pagina inteira: estado inicial (universo original) + abas e resultados (universo ampliado). */
async function medirRota(rota, preparar) {
  const { contexto, pagina, status } = await abrir(rota);
  try {
    if (status !== 200) { return { status }; }
    if (preparar) { await preparar(pagina); }
    const inicial = identidades(rota, await varrer(pagina));
    const ampliado = [];
    const falhas = [];
    for (const [estado, lista] of Object.entries(await medirAbas(pagina))) {
      ampliado.push(...identidades(rota, lista, estado));
    }
    for (const a of ACOES[rota] || []) {
      const estado = (a.tipo || 'resultado') + ':' + a.estado;
      try {
        ampliado.push(...identidades(rota, await medirAcao(pagina, a), estado));
      } catch (e) {
        falhas.push(`${estado} (${String(e).split('\n')[0].slice(0, 90)})`);
      }
    }
    return { status, inicial, ampliado: [...new Set(ampliado)].sort(), falhas };
  } finally {
    await contexto.close();
  }
}

// Calibracao (A1) do estado inicial: o botao sem dica injetado TEM de aparecer; o botao com a marcacao canonica nao.
{
  const { contexto, pagina, status } = await abrir('/');
  if (status !== 200) { console.log('NAO VERIFICOU: / respondeu ' + status); process.exit(2); }
  await pagina.evaluate(() => {
    const ruim = document.createElement('button'); ruim.textContent = 'CalibracaoSemDica'; document.body.appendChild(ruim);
    const bom = document.createElement('span'); bom.className = 'dica';
    bom.innerHTML = '<button type="button" aria-describedby="d-calib">CalibracaoComDica</button>'
      + '<span id="d-calib" role="tooltip" class="dica__texto">Explica o que o botao de calibracao faz.</span>';
    document.body.appendChild(bom);
  });
  const achados = await varrer(pagina);
  await contexto.close();
  const pegouRuim = achados.some((a) => a.includes('CalibracaoSemDica'));
  const poupouBom = !achados.some((a) => a.includes('CalibracaoComDica'));
  if (!pegouRuim || !poupouBom) {
    console.log(`NAO VERIFICOU: calibracao falhou (acusou o sem dica=${pegouRuim}, poupou o com dica=${poupouBom})`);
    process.exit(2);
  }
  console.log('calibracao: botao sem dica acusado, botao com dica canonica aceito');
}

// Calibracao (A1) do universo ampliado: dentro da aba OCULTA "vlan" da calculadora, o botao sem dica injetado tem de
// aparecer SO no estado aba:vlan (e nao no inicial); o vizinho com dica canonica nao aparece em estado nenhum.
{
  const r = await medirRota('/calculadora', (pagina) => pagina.evaluate(() => {
    const painel = document.querySelector('[data-calc-panel="vlan"]');
    const ruim = document.createElement('button'); ruim.textContent = 'CalibracaoAbaSemDica'; painel.appendChild(ruim);
    const bom = document.createElement('span'); bom.className = 'dica';
    bom.innerHTML = '<button type="button" aria-describedby="d-calib-aba">CalibracaoAbaComDica</button>'
      + '<span id="d-calib-aba" role="tooltip" class="dica__texto">Explica o que o botao de calibracao faz.</span>';
    painel.appendChild(bom);
  }));
  const todos = [...(r.inicial || []), ...(r.ampliado || [])];
  const naAba = (r.ampliado || []).includes('aba:vlan|button|sem tooltip|CalibracaoAbaSemDica');
  const foraDoInicial = !(r.inicial || []).some((a) => a.includes('CalibracaoAba'));
  const poupouBom = !todos.some((a) => a.includes('CalibracaoAbaComDica'));
  if (!naAba || !foraDoInicial || !poupouBom) {
    console.log(`NAO VERIFICOU: calibracao da aba oculta falhou (acusou na aba=${naAba}, fora do inicial=${foraDoInicial}, poupou o com dica=${poupouBom})`);
    process.exit(2);
  }
  console.log('calibracao: botao sem dica dentro de aba oculta acusado so em aba:vlan, botao com dica canonica aceito');
}

const atual = {};
const ampliadoAtual = {};
const falhasDeEstado = {};
let naoAbriram = 0;
for (const rota of paginas) {
  try {
    const r = await medirRota(rota);
    if (r.status !== 200) { naoAbriram++; console.log(`  ${rota} NAO VERIFICOU (status ${r.status})`); continue; }
    atual[rota] = r.inicial;
    ampliadoAtual[rota] = r.ampliado;
    if (r.falhas.length) {
      falhasDeEstado[rota] = r.falhas;
      console.log(`  ${rota} estado(s) NAO VERIFICADO(S): ${r.falhas.join(' ; ')}`);
    }
  } catch (e) {
    naoAbriram++;
    console.log(`  ${rota} NAO VERIFICOU (${String(e).slice(0, 80)})`);
  }
}
await navegador.close();

const estadoDe = (id) => id.split('|')[0];
const contar = (o) => Object.values(o).reduce((n, l) => n + l.length, 0);
const totalOriginal = contar(atual);
const totalAmpliado = contar(ampliadoAtual);
const estadosFalhos = Object.values(falhasDeEstado).reduce((n, l) => n + l.length, 0);
const naoMedidos = Object.entries(NAO_MEDIDOS).filter(([r]) => !SO.length || SO.includes(r));
if (SAIDA_JSON) {
  writeFileSync(SAIDA_JSON, JSON.stringify({ original: atual, ampliado: ampliadoAtual }, null, 1) + '\n');
}

const arquivo = existsSync(LINHA_DE_BASE) ? JSON.parse(readFileSync(LINHA_DE_BASE, 'utf8')) : {};
const baseOriginal = Object.fromEntries(Object.entries(arquivo).filter(([k]) => k.startsWith('/')));
const secao = arquivo[SECAO_AMPLIADA];
const baseAmpliada = secao ? secao.rotas : null;

function comparar(atualU, baseU, rotulo) {
  let novos = 0;
  let resolvidos = 0;
  for (const [rota, lista] of Object.entries(atualU)) {
    const antes = new Set(baseU[rota] || []);
    const agora = new Set(lista);
    const falhos = (falhasDeEstado[rota] || []).map((f) => f.split(' ')[0]);
    const n = lista.filter((x) => !antes.has(x));
    const r = [...antes].filter((x) => !agora.has(x) && !falhos.includes(estadoDe(x)));
    novos += n.length;
    resolvidos += r.length;
    if (n.length) { console.log(`  ${rotulo} ${rota}: ${n.length} NOVO(S) sem dica — ${n.slice(0, 4).join(' ; ')}`); }
  }
  return { novos, resolvidos };
}

/** Linha de base so desce: fica a intersecao; rota ou estado nao medido mantem o que tinha. */
function encolher(baseU, atualU) {
  const saida = {};
  for (const [rota, antes] of Object.entries(baseU)) {
    if (!(rota in atualU)) { saida[rota] = antes; continue; }
    const agora = new Set(atualU[rota]);
    const falhos = (falhasDeEstado[rota] || []).map((f) => f.split(' ')[0]);
    saida[rota] = antes.filter((x) => agora.has(x) || falhos.includes(estadoDe(x)));
  }
  return saida;
}

function gravarArquivo(original, ampliada) {
  const saida = { ...original };
  if (ampliada) {
    saida[SECAO_AMPLIADA] = {
      descricao: 'Universo ampliado em 24/10/2026: elementos que so ficam visiveis ao abrir uma aba (aba:<nome>) ou '
        + 'depois de um calculo realista (resultado:<nome>). Separado da linha de base original (chaves de rota acima), '
        + 'que mede so o estado inicial. Um elemento conta no primeiro estado em que aparece. So desce.',
      naoMedidos: Object.fromEntries(Object.entries(NAO_MEDIDOS)),
      rotas: ampliada,
    };
  }
  writeFileSync(LINHA_DE_BASE, JSON.stringify(saida, null, 1) + '\n');
}

if (REGISTRAR) {
  if (secao) { console.log('RECUSADO: a secao ' + SECAO_AMPLIADA + ' ja existe; o universo so se registra uma vez.'); process.exit(1); }
  const { novos } = comparar(atual, baseOriginal, 'original');
  if (novos || naoAbriram || estadosFalhos || SO.length) {
    console.log(`RECUSADO: registrar o universo exige medicao completa e limpa (novos ${novos}, paginas nao verificadas ${naoAbriram}, estados nao verificados ${estadosFalhos}, --so ${SO.length ? 'usado' : 'nao'}).`);
    process.exit(2);
  }
  gravarArquivo(baseOriginal, ampliadoAtual);
  console.log(`universo ampliado registrado: ${totalAmpliado} elemento(s) em ${Object.keys(ampliadoAtual).length} pagina(s); base original intocada (${totalOriginal}).`);
  process.exit(0);
}

const o = comparar(atual, baseOriginal, 'original');
const a = baseAmpliada ? comparar(ampliadoAtual, baseAmpliada, 'ampliado') : { novos: 0, resolvidos: 0 };
const semSecao = !baseAmpliada;

if (GRAVAR) {
  if (!existsSync(LINHA_DE_BASE)) {
    gravarArquivo(atual, null);
    console.log(`linha de base criada: ${totalOriginal} elemento(s) sem dica (rode --registrar-universo-ampliado em seguida)`);
    process.exit(naoAbriram ? 2 : 0);
  }
  gravarArquivo(encolher(baseOriginal, atual), baseAmpliada ? encolher(baseAmpliada, ampliadoAtual) : null);
  console.log(`linha de base regravada (so desce): removidos ${o.resolvidos} da original e ${a.resolvidos} do ampliado; novos NAO entram (${o.novos + a.novos}).`);
}

for (const [rota, estados] of naoMedidos) {
  for (const [estado, motivo] of Object.entries(estados)) { console.log(`  nao medido: ${rota} ${estado} — ${motivo}`); }
}
if (externosAbortados) { console.log(`  pedidos do navegador para fora de ${ORIGEM} abortados: ${externosAbortados}`); }
if (semSecao) { console.log('  universo ampliado SEM linha de base: tudo nele conta como novo ate rodar --registrar-universo-ampliado'); }
const novosAmpliado = semSecao ? totalAmpliado : a.novos;
console.log(`\nsem dica: base original ${totalOriginal} (novos ${o.novos}, resolvidos ${o.resolvidos}) | universo ampliado ${totalAmpliado} `
  + `(novos ${novosAmpliado}, resolvidos ${a.resolvidos}) | novos ${o.novos + novosAmpliado}, paginas nao verificadas ${naoAbriram}, `
  + `estados nao verificados ${estadosFalhos}, estados nao medidos ${naoMedidos.reduce((n, [, e]) => n + Object.keys(e).length, 0)}`);
if (o.novos + novosAmpliado > 0) { process.exit(1); }
if (naoAbriram > 0 || estadosFalhos > 0) { console.log('NAO VERIFICOU: houve pagina ou estado que nao abriu — isso nao e aprovacao.'); process.exit(2); }
if (!GRAVAR && o.resolvidos + a.resolvidos > 0) { console.log('divida desceu: regrave a linha de base com --gravar para a catraca acompanhar (so remove).'); }
process.exit(0);
