/**
 * Catraca das dicas: dica só onde há o que explicar, e nenhuma dica onde ela atrapalha.
 *
 * PROPOSITO DE NEGOCIO: a dica (tooltip) existe para dizer o que o rotulo visivel nao diz e a pessoa precisa para
 * agir: o que um botao so com icone faz, o formato de um campo, o motivo de um item desabilitado, o significado de
 * uma sigla ou unidade. Ate 24/10/2026 o site exigia dica em todo titulo, rotulo, coluna e botao, e o resultado foi
 * um "?" em cada titulo de card — uma parada a mais no Tab e uma frase a mais no leitor de tela em cada um, sem
 * acrescentar nada. Esta catraca mede o site inteiro com a varredura do componente compartilhado e reprova as duas
 * direcoes do erro:
 *  - FALHA: dica que falta onde ha o que explicar (controle so com icone sem nome ou sem dica, desabilitado sem
 *    motivo, campo com formato ou limite declarado sem dica) e dica de ma qualidade (vazia, acima de 160
 *    caracteres, repetindo o rotulo, gatilho "?" sem nome);
 *  - AVISO: dica onde nao se usa (em titulo, em card clicavel, em texto corrido). Aqui o aviso tambem reprova: a
 *    revisao de 24/10 removeu todos, e um novo so entra com exceção registrada (data-dica-excecao="motivo").
 *
 * UNIVERSO: cada pagina do inventario (node scripts/inventario.mjs) e as variantes, no estado inicial, em cada aba
 * (role=tab, data-bs-toggle=tab/pill, clicada em ordem de documento, inclusive aba dentro de aba), no painel de
 * resultado de UM calculo realista nas calculadoras (ACOES) e nas etapas de laboratorio que so aparecem avancando a
 * experiencia. Estado que depende de servico externo ou de dado ao vivo NAO e medido e aparece listado com o motivo
 * (NAO_MEDIDOS). Com FRAMEWORK_CHAVE_LOCAL no ambiente (a chave de contingencia do .env local), mede tambem o painel
 * de telemetria logado.
 *
 * INVARIANTES:
 *  - o instrumento e a varredura do componente compartilhado (scripts/dica-varredura.js, copia sem edicao);
 *  - um elemento pertence a UM estado so: o primeiro em que ficou visivel (inicial, abas, resultados);
 *  - nenhuma linha de base: falha e aviso reprovam sempre (a linha de base da regra antiga, que exigia dica em todo
 *    titulo e coluna, saiu em 24/10 — o criterio mudou e ela media outra coisa);
 *  - o Google Tradutor fica bloqueado (carga em volume manda a rede para o captcha) e todo pedido do navegador para
 *    fora do servidor medido e abortado e contado.
 *
 * COMPORTAMENTO EM CASO DE FALHA: sai 2 (NAO VERIFICOU) quando o servidor nao responde, o inventario nao existe, uma
 * pagina ou estado nao pode ser medido, ou a calibracao falha — o instrumento tem de acusar, na fronteira, o botao
 * so com icone sem dica (e nao o mesmo botao com dica), a dica num titulo (e nao a dica de campo num painel "card"
 * que nao e clicavel), e o botao sem dica escondido numa aba (so no estado da aba); sai 1 quando ha falha ou aviso
 * em qualquer estado; sai 0 quando nao ha nenhum.
 *
 * USO:  node scripts/verificar-dicas.mjs [http://localhost:8081] [--so=/rota1,/rota2] [--json=saida.json]
 */
import { chromium } from 'playwright';
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const AQUI = dirname(fileURLToPath(import.meta.url));
const ARGS = process.argv.slice(2);
const BASE = ARGS.find((a) => a.startsWith('http')) || 'http://localhost:8081';
const ORIGEM = new URL(BASE).origin;
const SO = (ARGS.find((a) => a.startsWith('--so=')) || '').slice(5).split(',').filter(Boolean);
const SAIDA_JSON = (ARGS.find((a) => a.startsWith('--json=')) || '').slice(7);
const VARREDURA = readFileSync(join(AQUI, 'dica-varredura.js'), 'utf8').replace(/if \(typeof module[\s\S]*$/, '');
const GOOGLE = /^https?:\/\/([a-z0-9-]+\.)*(google\.com|googleapis\.com|gstatic\.com)\//i;
const ABAS = '[role=tab], [data-bs-toggle=tab], [data-bs-toggle=pill]';
const CHAVE = process.env.FRAMEWORK_CHAVE_LOCAL || '';

/**
 * Um calculo realista por painel de resultado. Os campos ja vem com exemplo na maioria das telas; onde o campo vem
 * vazio (so placeholder), o valor preenchido e um exemplo didatico privado ou de documentacao (RFC 5737/3849).
 * botao: nome acessivel do botao (o rotulo visivel; o texto da dica nao entra no nome).
 * tipo: prefixo do estado ('resultado' quando omitido; 'etapa' para passo de laboratorio).
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

/** Estados que a catraca NAO mede, com o motivo. Aparecem na saida; nunca contam como aprovados. */
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
const inventario = JSON.parse(readFileSync(join(AQUI, 'inventario.json'), 'utf8'));
const todas = [...inventario.paginas, ...(inventario.variantes || []), '/login/?modo=contingencia'];
const paginas = SO.length ? todas.filter((r) => SO.includes(r)) : todas;

let externosAbortados = 0;
const navegador = await chromium.launch();
let sessao;
if (CHAVE) {
  const c = await navegador.newContext();
  const p = await c.newPage();
  await p.goto(BASE + '/login/?modo=contingencia');
  await p.locator('input[name=adminKey]').fill(CHAVE);
  await Promise.all([p.waitForNavigation(), p.locator('input[name=adminKey]').press('Enter')]);
  if (!new URL(p.url()).pathname.startsWith('/telemetria')) { console.log('NAO VERIFICOU: o login de contingencia nao abriu a telemetria'); process.exit(2); }
  sessao = await c.storageState();
  await c.close();
}

async function abrir(rota, comSessao = false) {
  const contexto = await navegador.newContext({ viewport: { width: 1440, height: 900 }, storageState: comSessao ? sessao : undefined });
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
 * elementos ja medidos (por referencia, nao por texto): a raiz entregue a varredura so devolve os de fora dele.
 * Devolve uma linha por achado: "falha|problema|elemento" ou "aviso|problema|elemento".
 */
const varrer = (pagina) => pagina.evaluate((src) => {
  eval(src); // eslint-disable-line no-eval
  const vistos = window.__dicaVistos || (window.__dicaVistos = new Set());
  const visivel = (el) => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
  const raiz = { querySelectorAll: (s) => [...document.querySelectorAll(s)].filter((el) => !vistos.has(el) && visivel(el)) };
  const linha = (tipo) => (f) => `${tipo}|${f.problema.replace(/\d+/g, '#')}|${(f.elemento || '').replace(/\s+/g, ' ').replace(/\d+/g, '#').trim()}`;
  const achados = [...varreduraDica(raiz).map(linha('falha')), ...avisosDica(raiz).map(linha('aviso'))];
  document.querySelectorAll('button, a[href], [role=button], [role=menuitem], [role=tab], [role=switch], input, select, textarea, .dica')
    .forEach((el) => { if (visivel(el)) { vistos.add(el); } });
  return achados;
}, VARREDURA);

/** Nome estavel da aba: a chave de dados que o site usa para ela; na falta, o alvo, o id ou o texto. */
const nomeDaAba = (h) => h.evaluate((t) => t.getAttribute('data-tab') || t.getAttribute('data-calc-tab')
  || t.getAttribute('data-anom') || (t.getAttribute('aria-controls') || '').replace(/^painel-/, '')
  || (t.getAttribute('data-bs-target') || t.getAttribute('href') || '').replace(/^#/, '') || t.id
  || (t.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 30));

/** Abas visiveis ainda nao vistas, em ordem de documento; ficam marcadas como vistas. */
async function abasNovas(pagina) {
  const lista = [];
  for (const h of await pagina.$$(ABAS)) {
    if (!(await h.isVisible())) { continue; }
    if (!(await h.evaluate((t) => t.hasAttribute('data-dica-aba-vista')))) { lista.push(h); }
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

/** Mede a pagina inteira: estado inicial + abas + resultados. Devolve os achados prefixados pelo estado. */
async function medirRota(rota, preparar, comSessao = false) {
  const { contexto, pagina, status } = await abrir(rota, comSessao);
  try {
    if (status !== 200) { return { status }; }
    if (preparar) { await preparar(pagina); }
    const achados = (await varrer(pagina)).map((x) => 'inicial|' + x);
    const falhas = [];
    for (const [estado, lista] of Object.entries(await medirAbas(pagina))) {
      achados.push(...lista.map((x) => estado + '|' + x));
    }
    for (const a of ACOES[rota] || []) {
      const estado = (a.tipo || 'resultado') + ':' + a.estado;
      try {
        achados.push(...(await medirAcao(pagina, a)).map((x) => estado + '|' + x));
      } catch (e) {
        falhas.push(`${estado} (${String(e).split('\n')[0].slice(0, 90)})`);
      }
    }
    return { status, achados: [...new Set(achados)].sort(), falhas };
  } finally {
    await contexto.close();
  }
}

// Calibracao (A1, na fronteira) do estado inicial. Pares com o MESMO sinal: botao so com icone sem dica (acusa) x o
// mesmo botao com aria-label e dica canonica (aceita); dica num titulo (avisa) x dica de campo num painel com a classe
// "card" que nao e clicavel (nao avisa).
{
  const { contexto, pagina, status } = await abrir('/');
  if (status !== 200) { console.log('NAO VERIFICOU: / respondeu ' + status); process.exit(2); }
  await pagina.evaluate(() => {
    const svg = '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="8"/></svg>';
    const caixa = document.createElement('div');
    caixa.innerHTML = `<button type="button" aria-label="CalibracaoSemDica">${svg}</button>`
      + `<span class="dica"><button type="button" aria-label="CalibracaoComDica" aria-describedby="d-calib">${svg}</button>`
      + '<span id="d-calib" role="tooltip" class="dica__texto">Explica o que o botao de calibracao faz.</span></span>'
      + '<h2>CalibracaoTitulo <span class="dica"><button type="button" class="dica__gatilho" aria-label="Ajuda sobre o titulo" aria-describedby="d-calib-t">?</button>'
      + '<span id="d-calib-t" role="tooltip" class="dica__texto">Dica que nao devia estar no titulo.</span></span></h2>'
      + '<div class="card"><div class="campo"><label for="calib-cep">CalibracaoCampo</label>'
      + '<input id="calib-cep" pattern="\\d{8}" aria-describedby="d-calib-c">'
      + '<span id="d-calib-c" role="tooltip" class="dica__texto">Oito numeros, sem o hifen.</span></div></div>';
    document.body.appendChild(caixa);
  });
  const achados = await varrer(pagina);
  await contexto.close();
  const tem = (s) => achados.some((a) => a.includes(s));
  const ok = tem('CalibracaoSemDica') && !tem('CalibracaoComDica') && tem('CalibracaoTitulo') && !tem('CalibracaoCampo');
  if (!ok) {
    console.log(`NAO VERIFICOU: calibracao falhou (sem dica acusado=${tem('CalibracaoSemDica')}, com dica poupado=${!tem('CalibracaoComDica')}, `
      + `titulo avisado=${tem('CalibracaoTitulo')}, campo em painel poupado=${!tem('CalibracaoCampo')})`);
    process.exit(2);
  }
  console.log('calibracao: icone sem dica acusado e com dica aceito; dica em titulo avisada e dica de campo em painel "card" aceita');
}

// Calibracao do universo ampliado: dentro da aba OCULTA "vlan" da calculadora, o botao so com icone sem dica tem de
// aparecer SO no estado aba:vlan (e nao no inicial).
{
  const r = await medirRota('/calculadora', (pagina) => pagina.evaluate(() => {
    const painel = document.querySelector('[data-calc-panel="vlan"]');
    const b = document.createElement('button');
    b.type = 'button'; b.setAttribute('aria-label', 'CalibracaoAbaSemDica');
    b.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="8"/></svg>';
    painel.appendChild(b);
  }));
  const naAba = (r.achados || []).some((a) => a.startsWith('aba:vlan|') && a.includes('CalibracaoAbaSemDica'));
  const foraDoInicial = !(r.achados || []).some((a) => a.startsWith('inicial|') && a.includes('CalibracaoAba'));
  if (!naAba || !foraDoInicial) {
    console.log(`NAO VERIFICOU: calibracao da aba oculta falhou (acusou na aba=${naAba}, fora do inicial=${foraDoInicial})`);
    process.exit(2);
  }
  console.log('calibracao: botao sem dica dentro de aba oculta acusado so em aba:vlan');
}

const resultado = {};
const falhasDeEstado = {};
let naoAbriram = 0;
const alvos = paginas.map((r) => [r, false]);
if (CHAVE && (!SO.length || SO.includes('/telemetria'))) { alvos.push(['/telemetria', true]); }
for (const [rota, comSessao] of alvos) {
  const chave = comSessao ? rota + ' (logado)' : rota;
  try {
    const r = await medirRota(rota, null, comSessao);
    if (r.status !== 200) { naoAbriram++; console.log(`  ${chave} NAO VERIFICOU (status ${r.status})`); continue; }
    resultado[chave] = r.achados;
    if (r.falhas.length) {
      falhasDeEstado[chave] = r.falhas;
      console.log(`  ${chave} estado(s) NAO VERIFICADO(S): ${r.falhas.join(' ; ')}`);
    }
    for (const a of r.achados) { console.log(`  ${chave}: ${a}`); }
  } catch (e) {
    naoAbriram++;
    console.log(`  ${chave} NAO VERIFICOU (${String(e).slice(0, 80)})`);
  }
}
await navegador.close();

if (SAIDA_JSON) { writeFileSync(SAIDA_JSON, JSON.stringify({ resultado, falhasDeEstado }, null, 1) + '\n'); }
for (const [rota, estados] of Object.entries(NAO_MEDIDOS).filter(([r]) => !SO.length || SO.includes(r))) {
  for (const [estado, motivo] of Object.entries(estados)) { console.log(`  nao medido: ${rota} ${estado} — ${motivo}`); }
}
if (externosAbortados) { console.log(`  pedidos do navegador para fora de ${ORIGEM} abortados: ${externosAbortados}`); }
const todos = Object.values(resultado).flat();
const nFalhas = todos.filter((a) => a.split('|')[1] === 'falha').length;
const nAvisos = todos.filter((a) => a.split('|')[1] === 'aviso').length;
const estadosFalhos = Object.values(falhasDeEstado).reduce((n, l) => n + l.length, 0);
console.log(`\ndicas: ${Object.keys(resultado).length} pagina(s) medida(s) | falhas ${nFalhas} | avisos ${nAvisos} | `
  + `paginas nao verificadas ${naoAbriram} | estados nao verificados ${estadosFalhos}`);
if (nFalhas + nAvisos > 0) { process.exit(1); }
if (naoAbriram > 0 || estadosFalhos > 0) { console.log('NAO VERIFICOU: houve pagina ou estado que nao abriu — isso nao e aprovacao.'); process.exit(2); }
process.exit(0);
