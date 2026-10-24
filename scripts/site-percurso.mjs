/**
 * Percurso do site para as catracas de interface: abre cada pagina do inventario, clica cada aba e executa um calculo
 * realista em cada painel de resultado, chamando a medicao da catraca em cada estado.
 *
 * PROPOSITO DE NEGOCIO: a catraca das dicas e a dos icones perguntam coisas diferentes ao mesmo site, mas precisam
 * do MESMO universo — as mesmas paginas, as mesmas abas, os mesmos resultados. Um percurso so evita que as duas
 * divirjam sobre o que e "o site inteiro" (uma medir uma aba que a outra nao abre e as duas parecerem verdes).
 *
 * INVARIANTES:
 *  - um elemento pertence a UM estado so: o primeiro em que ficou visivel (inicial, abas, resultados) — quem garante
 *    e a medicao, que marca o que ja viu; o percurso so define a ordem (inicial, abas em ordem de documento, acoes);
 *  - a aba visitada fica marcada com o atributo informado pela catraca, para cada catraca ter o proprio registro;
 *  - o Google Tradutor fica bloqueado e todo pedido do navegador para fora do servidor medido e abortado e contado.
 *
 * COMPORTAMENTO EM CASO DE FALHA: nao lanca para a catraca por acao que falhou — a acao entra em `falhas` com o
 * motivo, e a catraca decide (estado nao verificado nunca e aprovacao); pagina que nao abre devolve so o status.
 */
import { chromium } from 'playwright';

export const GOOGLE = /^https?:\/\/([a-z0-9-]+\.)*(google\.com|googleapis\.com|gstatic\.com)\//i;
const ABAS = '[role=tab], [data-bs-toggle=tab], [data-bs-toggle=pill]';

/**
 * Um calculo realista por painel de resultado. Os campos ja vem com exemplo na maioria das telas; onde o campo vem
 * vazio (so placeholder), o valor preenchido e um exemplo didatico privado ou de documentacao (RFC 5737/3849).
 * botao: nome acessivel do botao (o rotulo visivel; o texto da dica nao entra no nome).
 * tipo: prefixo do estado ('resultado' quando omitido; 'etapa' para passo de laboratorio).
 * cliques: quantas vezes clicar no botao antes de medir (1 quando omitido); acumula com a acao anterior da mesma pagina.
 */
export const ACOES = {
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

/** Estados que as catracas NAO medem, com o motivo. Aparecem na saida; nunca contam como aprovados. */
export const NAO_MEDIDOS = {
  '/analise': { 'resultado:dominio': 'consulta DNS real a resolvedor externo' },
  '/ipv6/analise': { 'resultado:dominio': 'consulta DNS real (AAAA) a resolvedor externo' },
  '/trafego': { 'resultado:aovivo': 'leitura ao vivo com temporizador; o conteudo muda a cada leitura' },
  '/seguranca': { 'resultado:canvas': 'a tela de desenho comeca vazia; testar fluxo exige montar topologia a mao' },
  '/localizacao': { 'resultado:geoip/privacidade/cep': 'geolocalizacao, CEP e mapa consultam servicos externos' },
  '/informacoes': { 'resultado:consulta': 'geolocalizacao do IP consulta servico externo' },
};

/**
 * Cria o percurso. `varrer(pagina)` e a medicao da catraca (devolve a lista de achados do estado); `marcaAba` e o
 * atributo que registra a aba ja visitada. Com `chave`, entra pela contingencia e guarda a sessao para as rotas
 * logadas. Devolve { navegador, abrir, medirRota, externos() }.
 */
export async function criarPercurso({ base, chave = '', varrer, marcaAba }) {
  const origem = new URL(base).origin;
  let externosAbortados = 0;
  const navegador = await chromium.launch();
  let sessao;
  if (chave) {
    const c = await navegador.newContext();
    const p = await c.newPage();
    await p.goto(base + '/login/?modo=contingencia');
    await p.locator('input[name=adminKey]').fill(chave);
    await Promise.all([p.waitForNavigation(), p.locator('input[name=adminKey]').press('Enter')]);
    if (!new URL(p.url()).pathname.startsWith('/telemetria')) { throw new Error('o login de contingencia nao abriu a telemetria'); }
    sessao = await c.storageState();
    await c.close();
  }

  async function abrir(rota, comSessao = false) {
    const contexto = await navegador.newContext({ viewport: { width: 1440, height: 900 }, storageState: comSessao ? sessao : undefined });
    await contexto.route(GOOGLE, (r) => r.abort());
    await contexto.route((url) => url.origin !== origem && !GOOGLE.test(url.href) && /^https?:/.test(url.href), (r) => {
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
    const resp = await pagina.goto(base + rota, { waitUntil: 'load', timeout: 45000 });
    await pagina.waitForTimeout(400);
    return { contexto, pagina, status: resp ? resp.status() : 0 };
  }

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
      if (!(await h.evaluate((t, m) => t.hasAttribute(m), marcaAba))) { lista.push(h); }
    }
    for (const h of lista) { await h.evaluate((t, m) => t.setAttribute(m, ''), marcaAba); }
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

  async function clicarAba(pagina, aba) {
    const h = pagina.locator(`[role=tab][data-tab="${aba}"], [role=tab][data-calc-tab="${aba}"]`).filter({ visible: true }).first();
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

  return { navegador, abrir, medirRota, externos: () => externosAbortados };
}
