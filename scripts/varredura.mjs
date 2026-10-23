/**
 * VARREDURA EXAUSTIVA DO SITE — consome o inventario, exercita cada item.
 *
 * PROPOSITO DE NEGOCIO: teste de backend prova que o servidor respondeu 200.
 * Nao prova que a tela apareceu. Texto cortado, botao coberto, contraste
 * ilegivel, aba que nao troca, expressao Qute vazando como `{lab_count}`, icone
 * que virou a palavra "sync_alt", overflow horizontal no celular e erro de
 * sintaxe em .js sao defeitos que respondem 200 e so o navegador revela. Este
 * script abre cada pagina do inventario num Chromium de verdade, clica em cada
 * gatilho, preenche cada formulario, forca cada modal e mede o que a pessoa veria.
 *
 * INVARIANTES DO DOMINIO:
 *  - NADA aqui e escolhido de memoria: a lista vem de `inventario.json`, extraido
 *    do codigo por `inventario.mjs`;
 *  - detector so vale depois de ter sido visto ACUSANDO um defeito injetado —
 *    detector cego devolve "nenhum problema" e e indistinguivel de sistema sadio;
 *  - rota mutante (logout, limpar console, sincronizar dataset) e download nunca
 *    sao navegados: mudariam o estado que as telas seguintes exibem, e o
 *    instrumento passaria a medir outra coisa;
 *  - tres estados de saida, nunca dois: 0 passou · 1 reprovou · 2 NAO VERIFICOU
 *    (que nao e aprovacao).
 *
 * COMPORTAMENTO EM CASO DE FALHA: sai 2 quando o inventario nao existe, quando a
 * aplicacao nao responde ou quando qualquer detector falha na calibragem — nesses
 * casos nao imprime "nenhum defeito", porque nao olhou. Sai 1 quando ha achados.
 * Falha de um gatilho isolado nao derruba a varredura: vira achado e a lista segue.
 *
 * USO:
 *   node scripts/inventario.mjs                  # 1. extrai a lista do codigo
 *   node scripts/varredura.mjs                   # 2. varre localhost:8081
 *   node scripts/varredura.mjs http://host:8081  # outra base
 *   node scripts/varredura.mjs --vigiar          # tempo real: repete e mostra so o NOVO
 *   node scripts/varredura.mjs --so-paginas      # rapido: pula cliques e formularios
 *   node scripts/varredura.mjs --telas=320,390,768,1024,1440,1920   # responsividade: N larguras
 *   node scripts/varredura.mjs --com-google      # NAO bloqueia o tradutor (ver abaixo)
 *   node scripts/varredura.mjs --rotas=/analise,/trafego   # remede so essas paginas na etapa A
 *
 * O TRADUTOR DO GOOGLE E BLOQUEADO POR PADRAO (01/10/2026): cada pagina carrega o element.js, e
 * uma varredura de centenas de cargas em sequencia fez o Google mandar o endereco inteiro para o
 * captcha (google.com/sorry) — inclusive o navegador de quem estava usando o site na mesma rede.
 * A traducao tem roteiro proprio, que toca o Google uma unica vez: scripts/verificar-tradutor.mjs.
 */
import { chromium } from 'playwright';
import { readFileSync, writeFileSync, appendFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const AQUI = dirname(fileURLToPath(import.meta.url));
const RAIZ = join(AQUI, '..');
const INVENTARIO = join(AQUI, 'inventario.json');
const RELATORIO = join(AQUI, 'relatorio-varredura.txt');

const argumentos = process.argv.slice(2);
const VIGIAR = argumentos.includes('--vigiar');
const SO_PAGINAS = argumentos.includes('--so-paginas');
const INTERVALO = Number((argumentos.find((a) => a.startsWith('--intervalo=')) || '').split('=')[1] || 60);
const BASE_CLI = argumentos.find((a) => a.startsWith('http'));

/** Quantos achados visuais uma unica pagina pode ocupar no relatorio. */
const TETO_POR_PAGINA = 12;

/** Viewports medidos: o desktop de trabalho e o celular onde o overflow aparece. */
const TELAS_PADRAO = [
  { nome: 'desktop', width: 1440, height: 900 },
  { nome: 'celular', width: 390, height: 844 },
];

/**
 * `--telas=320,390,1920` troca as duas telas padrao por uma lista de larguras (responsividade "em
 * qualquer tela"). A primeira largura da lista e a usada nas etapas de clique (B em diante), entao a
 * lista e reordenada para comecar pela maior — a etapa de cliques continua medindo o desktop.
 * Altura: retrato (largura × 2,16) ate 768 px, paisagem comum (largura × 0,625) acima.
 */
const TELAS = (() => {
  const arg = argumentos.find((a) => a.startsWith('--telas='));
  if (!arg) { return TELAS_PADRAO; }
  const larguras = arg.split('=')[1].split(',').map((x) => Number(x.trim())).filter((n) => Number.isInteger(n) && n >= 240 && n <= 3840);
  if (larguras.length === 0) {
    console.error('  NAO VERIFICADO: --telas sem nenhuma largura valida (240 a 3840)');
    process.exit(2);
  }
  return [...new Set(larguras)].sort((a, b) => b - a).map((w) => ({
    nome: w + 'px', width: w, height: w <= 768 ? Math.round(w * 2.16) : Math.round(w * 0.625),
  }));
})();
const COM_GOOGLE = argumentos.includes('--com-google');
/**
 * `--rotas=/a,/b` restringe a etapa A as rotas listadas (caminho exato, sem a query). Serve para
 * remedir so o que foi corrigido; a calibragem roda inteira do mesmo jeito. Rota pedida que nao
 * esta no inventario derruba a varredura com 2: medir zero paginas nao e "nenhum defeito".
 */
const ROTAS = (() => {
  const arg = argumentos.find((a) => a.startsWith('--rotas='));
  return arg ? arg.split('=')[1].split(',').map((x) => x.trim()).filter(Boolean) : null;
})();
/** Hosts do tradutor do Google (bloqueados por padrao — ver o cabecalho deste arquivo). */
const GOOGLE_TRADUTOR = /(^|\.)(translate\.google\.com|translate\.googleapis\.com|translate-pa\.googleapis\.com|www\.google\.com)$/i;

/**
 * Gatilhos que NAO devem ser clicados pela varredura.
 *
 * PROPOSITO: separar "nao clicou porque falhou" de "nao clicou de proposito".
 * Impressao abre dialogo nativo do sistema e trava o Chromium headless; GPS pede
 * permissao; copiar exige clipboard; exportar baixa arquivo; sair encerra a
 * sessao no meio da varredura. Cada um destes aparece no relatorio como PULADO
 * COM MOTIVO — silenciar seria dizer "cobri tudo" sobre o que ficou de fora.
 */
const NAO_CLICAR = [
  [/imprimir|pdf|print/i, 'abre dialogo de impressao do sistema'],
  [/export|download|baixar|\.csv|\.zip/i, 'dispara download de arquivo'],
  [/copiar|copy/i, 'exige permissao de area de transferencia'],
  [/gps|geoloc/i, 'pede permissao de localizacao ao navegador'],
  [/sair|logout|sincronizar|limpar-console/i, 'muda estado da sessao ou do servidor'],
  [/translate|lang/i, 'chama servico externo de traducao'],
];

const log = (linha = '') => {
  console.log(linha);
  try { appendFileSync(RELATORIO, linha + '\n'); } catch { /* relatorio e conveniencia, nao invariante */ }
};

// ===========================================================================
// OS DETECTORES — rodam dentro da pagina, no navegador
// ===========================================================================

/**
 * Mede a pagina inteira e devolve a lista de defeitos visiveis.
 *
 * PROPOSITO DE NEGOCIO: e o olho da varredura. Reproduz o que uma pessoa veria
 * de errado na tela: texto que nao cabe, controle fora do alcance ou coberto,
 * texto que some no fundo, modal transparente, imagem quebrada, marcador de
 * template vazando e icone que virou palavra.
 *
 * INVARIANTES: so mede o que esta VISIVEL (aba inativa, `display:none` e
 * opacidade baixa ficam de fora, senao metade dos achados seria de coisa que
 * ninguem ve); contraste so e avaliado quando da para determinar a cor de fundo
 * solida — sobre gradiente o detector se declara cego naquele elemento em vez de
 * chutar; cada achado carrega o seletor e o texto, para dar para ir conferir.
 *
 * COMPORTAMENTO EM CASO DE FALHA: erro em um detector nao derruba os outros —
 * cada bloco e independente e o que quebrar aparece como `erro-detector`.
 *
 * @returns {Array<{t:string,s:string,d:string}>} tipo, seletor e detalhe
 */
function DETECTORES() {
  const out = [];
  const W = window.innerWidth;
  const H = window.innerHeight;

  const luz = (cor) => {
    if (!cor || typeof cor !== 'string') { return null; }
    const m = cor.match(/[\d.]+/g);
    if (!m) { return null; }
    if (m.length > 3 && parseFloat(m[3]) < 0.35) { return null; }
    const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); };
    return 0.2126 * f(+m[0]) + 0.7152 * f(+m[1]) + 0.0722 * f(+m[2]);
  };
  // Conteudo de <details> FECHADO nao aparece na tela, mas o Chromium ainda devolve
  // uma caixa calculada para ele: medido em /ferramentas/rede, os botoes da saida
  // simulada eram acusados de "coberto" e "fora da janela" sem nunca estarem visiveis.
  // O resumo (<summary>) do details fechado continua visivel e continua medido.
  const emDetailsFechado = (el) => {
    for (let d = el.closest('details'); d; d = d.parentElement && d.parentElement.closest('details')) {
      if (!d.open) {
        const s = el.closest('summary');
        if (!(s && s.parentElement === d)) { return true; }
      }
    }
    return false;
  };
  const vis = (el) => {
    const r = el.getBoundingClientRect();
    const cs = getComputedStyle(el);
    return r.width > 12 && r.height > 8 && cs.visibility !== 'hidden'
      && cs.display !== 'none' && +cs.opacity >= 0.15 && !emDetailsFechado(el);
  };
  const nome = (el) => {
    if (!el) { return '?'; }
    const cls = typeof el.className === 'string' && el.className.trim()
      ? '.' + el.className.trim().split(/\s+/).slice(0, 2).join('.') : '';
    return el.tagName.toLowerCase() + (el.id ? '#' + el.id : cls);
  };
  const bloco = (rotulo, fn) => { try { fn(); } catch (e) { out.push({ t: 'erro-detector', s: rotulo, d: String(e).slice(0, 90) }); } };

  const raiz = document.body;
  const ignorar = (el) => el.closest('#google_translate_element, .skiptranslate, script, style, head');

  // 1. Texto cortado -------------------------------------------------------
  bloco('texto-cortado', () => {
    raiz.querySelectorAll('*').forEach((el) => {
      if (el.children.length > 0 || !vis(el) || ignorar(el)) { return; }
      const t = (el.innerText || '').trim();
      if (t.length < 3) { return; }
      const cs = getComputedStyle(el);
      if (cs.textOverflow === 'ellipsis') { return; }
      if (!(cs.overflow === 'hidden' || cs.overflowX === 'hidden')) { return; }
      if (cs.whiteSpace === 'nowrap' && el.scrollWidth > el.clientWidth + 6) {
        out.push({ t: 'texto CORTADO', s: nome(el), d: `"${t.slice(0, 40)}" (${el.scrollWidth}>${el.clientWidth})` });
      } else if (el.scrollHeight > el.clientHeight + 8 && cs.overflowY !== 'auto' && cs.overflowY !== 'scroll') {
        out.push({ t: 'texto cortado (altura)', s: nome(el), d: `"${t.slice(0, 40)}"` });
      }
    });
  });

  // 2. Controle fora da janela --------------------------------------------
  bloco('controle-fora', () => {
    raiz.querySelectorAll('button,a[href],input,select,textarea').forEach((el) => {
      if (!vis(el) || ignorar(el)) { return; }
      const r = el.getBoundingClientRect();
      if (r.left < -24 || r.right > W + 24) {
        out.push({ t: 'controle fora da janela', s: nome(el), d: `x ${Math.round(r.left)}→${Math.round(r.right)} (janela ${W})` });
      }
    });
  });

  // 3. Controle coberto ----------------------------------------------------
  bloco('controle-coberto', () => {
    /**
     * Tapa-buraco de propósito ou defeito?
     *
     * Tooltip e popover cobrem o botao enquanto o mouse esta em cima — e o
     * comportamento correto deles. Barra fixa/grudenta cobre o que rolou para
     * baixo dela — o usuario rola e alcanca. Nenhum dos dois e defeito, e os
     * dois entupiam o relatorio. Ja uma capa que cobre a TELA INTEIRA prende o
     * usuario de verdade — e e o caso que a calibragem injeta.
     */
    const areaTela = W * H;
    const cobreDePropósito = (sob) => {
      if (sob.closest('.tooltip,.popover,[role=tooltip]')) { return true; }
      // Sobe a arvore: `elementFromPoint` devolve o filho mais interno, e quem e
      // fixo/grudento e a barra ANCESTRAL. Checar so o elemento devolvido dizia
      // "position: static" e a nav grudenta virava achado a cada rolagem.
      for (let n = sob; n && n !== document.documentElement; n = n.parentElement) {
        const cs = getComputedStyle(n);
        if (cs.position === 'fixed' || cs.position === 'sticky') {
          const r = n.getBoundingClientRect();
          return (r.width * r.height) < areaTela * 0.6;
        }
      }
      return false;
    };
    [...raiz.querySelectorAll('button,a[href],input[type=submit]')].filter(vis).slice(0, 120).forEach((el) => {
      if (ignorar(el)) { return; }
      const r = el.getBoundingClientRect();
      const cx = Math.round(r.left + r.width / 2);
      const cy = Math.round(r.top + r.height / 2);
      if (cx < 0 || cy < 0 || cx > W || cy > H) { return; }
      const sob = document.elementFromPoint(cx, cy);
      if (!sob || sob === el || el.contains(sob) || sob.contains(el)) { return; }
      if (cobreDePropósito(sob)) { return; }
      out.push({ t: 'controle COBERTO', s: nome(el), d: `"${(el.innerText || el.value || '').trim().slice(0, 24).replace(/\s+/g, ' ')}" por ${nome(sob)}` });
    });
  });

  // 4. Texto ilegivel (contraste) -----------------------------------------
  bloco('contraste', () => {
    // Sobre gradiente ou imagem nao ha cor solida para comparar. O detector se
    // declara cego naquele elemento (null) em vez de comparar com a cor que esta
    // por baixo e inventar um numero — falso positivo em massa mata a lista.
    const fundoDe = (el) => {
      let n = el;
      while (n && n !== document.documentElement) {
        const c = getComputedStyle(n);
        if (c.backgroundImage && c.backgroundImage !== 'none') { return null; }
        const l = luz(c.backgroundColor);
        if (l !== null) { return c.backgroundColor; }
        n = n.parentElement;
      }
      return getComputedStyle(document.body).backgroundColor;
    };
    const folha = 'button,a,label,td,th,h1,h2,h3,h4,h5,option,summary,legend,b,strong,dd,dt,span,p,small,li,code,pre';
    raiz.querySelectorAll('*').forEach((el) => {
      if (ignorar(el)) { return; }
      if (el.children.length > 0) {
        if (!el.matches(folha)) { return; }
        let soTexto = true;
        el.childNodes.forEach((n) => { if (n.nodeType === 1) { soTexto = false; } });
        if (!soTexto) { return; }
      }
      if (!vis(el)) { return; }
      // Radio, checkbox e afins tem `value` mas nao DESENHAM texto. Medir o
      // contraste do value deles acusava 3 "textos ilegiveis" em /calculadora
      // que eram os tres radios do criterio de divisao — nao havia texto ali.
      if (el.tagName === 'INPUT'
          && ['radio', 'checkbox', 'range', 'color', 'file', 'image', 'hidden'].includes(el.type)) { return; }
      const t = (el.innerText || el.value || el.placeholder || '').trim();
      if (!t) { return; }
      const cs = getComputedStyle(el);
      const f = fundoDe(el);
      const x = luz(f);
      const y = luz(cs.color);
      if (x === null || y === null) { return; }
      const c = (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
      if (c < 3.0) {
        out.push({ t: `ilegivel ${c.toFixed(2)}:1`, s: nome(el), d: `fundo ${f} tinta ${cs.color} "${t.slice(0, 30)}"` });
      }
    });
  });

  // 5. Modal / dialogo -----------------------------------------------------
  bloco('modal', () => {
    document.querySelectorAll('.modal,[role=dialog],dialog[open]').forEach((el) => {
      if (!vis(el)) { return; }
      const r = el.getBoundingClientRect();
      const cs = getComputedStyle(el);
      const conteudo = el.querySelector('.modal-content') || el;
      const csConteudo = getComputedStyle(conteudo);
      if (r.right < 8 || r.left > W - 8 || r.bottom < 8 || r.top > H - 8) {
        out.push({ t: 'modal FORA da tela', s: nome(el), d: `x=${Math.round(r.left)} y=${Math.round(r.top)}` });
      }
      if (luz(csConteudo.backgroundColor) === null
          && (!csConteudo.backgroundImage || csConteudo.backgroundImage === 'none')) {
        out.push({ t: 'modal SEM FUNDO', s: nome(conteudo), d: csConteudo.backgroundColor });
      }
      if (r.width < 120 || r.height < 60) {
        out.push({ t: 'modal pequeno demais', s: nome(el), d: `${Math.round(r.width)}x${Math.round(r.height)}` });
      }
      if (cs.zIndex && +cs.zIndex < 0) {
        out.push({ t: 'modal atras do conteudo', s: nome(el), d: `z-index ${cs.zIndex}` });
      }
    });
  });

  // 6. Overflow horizontal da pagina --------------------------------------
  bloco('overflow', () => {
    const excesso = document.documentElement.scrollWidth - W;
    if (excesso > 2) {
      const culpados = [];
      raiz.querySelectorAll('*').forEach((el) => {
        if (!vis(el) || ignorar(el)) { return; }
        const r = el.getBoundingClientRect();
        if (r.right > W + 2 && r.width <= W) { culpados.push(`${nome(el)} ate x=${Math.round(r.right)}`); }
      });
      out.push({ t: 'PAGINA rola na horizontal', s: 'documento', d: `${excesso}px alem de ${W}px · ${culpados.slice(0, 3).join(' | ') || 'origem nao isolada'}` });
    }
  });

  // 7. Imagem quebrada -----------------------------------------------------
  bloco('imagem', () => {
    document.querySelectorAll('img').forEach((img) => {
      if (!img.complete) { return; }
      if (img.naturalWidth === 0) {
        out.push({ t: 'IMAGEM quebrada', s: nome(img), d: (img.getAttribute('src') || '').slice(0, 80) });
      }
    });
  });

  // 8. Expressao de template vazando --------------------------------------
  bloco('qute', () => {
    // `{item.id}`, `{lab_count}`, `{#if ...}` no texto renderizado significam que
    // o Qute nao resolveu a expressao. Este projeto ja pagou por isso: `{count}`
    // nao existe como metadado de laco, e o erro so aparece na renderizacao.
    const re = /\{#?[a-zA-Z_][\w.$]*(?:\s+[^}]*)?\}/;
    raiz.querySelectorAll('*').forEach((el) => {
      if (el.children.length > 0 || !vis(el) || ignorar(el)) { return; }
      // Bloco de codigo e documentacao mostram chaves DE PROPOSITO: a pagina
      // /documentacao renderiza o README, que cita `${HTTP_PORT}` e trechos de
      // template. Acusar ali seria denunciar o conteudo como se fosse defeito.
      if (el.closest('code,pre,kbd,samp,.mermaid,textarea')) { return; }
      const t = (el.innerText || '').trim();
      const achado = t.match(re);
      if (achado && !/^\s*\{\s*\}\s*$/.test(achado[0])) {
        out.push({ t: 'TEMPLATE nao renderizado', s: nome(el), d: achado[0].slice(0, 50) });
      }
    });
  });

  // 9. Icone Material Symbols que virou palavra ---------------------------
  bloco('icone', () => {
    // Nome invalido nao vira ligadura: o navegador desenha o texto literal
    // ("sync_alt"), muito mais largo que o quadrado do icone. Este projeto ja
    // teve `http` (que nao existe) escrito por extenso na tela da telemetria.
    let total = 0;
    let suspeitos = [];
    document.querySelectorAll('.material-symbols-outlined').forEach((el) => {
      if (!vis(el)) { return; }
      total++;
      const fonte = parseFloat(getComputedStyle(el).fontSize) || 24;
      const texto = (el.textContent || '').trim();
      if (!texto) { return; }
      // Mede a LARGURA DO TEXTO, nao a da caixa: `.home-module-icon` e
      // `display:block` e ocupa 214px com o glifo desenhado certinho em 32px.
      // Medir a caixa acusava 14 icones sadios na home — falso positivo puro.
      const faixa = document.createRange();
      faixa.selectNodeContents(el);
      const larguraTexto = faixa.getBoundingClientRect().width;
      if (larguraTexto > fonte * 1.6) {
        suspeitos.push({ s: nome(el), d: `"${texto}" desenha ${Math.round(larguraTexto)}px de texto com fonte ${Math.round(fonte)}px` });
      }
    });
    // Controle: se quase todos falharam, o problema e a FONTE que nao carregou,
    // nao 40 nomes de icone errados. Um achado, nao quarenta.
    if (total >= 4 && suspeitos.length / total > 0.6) {
      out.push({ t: 'FONTE de icones nao carregou', s: '.material-symbols-outlined', d: `${suspeitos.length}/${total} desenhados como texto` });
    } else {
      suspeitos.forEach((x) => out.push({ t: 'ICONE virou texto', s: x.s, d: x.d }));
    }
  });

  return out;
}

/**
 * Assinatura do estado da pagina: muda quando qualquer coisa visivel mudou.
 *
 * PROPOSITO DE NEGOCIO: e o criterio de "o controle respondeu". Sem ela, um
 * botao morto e um botao que funciona sao indistinguiveis para a varredura.
 *
 * INVARIANTE: precisa registrar QUAL elemento esta ativo, nao QUANTOS. Contar
 * ativos dava colisao exata na troca de aba (um sai, outro entra, o total nao
 * muda) e a varredura acusava "aba wildcard NAO mudou nada" numa aba que troca
 * perfeitamente. Identidade, nunca cardinalidade.
 *
 * FALHA: elemento sem id, sem data-tab e sem classe entra como string vazia —
 * no pior caso a assinatura fica menos sensivel, nunca mais barulhenta.
 */
function ASSINATURA() {
  const alvo = document.querySelector('main') || document.body;
  const ativos = [...document.querySelectorAll('.active,.show,[aria-expanded="true"],[aria-selected="true"]')]
    .map((e) => e.dataset.tab || e.dataset.tabPanel || e.id || (typeof e.className === 'string' ? e.className.slice(0, 24) : ''))
    .join(',');
  return [
    alvo.innerHTML.length,
    (alvo.innerText || '').slice(0, 120),
    ativos,
    location.pathname + location.search,
    document.querySelectorAll('*').length,
  ].join('§');
}

/**
 * Tira tooltip e popover da frente antes de interagir.
 *
 * PROPOSITO: o projeto poe tooltip do Bootstrap em quase todo controle. Depois
 * do primeiro clique, o balao fica sobre o proximo botao e o Playwright espera
 * para sempre por um elemento "que recebe evento de ponteiro" — a varredura
 * acusava 9 gatilhos "nao clicaveis" em /trafego que funcionam a mao.
 * INVARIANTE: so remove o balao (efemero); nunca toca no conteudo da pagina.
 * FALHA: se nao houver nada para limpar, nao faz nada e nao lanca.
 */
async function limparBaloes(pagina) {
  await pagina.mouse.move(2, 2).catch(() => {});
  await pagina.evaluate(() => {
    document.querySelectorAll('.tooltip,.popover,[role=tooltip]').forEach((e) => e.remove());
  }).catch(() => {});
}

// ===========================================================================
// INFRAESTRUTURA DA VARREDURA
// ===========================================================================

/**
 * Preenche um campo com valor plausivel para o dominio de redes do projeto.
 *
 * PROPOSITO: formulario so revela defeito quando recebe entrada que o backend
 * aceita. Mandar "teste" num campo de IPv4 testa a mensagem de erro, nao a tela
 * de resultado — e e a tela de resultado que tem os defeitos visuais.
 *
 * INVARIANTE: o valor sai da CLASSE que o proprio projeto pos no campo
 * (`input-ipv4`, `input-cidr`, `input-numeric`), que e a convencao documentada
 * no README; nome e placeholder so entram como desempate.
 *
 * FALHA: campo que nao casa com nenhuma regra recebe um valor neutro e o achado,
 * se houver, sai marcado com o valor usado — para dar para reproduzir a mao.
 */
function valorPara(campo) {
  const chave = `${campo.classe} ${campo.name || ''} ${campo.seletor}`.toLowerCase();
  // Formulários de IPv6 (página IPv6 e projeto de rede IPv6) e a ACL: o valor genérico de IPv4
  // levava o backend a recusar com 400 (correto) e a varredura nunca via a tela de resultado.
  if (/#ipv6|#proj|#vlanbase|#vlanpfx/.test(chave)) {
    if (/bloco|base/.test(chave)) { return '2001:db8:abcd::/48'; }
    if (/alvo|prefixolan|vlanpfx/.test(chave)) { return '64'; }
    if (/prefixowan/.test(chave)) { return '127'; }
    if (/pref64/.test(chave)) { return '2001:db8:1:2::/64'; }
    if (/mac/.test(chave)) { return '00:1A:2B:3C:4D:5E'; }
    if (/faixaini/.test(chave)) { return '2001:db8::1'; }
    if (/faixafim/.test(chave)) { return '2001:db8::ff'; }
    if (/sumprefixos/.test(chave)) { return '2001:db8:0::/64\n2001:db8:1::/64'; }
    if (/endereco|compa|compb|nibend/.test(chave)) { return '2001:db8::1'; }
  }
  if (/#aclregra/.test(chave)) { return 'permit tcp any any eq 443'; }
  if (/input-ipv4|\bip\b|ipv4|endereco/.test(chave)) { return '192.168.10.25'; }
  if (/input-cidr|cidr|prefixo|mascara/.test(chave)) { return '24'; }
  if (/ipv6/.test(chave)) { return '2001:db8::1'; }
  if (/hex|dump|pacote/.test(chave)) { return '00112233445566778899aabb08004500002800010000400600007f0000017f000001'; }
  if (/cep/.test(chave)) { return '01001000'; }
  if (/dominio|host|dns|url/.test(chave)) { return 'example.com'; }
  if (/porta|port/.test(chave)) { return '443'; }
  if (/vlan/.test(chave)) { return '10'; }
  if (/as\b|asn|processo/.test(chave)) { return '65001'; }
  if (/input-numeric|hosts|quantidade|numero/.test(chave)) { return '30'; }
  if (/chave|key|senha|password/.test(chave)) { return 'valor-de-teste-da-varredura'; }
  return '10.0.0.1';
}

const criarPagina = async (contexto, tela) => {
  if (!COM_GOOGLE && !contexto.__tradutorBloqueado) {
    contexto.__tradutorBloqueado = true;
    await contexto.route('**/*', (rota) => {
      let host = '';
      try { host = new URL(rota.request().url()).hostname; } catch { /* url opaca (data:, blob:) */ }
      return GOOGLE_TRADUTOR.test(host) ? rota.abort('blockedbyclient') : rota.continue();
    });
  }
  const pagina = await contexto.newPage();
  await pagina.setViewportSize({ width: tela.width, height: tela.height });
  return pagina;
};

/** Liga os ouvintes de erro do navegador a um acumulador da pagina atual. */
function escutar(pagina, caixa) {
  // /login/chave e o endpoint de autenticacao: um 401 ali e o mecanismo REJEITANDO
  // uma chave errada — comportamento correto, esperado durante a sondagem de login
  // deste proprio instrumento. Nao e defeito do site, entao nao vira achado.
  const ehRuidoDeAuth = (url) => /\/login\/chave/.test(url || '');
  // O bloqueio do tradutor e escolha deste instrumento (cabecalho), nao defeito do site.
  const ehTradutorBloqueado = (url) => {
    if (COM_GOOGLE) { return false; }
    try { return GOOGLE_TRADUTOR.test(new URL(url).hostname); } catch { return false; }
  };
  pagina.on('console', (msg) => {
    if (msg.type() !== 'error') { return; }
    if (/Failed to load resource/.test(msg.text())
        && (ehRuidoDeAuth(msg.location()?.url) || ehTradutorBloqueado(msg.location()?.url))) { return; }
    caixa.console.push(msg.text().slice(0, 160));
  });
  pagina.on('pageerror', (erro) => caixa.js.push(String(erro).split('\n')[0].slice(0, 160)));
  pagina.on('requestfailed', (req) => {
    const motivo = req.failure()?.errorText || '';
    if (/ERR_ABORTED/.test(motivo) || ehTradutorBloqueado(req.url())) { return; }
    caixa.rede.push(`${motivo} ${req.url().slice(0, 100)}`);
  });
  pagina.on('response', (res) => {
    if (res.status() >= 400 && !ehRuidoDeAuth(res.url())) {
      caixa.http.push(`${res.status()} ${res.url().slice(0, 100)}`);
    }
  });
  pagina.on('dialog', async (d) => { caixa.dialogos.push(`${d.type()}: ${d.message().slice(0, 80)}`); await d.dismiss(); });
}

const caixaNova = () => ({ console: [], js: [], rede: [], http: [], dialogos: [] });
const unicos = (l) => [...new Set(l)];

/** Hosts de terceiros: falha neles e problema do mundo, nao do projeto. */
const EXTERNO = /translate\.google|www\.google\.com|fonts\.googleapis|fonts\.gstatic|flagcdn|jsdelivr|unpkg|tile\.openstreetmap|nominatim|viacep|ip-api/i;

/**
 * Abre uma URL sem depender de `networkidle`.
 *
 * PROPOSITO DE NEGOCIO: paginas que fazem polling (o dashboard da telemetria
 * consulta a cada segundo) e paginas que chamam servico externo lento NUNCA
 * atingem `networkidle`. Esperar por ele fazia a varredura declarar "NAO
 * CARREGOU" em /telemetria e /sobre — que carregam perfeitamente. Instrumento
 * que reprova pagina sadia queima a confianca na lista inteira.
 *
 * INVARIANTE: a medicao so acontece depois do DOM pronto MAIS uma folga fixa
 * para o JS da pagina montar graficos e tabelas; sem a folga o detector mediria
 * a tela antes de ela existir.
 *
 * COMPORTAMENTO EM CASO DE FALHA: devolve `{ok:false, erro}` e o chamador
 * registra como achado — navegacao que falha de verdade continua sendo defeito.
 */
async function abrir(pagina, url, folga = 1200) {
  try {
    const r = await pagina.goto(url, { waitUntil: 'domcontentloaded', timeout: 25000 });
    await pagina.waitForLoadState('load', { timeout: 8000 }).catch(() => { /* recurso externo pendurado */ });
    await pagina.waitForTimeout(folga);
    return { ok: true, status: r ? r.status() : 'sem resposta' };
  } catch (e) {
    return { ok: false, erro: String(e).split('\n')[0].slice(0, 90) };
  }
}

// ===========================================================================
// EXECUCAO
// ===========================================================================

if (!existsSync(INVENTARIO)) {
  console.error('  NAO VERIFICADO: scripts/inventario.json ausente. Rode antes: node scripts/inventario.mjs');
  process.exit(2);
}
const INV = JSON.parse(readFileSync(INVENTARIO, 'utf8'));
const BASE = (BASE_CLI || process.env.BASE_URL || INV.base || 'http://127.0.0.1:8081').replace(/\/$/, '');
const LOCAL = /^https?:\/\/(127\.0\.0\.1|localhost|\[::1\])(:|\/|$)/i.test(BASE);

/*
 * Trava contra o engano de boa-fe: apontar a varredura para PRODUCAO.
 *
 * Nao e ataque, e o caminho natural — "deixa eu conferir se o site esta bom" com
 * a URL de producao colada. Duas coisas ruins acontecem sozinhas: a varredura
 * CLICA em cada gatilho e ENVIA cada formulario do site no ar (dezenas de POSTs
 * reais, telemetria poluida), e manda a ADMIN_API_KEY do `.env` local para um
 * host que nao e esta maquina. Base remota agora exige `--permitir-remoto`
 * escrito a mao, e mesmo assim a chave local nunca sai daqui.
 */
if (!LOCAL && !argumentos.includes('--permitir-remoto')) {
  console.error(`\n  RECUSANDO VARRER BASE REMOTA: ${BASE}`);
  console.error('  A varredura clica em cada gatilho e envia cada formulario — em producao');
  console.error('  isso vira trafego real e telemetria poluida.');
  console.error('\n  Se e mesmo o que voce quer, repita com --permitir-remoto.');
  console.error('  (A chave do .env local NAO e enviada para base remota em nenhum caso.)\n');
  process.exit(2);
}

/**
 * Uma passada completa de varredura.
 *
 * PROPOSITO DE NEGOCIO: e a unidade de medida. No modo normal roda uma vez; no
 * modo `--vigiar` roda em ciclo e so mostra o que apareceu de novo, que e o que
 * transforma a varredura em acompanhamento em tempo real de uma sessao de
 * desenvolvimento.
 *
 * INVARIANTES: a calibragem roda ANTES de qualquer medicao valer; o navegador e
 * fechado mesmo quando ha excecao no meio (senao sobra Chromium orfao a cada
 * ciclo do modo vigiar).
 *
 * FALHA: devolve `{ estado: 'nao-verificado' }` quando a aplicacao nao responde
 * ou algum detector esta cego. Nunca devolve "limpo" sem ter medido.
 *
 * @returns {Promise<{estado:string, achados:string[]}>}
 */
async function varrer() {
  const navegador = await chromium.launch();
  const achados = [];
  const suspeitas = [];
  const pulados = [];
  try {
    const contexto = await navegador.newContext({ ignoreHTTPSErrors: true });
    const pagina = await criarPagina(contexto, TELAS[0]);
    const caixa = caixaNova();
    escutar(pagina, caixa);

    // --- porta de entrada: a aplicacao esta no ar? ------------------------
    try {
      const r = await pagina.goto(BASE + '/', { waitUntil: 'domcontentloaded', timeout: 20000 });
      if (!r || r.status() >= 400) { throw new Error(`HTTP ${r ? r.status() : 'sem resposta'}`); }
    } catch (e) {
      log(`  NAO VERIFICADO: ${BASE} nao respondeu (${String(e).slice(0, 80)}).`);
      log('  Suba a aplicacao com .\\scripts\\subir.ps1 e rode de novo.');
      return { estado: 'nao-verificado', achados: [] };
    }

    // --- 0. CALIBRAGEM ----------------------------------------------------
    log(`\n${'='.repeat(70)}\n  0. CALIBRAGEM DOS DETECTORES\n${'='.repeat(70)}`);
    await abrir(pagina, BASE + '/portas');
    const base = await pagina.evaluate(DETECTORES);
    const conta = (lista, marca) => lista.filter((x) => x.t.includes(marca)).length;
    log(`  base da pagina de controle (/portas): ${base.length} achado(s)`);

    /**
     * Injeta um defeito, exige que o detector o acuse, e desfaz.
     * O criterio e DELTA, nao presenca: se o detector ja acusa na base (defeito
     * real na pagina), exigir "aparecer do nada" reprovaria a calibragem por
     * causa de um bug verdadeiro.
     */
    const calibrar = async (rotulo, marca, injetar, arg) => {
      await pagina.evaluate(injetar, arg);
      await pagina.waitForTimeout(250);
      const comDefeito = await pagina.evaluate(DETECTORES);
      await pagina.evaluate(() => {
        document.querySelectorAll('#cal-css,#cal-el').forEach((e) => e.remove());
        document.querySelectorAll('[data-cal]').forEach((e) => { e.removeAttribute('style'); e.removeAttribute('data-cal'); });
      });
      await pagina.waitForTimeout(150);
      const antes = conta(base, marca);
      const depois = conta(comDefeito, marca);
      const ok = depois > antes;
      log(`   ${ok ? 'ok  ' : 'CEGO'}  ${rotulo.padEnd(30)} ${antes} → ${depois}`);
      return ok;
    };

    const injetarCss = (texto) => {
      const s = document.createElement('style');
      s.id = 'cal-css';
      s.textContent = texto;
      document.head.appendChild(s);
    };
    const calibragens = [
      await calibrar('texto cortado', 'CORTADO', injetarCss,
        '.aed-topnav .aed-nav-link span:last-child{display:inline-block !important;width:14px !important;overflow:hidden !important;white-space:nowrap !important}'),
      await calibrar('controle fora da janela', 'fora da janela', injetarCss,
        '.aed-brand{position:relative !important;left:5000px !important}'),
      await calibrar('controle coberto', 'COBERTO', () => {
        const d = document.createElement('div');
        d.id = 'cal-el';
        d.style.cssText = 'position:fixed;inset:0;z-index:99999;background:rgba(0,0,0,.01)';
        document.body.appendChild(d);
      }),
      await calibrar('texto ilegivel', 'ilegivel', injetarCss,
        '.aed-nav-link span{color:#0a0d16 !important;background:#0a0d16 !important}'),
      await calibrar('modal sem fundo', 'modal', () => {
        const d = document.createElement('div');
        d.id = 'cal-el';
        d.className = 'modal';
        d.style.cssText = 'display:block;position:fixed;left:20%;top:20%;width:420px;height:220px;background:transparent';
        d.innerHTML = '<div class="modal-content" style="background:transparent;width:100%;height:100%">cal</div>';
        document.body.appendChild(d);
      }),
      await calibrar('overflow horizontal', 'rola na horizontal', () => {
        const d = document.createElement('div');
        d.id = 'cal-el';
        d.style.cssText = 'width:120vw;height:8px;background:#f00';
        document.body.appendChild(d);
      }),
      await calibrar('imagem quebrada', 'IMAGEM quebrada', () => {
        const i = document.createElement('img');
        i.id = 'cal-el';
        i.src = '/nao-existe-imagem-de-calibragem.png';
        i.style.cssText = 'width:40px;height:40px';
        document.body.appendChild(i);
      }),
      await calibrar('template nao renderizado', 'TEMPLATE nao', () => {
        const d = document.createElement('div');
        d.id = 'cal-el';
        d.textContent = '{item.naoResolvido}';
        d.style.cssText = 'font-size:16px;padding:4px';
        document.body.appendChild(d);
      }),
      await calibrar('icone virou texto', 'ICONE virou texto', () => {
        const s = document.createElement('span');
        s.id = 'cal-el';
        s.className = 'material-symbols-outlined';
        s.textContent = 'nome_de_icone_que_nao_existe';
        s.style.cssText = 'font-size:24px';
        document.body.appendChild(s);
      }),
    ];
    // Decimo detector: o de BOTAO MORTO. Os nove acima provam que a varredura
    // enxerga defeito visual; este prova que ela ainda distingue clique que faz
    // algo de clique que nao faz nada. E o unico calibrado ao contrario: o
    // acerto e a assinatura NAO mudar. Sem ele, a assinatura ficar barulhenta
    // (um tooltip com `.show` basta) aprovaria qualquer botao morto em silencio.
    const calibragemBotaoMorto = await (async () => {
      await pagina.evaluate(() => {
        const b = document.createElement('button');
        b.id = 'cal-morto';
        b.type = 'button';
        b.textContent = 'botao de calibragem';
        b.style.cssText = 'position:fixed;left:8px;bottom:8px;z-index:2147483647;padding:6px';
        document.body.appendChild(b);
      });
      await limparBaloes(pagina);
      const antes = await pagina.evaluate(ASSINATURA);
      await pagina.click('#cal-morto', { timeout: 4000 }).catch(() => {});
      await pagina.waitForTimeout(600);
      await limparBaloes(pagina);
      const depois = await pagina.evaluate(ASSINATURA);
      await pagina.evaluate(() => { const e = document.getElementById('cal-morto'); if (e) { e.remove(); } });
      const ok = antes === depois;
      log(`   ${ok ? 'ok  ' : 'CEGO'}  ${'botao morto (assinatura estavel)'.padEnd(30)} ${ok ? 'sem mudanca' : 'assinatura mudou sozinha'}`);
      return ok;
    })();
    calibragens.push(calibragemBotaoMorto);
    // Decimo primeiro: a fronteira do <details>. O MESMO conteudo largo tem de ficar
    // calado com o details fechado (ninguem o ve) e tem de ser acusado com ele aberto.
    // So o primeiro lado prova que o detector nao inventa; so o segundo, que nao ficou
    // cego para o que a pessoa ve ao clicar no resumo.
    const calibragemDetails = await (async () => {
      const marcas = ['fora da janela', 'rola na horizontal'];
      const soma = (lista) => marcas.reduce((n, m) => n + conta(lista, m), 0);
      await pagina.evaluate(() => {
        const d = document.createElement('details');
        d.id = 'cal-el';
        d.innerHTML = '<summary>calibragem</summary><div style="width:130vw;height:8px"></div>'
          + '<button type="button" style="position:relative;left:140vw">fora</button>';
        document.body.prepend(d);
      });
      await pagina.waitForTimeout(150);
      const fechado = await pagina.evaluate(DETECTORES);
      await pagina.evaluate(() => { document.getElementById('cal-el').open = true; });
      await pagina.waitForTimeout(150);
      const aberto = await pagina.evaluate(DETECTORES);
      await pagina.evaluate(() => { const e = document.getElementById('cal-el'); if (e) { e.remove(); } });
      const ok = soma(fechado) === soma(base) && soma(aberto) > soma(base);
      log(`   ${ok ? 'ok  ' : 'CEGO'}  ${'details fechado / aberto'.padEnd(30)} fechado ${soma(base)} → ${soma(fechado)} · aberto → ${soma(aberto)}`);
      return ok;
    })();
    calibragens.push(calibragemDetails);

    if (calibragens.some((x) => !x)) {
      log('\n  NAO VERIFICADO: ha detector CEGO. Nada abaixo provaria ausencia de defeito.');
      return { estado: 'nao-verificado', achados: [] };
    }
    log('  todos os detectores foram vistos acusando defeito injetado.');
    // A calibragem SUJA a caixa de erros: a imagem quebrada injetada gera um 404
    // de verdade. Sem esta limpeza o relatorio acusava o proprio instrumento —
    // "404 /nao-existe-imagem-de-calibragem.png" listado como defeito do site.
    Object.keys(caixa).forEach((k) => { caixa[k].length = 0; });

    // --- sessao autenticada para as telas protegidas ----------------------
    // Entra pela MESMA tela que uma pessoa usaria (formulario de contingencia).
    // Autenticar por header seria mais simples e esconderia exatamente o defeito
    // que esta varredura encontrou em 2026-08-11: o formulario voltava 403 de
    // CSRF. O caminho do usuario e o caminho que precisa ser medido.
    // Chaves a tentar, em ordem. Em base LOCAL tambem tentamos a chave publica de
    // desenvolvimento (dev-admin-key-local, literal no application.properties do perfil
    // dev) — assim a varredura autentica tanto no Docker/subir.ps1 (perfil prod, chave do
    // .env) quanto no quarkusDev (perfil dev, chave fixa), sem os dois se confundirem.
    const local = /localhost|127\.0\.0\.1/.test(BASE);
    const chaves = [];
    // Em localhost, a chave publica do perfil dev vem primeiro: no quarkusDev ela
    // autentica de primeira e nem chega a gerar o 401 da tentativa seguinte.
    if (local) { chaves.push('dev-admin-key-local'); }
    const chaveEnv = lerChaveLocal();
    if (chaveEnv) { chaves.push(chaveEnv); }
    if (chaves.length === 0) {
      log('\n  sem chave para /telemetria — sera medida como visitante deslogado');
    } else {
      let dentro = false;
      let ultimoMotivo = '';
      for (const chave of chaves) {
        try {
          await abrir(pagina, BASE + '/login/?modo=contingencia');
          await pagina.fill('#adminKey', chave);
          await pagina.click('form[action="/login/chave"] button[type=submit]');
          await pagina.waitForLoadState('load', { timeout: 15000 }).catch(() => { /* recurso externo pendurado */ });
          await pagina.waitForTimeout(800);
          dentro = !/\/login/.test(pagina.url());
          if (dentro) { break; }
          ultimoMotivo = (await pagina.evaluate(() => document.body.innerText)).replace(/\s+/g, ' ').slice(0, 100);
        } catch (e) {
          ultimoMotivo = String(e).split('\n')[0].slice(0, 100);
        }
      }
      if (dentro) {
        log('\n  login de contingencia: ok (telas protegidas serao medidas por dentro)');
      } else {
        achados.push(`LOGIN DE CONTINGENCIA NAO FUNCIONA: nenhuma chave autenticou — "${ultimoMotivo}"`);
        log('\n  login de contingencia: FALHOU — vira achado, e /telemetria sera medida deslogada');
      }
    }

    const todos = [...INV.paginas, ...INV.variantes];
    const alvos = ROTAS ? todos.filter((r) => ROTAS.includes(r.split('?')[0])) : todos;
    if (ROTAS) {
      const fora = ROTAS.filter((r) => !todos.some((t) => t.split('?')[0] === r));
      if (fora.length || alvos.length === 0) {
        log(`  NAO VERIFICADO: --rotas fora do inventario: ${fora.join(', ') || '(nenhuma rota casou)'}`);
        return { estado: 'nao-verificado', achados: [] };
      }
    }

    // --- A. PAGINAS, nas duas telas ---------------------------------------
    log(`\n${'='.repeat(70)}\n  A. PAGINAS (${alvos.length} alvos × ${TELAS.length} telas)\n${'='.repeat(70)}`);
    for (const tela of TELAS) {
      await pagina.setViewportSize({ width: tela.width, height: tela.height });
      for (const rota of alvos) {
        const antes = caixa.http.length;
        const r = await abrir(pagina, BASE + rota);
        if (!r.ok) {
          achados.push(`[${tela.nome}] ${rota}  NAO CARREGOU: ${r.erro}`);
          continue;
        }
        const status = r.status;
        if (typeof status === 'number' && status >= 400) {
          achados.push(`[${tela.nome}] ${rota}  HTTP ${status}`);
        }
        await pagina.waitForTimeout(400);
        const defeitos = agrupar(await pagina.evaluate(DETECTORES));
        // Teto por pagina: sem ele, uma cor errada num token do design system
        // gera centenas de linhas e enterra os outros achados. O que ficou de
        // fora e DITO — corte silencioso se le como "cobri tudo".
        defeitos.slice(0, TETO_POR_PAGINA)
          .forEach((d) => achados.push(`[${tela.nome}] ${rota}  [${d.t}] ${d.s}${d.n > 1 ? ` ×${d.n}` : ''}  ${d.d}`));
        if (defeitos.length > TETO_POR_PAGINA) {
          achados.push(`[${tela.nome}] ${rota}  (+${defeitos.length - TETO_POR_PAGINA} achado(s) do mesmo lote nao listados — teto de ${TETO_POR_PAGINA} por pagina)`);
        }
        // Segundo estado da pagina: os <details> abertos. Em repouso o conteudo deles
        // nao conta (ver emDetailsFechado), e foi ali que a saida simulada de
        // /ferramentas/rede estourava o cartao e rolava a pagina em 296 px — a pessoa
        // so via ao clicar no resumo, e a varredura nunca clicava. So entra o que
        // NAO apareceu em repouso, para nao repetir achado.
        const abertos = await pagina.evaluate(() => {
          const ds = [...document.querySelectorAll('details:not([open])')];
          ds.forEach((d) => { d.open = true; });
          return ds.length;
        });
        if (abertos > 0) {
          await pagina.waitForTimeout(250);
          const vistos = new Set(defeitos.map((d) => `${d.t}|${d.s}`));
          agrupar(await pagina.evaluate(DETECTORES))
            .filter((d) => !vistos.has(`${d.t}|${d.s}`))
            .slice(0, TETO_POR_PAGINA)
            .forEach((d) => achados.push(`[${tela.nome}] ${rota}  (${abertos} details abertos) [${d.t}] ${d.s}${d.n > 1 ? ` ×${d.n}` : ''}  ${d.d}`));
        }
        caixa.http.slice(antes).forEach((h) => achados.push(`[${tela.nome}] ${rota}  requisicao ${h}`));

        // Superficie prometida pelo template × superficie renderizada.
        // Elemento dentro de `{#if}`/`{#for}` nao e cobrado: ausencia ali e
        // comportamento correto (nao ha resultado ainda, o modo e outro). So o
        // que o template promete INCONDICIONALMENTE tem de estar na tela.
        const esperado = INV.superficie[rota.split('?')[0]];
        if (esperado && tela.nome === 'desktop') {
          const faltando = await pagina.evaluate((esp) => {
            const some = [];
            const ver = (lista, rotulo) => lista
              .filter((x) => !x.condicional)
              .forEach((x) => { if (!document.querySelector(x.seletor)) { some.push(`${rotulo} ${x.seletor}`); } });
            esp.abas.forEach((a) => { if (!document.querySelector(`[data-tab="${a}"]`)) { some.push(`aba ${a}`); } });
            ver(esp.campos, 'campo');
            ver(esp.gatilhos, 'gatilho');
            return some;
          }, esperado);
          faltando.forEach((f) => achados.push(`[template] ${rota}  declarado e NAO renderizado: ${f}`));
        }
      }
    }
    await pagina.setViewportSize({ width: TELAS[0].width, height: TELAS[0].height });

    if (!SO_PAGINAS) {
      // --- B. ABAS --------------------------------------------------------
      const comAbas = Object.entries(INV.superficie).filter(([, s]) => s.abas.length);
      log(`\n${'='.repeat(70)}\n  B. ABAS (${comAbas.reduce((n, [, s]) => n + s.abas.length, 0)} em ${comAbas.length} paginas)\n${'='.repeat(70)}`);
      for (const [rota, sup] of comAbas) {
        await abrir(pagina, BASE + rota);
        for (const aba of sup.abas) {
          const sel = `[data-tab="${aba}"]`;
          const antes = await pagina.evaluate(ASSINATURA);
          try {
            await limparBaloes(pagina);
            await pagina.click(sel, { timeout: 4000 });
          } catch {
            achados.push(`${rota}  aba "${aba}" nao pode ser clicada (${sel})`);
            continue;
          }
          await pagina.waitForTimeout(500);
          await limparBaloes(pagina);
          const depois = await pagina.evaluate(ASSINATURA);
          const painel = await pagina.evaluate((a) => {
            const p = document.querySelector(`[data-tab-panel="${a}"]`);
            return p ? p.classList.contains('active') : null;
          }, aba);
          if (antes === depois) { achados.push(`${rota}  aba "${aba}" NAO mudou nada na tela`); }
          if (painel === false) { achados.push(`${rota}  aba "${aba}" clicada mas o painel nao ficou ativo`); }
          if (painel === null) { achados.push(`${rota}  aba "${aba}" sem painel [data-tab-panel] correspondente`); }
          agrupar(await pagina.evaluate(DETECTORES)).slice(0, 4)
            .forEach((d) => achados.push(`${rota}#${aba}  [${d.t}] ${d.s}  ${d.d}`));
        }
      }

      // --- C. GATILHOS ----------------------------------------------------
      const total = Object.values(INV.superficie).reduce((n, s) => n + s.gatilhos.length, 0);
      log(`\n${'='.repeat(70)}\n  C. GATILHOS (${total} controles)\n${'='.repeat(70)}`);
      for (const [rota, sup] of Object.entries(INV.superficie)) {
        if (!sup.gatilhos.length) { continue; }
        for (const gat of sup.gatilhos) {
          // Aba nunca baixa arquivo nem imprime: a lista de excecoes se aplica ao
          // seletor e ao alvo htmx, e ao rotulo so quando o gatilho NAO e aba —
          // senao a aba "Privacidade" era pulada por o titulo dela citar GPS.
          const ehAba = /\[data-tab=/.test(gat.seletor);
          const assunto = ehAba ? `${gat.seletor} ${gat.htmx || ''}` : `${gat.seletor} ${gat.rotulo} ${gat.htmx || ''}`;
          const motivo = NAO_CLICAR.find(([re]) => re.test(assunto));
          if (motivo) { pulados.push(`${rota}  ${gat.seletor} — ${motivo[1]}`); continue; }
          await abrir(pagina, BASE + rota);
          const estado = await pagina.evaluate((sel) => {
            // O inventário vem dos templates: id montado pelo Qute ("#aba-{nivel.id}") não é seletor
            // válido e derrubava a varredura inteira. Vira "não coberto", com o motivo.
            let el;
            try { el = document.querySelector(sel); } catch (e) { return 'seletor de template (id montado pelo Qute)'; }
            if (!el) { return 'ausente'; }
            if (el.disabled || el.getAttribute('aria-disabled') === 'true') { return 'desabilitado'; }
            const r = el.getBoundingClientRect();
            const cs = getComputedStyle(el);
            if (r.width < 2 || r.height < 2 || cs.display === 'none' || cs.visibility === 'hidden') { return 'oculto'; }
            return 'clicavel';
          }, gat.seletor);
          if (estado === 'ausente') { continue; }
          // Desabilitado e oculto NAO sao defeito: o botao de pagina anterior
          // nasce desligado na pagina 1, e metade dos controles mora em aba
          // inativa. Sao registrados como nao cobertos, que e a verdade.
          if (estado !== 'clicavel') {
            pulados.push(`${rota}  ${gat.seletor} — ${estado} no estado inicial da pagina`);
            continue;
          }
          const antes = await pagina.evaluate(ASSINATURA);
          try {
            await limparBaloes(pagina);
            await pagina.click(gat.seletor, { timeout: 4000 });
          } catch (e) {
            achados.push(`${rota}  gatilho ${gat.seletor} nao clicavel: ${String(e).split('\n')[0].slice(0, 60)}`);
            continue;
          }
          await pagina.waitForTimeout(900);
          // O tooltip que o Playwright faz aparecer ao mover o mouse ganha a
          // classe `.show` e entraria na assinatura, fazendo TODO clique parecer
          // ter efeito — o detector de botao morto ficaria cego sem este passo.
          await limparBaloes(pagina);
          const depois = await pagina.evaluate(ASSINATURA);
          if (antes === depois) {
            // NAO COMPROVADO, e nao "defeito". O botao Limpar da Resolucao nao
            // muda nada num formulario ja vazio — e esta certo. Verificado a mao
            // em 2026-08-11: com dado preenchido ele limpa. Chamar isso de
            // defeito ensinaria a ignorar a lista inteira.
            suspeitas.push(`${rota}  ${gat.seletor} ${gat.rotulo ? `"${gat.rotulo.slice(0, 44)}"` : ''} — sem efeito visivel partindo do estado inicial (pode depender de dado preenchido)`);
          }
          agrupar(await pagina.evaluate(DETECTORES)).slice(0, 3)
            .forEach((d) => achados.push(`${rota} apos ${gat.seletor}  [${d.t}] ${d.s}  ${d.d}`));
        }
      }

      // --- D. FORMULARIOS -------------------------------------------------
      const comCampos = Object.entries(INV.superficie).filter(([, s]) => s.campos.some((c) => c.tag !== 'select'));
      log(`\n${'='.repeat(70)}\n  D. FORMULARIOS (${comCampos.length} paginas)\n${'='.repeat(70)}`);
      for (const [rota, sup] of comCampos) {
        await abrir(pagina, BASE + rota);
        let preenchidos = 0;
        for (const campo of sup.campos) {
          try {
            const el = await pagina.$(campo.seletor);
            if (!el || !(await el.isVisible())) { continue; }
            if (campo.tag === 'select') { continue; }
            if (['checkbox', 'radio', 'file', 'range', 'color', 'submit', 'button'].includes(campo.type)) { continue; }
            await el.fill(valorPara(campo), { timeout: 3000 });
            preenchidos++;
          } catch { /* campo escondido em aba inativa: nao e defeito */ }
        }
        if (!preenchidos) { continue; }
        const antes = await pagina.evaluate(ASSINATURA);
        const enviou = await pagina.evaluate(() => {
          const b = [...document.querySelectorAll('button[type=submit],input[type=submit],button[hx-post],button[hx-get]')]
            .find((x) => x.offsetParent !== null);
          if (!b) { return false; }
          b.click();
          return true;
        });
        if (!enviou) { continue; }
        await pagina.waitForTimeout(2200);
        const depois = await pagina.evaluate(ASSINATURA);
        if (antes === depois) { achados.push(`${rota}  formulario preenchido (${preenchidos} campos) e o envio NAO mudou a tela`); }
        const erroNaTela = await pagina.evaluate(() => {
          const t = (document.body.innerText || '').toLowerCase();
          const marcas = ['erro interno', 'internal server error', 'stack trace', 'exception', 'nullpointer', 'não foi possível processar'];
          return marcas.find((m) => t.includes(m)) || null;
        });
        if (erroNaTela) { achados.push(`${rota}  envio de formulario produziu ERRO na tela: "${erroNaTela}"`); }
        agrupar(await pagina.evaluate(DETECTORES)).slice(0, 6)
          .forEach((d) => achados.push(`${rota} (resultado)  [${d.t}] ${d.s}  ${d.d}`));
      }

      // --- E. MODAIS ------------------------------------------------------
      const comModais = Object.entries(INV.superficie).filter(([, s]) => s.modais.length);
      log(`\n${'='.repeat(70)}\n  E. MODAIS (${comModais.reduce((n, [, s]) => n + s.modais.length, 0)})\n${'='.repeat(70)}`);
      for (const [rota, sup] of comModais) {
        await abrir(pagina, BASE + rota);
        for (const modal of sup.modais) {
          const medida = await pagina.evaluate((sel) => {
            const el = document.querySelector(sel);
            if (!el) { return 'ausente'; }
            const antes = el.getAttribute('style') || '';
            const antesClasse = el.className;
            el.classList.add('show');
            el.style.setProperty('display', 'block', 'important');
            const r = el.getBoundingClientRect();
            const conteudo = el.querySelector('.modal-content') || el;
            const cs = getComputedStyle(conteudo);
            const temFundo = !(cs.backgroundColor === 'rgba(0, 0, 0, 0)' || cs.backgroundColor === 'transparent')
              || (cs.backgroundImage && cs.backgroundImage !== 'none');
            const dentro = r.right > 8 && r.left < innerWidth - 8 && r.bottom > 8 && r.top < innerHeight - 8;
            if (antes) { el.setAttribute('style', antes); } else { el.removeAttribute('style'); }
            el.className = antesClasse;
            return { w: Math.round(r.width), h: Math.round(r.height), temFundo, dentro, z: cs.zIndex };
          }, modal);
          if (medida === 'ausente') { achados.push(`${rota}  modal ${modal} declarado no template e AUSENTE na pagina`); continue; }
          const prob = [];
          if (!medida.temFundo && medida.w > 60) { prob.push('SEM FUNDO (le-se o conteudo por tras)'); }
          if (!medida.dentro) { prob.push('fora da tela'); }
          if (medida.w < 120 || medida.h < 60) { prob.push(`minusculo ${medida.w}x${medida.h}`); }
          if (prob.length) { achados.push(`${rota}  modal ${modal}: ${prob.join(', ')}`); }
        }
      }

      // --- F. LINKS INTERNOS ----------------------------------------------
      const links = unicos(Object.values(INV.superficie).flatMap((s) => s.links))
        .filter((l) => !/[{}]/.test(l))
        .map((l) => l.replace(/&amp;/g, '&'))
        .filter((l) => !INV.mutantes.includes(l) && !INV.downloads.includes(l));
      log(`\n${'='.repeat(70)}\n  F. LINKS INTERNOS (${links.length})\n${'='.repeat(70)}`);
      for (const link of links) {
        try {
          const r = await pagina.request.get(BASE + link, { maxRedirects: 5, timeout: 15000 });
          if (r.status() >= 400) { achados.push(`link QUEBRADO ${link} → HTTP ${r.status()}`); }
        } catch (e) {
          achados.push(`link QUEBRADO ${link} → ${String(e).slice(0, 60)}`);
        }
      }

      // --- G. APIs GET ----------------------------------------------------
      const apisGet = INV.apis.filter((a) => a.metodo === 'GET' && !/[{}]/.test(a.caminho));
      log(`\n${'='.repeat(70)}\n  G. APIs GET (${apisGet.length})\n${'='.repeat(70)}`);
      for (const api of apisGet) {
        try {
          const r = await pagina.request.get(BASE + api.caminho, { timeout: 15000 });
          if (r.status() >= 500) { achados.push(`API ${api.caminho} → HTTP ${r.status()}  (${api.origem})`); }
        } catch (e) {
          achados.push(`API ${api.caminho} → ${String(e).slice(0, 60)}`);
        }
      }
    }

    // --- H. ERROS DO NAVEGADOR -------------------------------------------
    // CDN, fonte do Google, tradutor, tiles do OSM e ViaCEP caem, sao bloqueados
    // por captcha ou demoram — e nao e defeito DESTE projeto. Continuam no
    // relatorio (a pagina depende deles de verdade), mas em secao propria: pilha
    // de ruido externo misturada aos achados faz a lista inteira ser ignorada.
    const externo = [];
    const interno = (lista, rotulo) => unicos(lista).forEach((e) => {
      (EXTERNO.test(e) ? externo : achados).push(`${rotulo}: ${e}`);
    });
    interno(caixa.js, 'ERRO DE JAVASCRIPT');
    interno(caixa.console, 'console.error');
    interno(caixa.rede, 'requisicao falhou');
    interno(caixa.http, 'resposta HTTP de erro');
    unicos(caixa.dialogos).forEach((e) => achados.push(`dialogo nativo inesperado: ${e}`));

    if (externo.length) {
      log(`\n${'='.repeat(70)}\n  RUIDO EXTERNO (${externo.length}) — host de terceiro, nao e defeito do projeto\n${'='.repeat(70)}`);
      externo.forEach((e) => log(`   · ${e}`));
    }

    if (suspeitas.length) {
      log(`
${'='.repeat(70)}
  NAO COMPROVADO (${suspeitas.length}) — o instrumento nao consegue decidir
${'='.repeat(70)}`);
      unicos(suspeitas).forEach((s) => log(`   ? ${s}`));
    }
    if (pulados.length) {
      log(`\n${'='.repeat(70)}\n  PULADOS COM MOTIVO (${pulados.length}) — nao foram cobertos\n${'='.repeat(70)}`);
      unicos(pulados).forEach((p) => log(`   · ${p}`));
    }
    return { estado: 'medido', achados: unicos(achados), suspeitas: unicos(suspeitas), pulados: unicos(pulados) };
  } finally {
    await navegador.close();
  }
}

/** Agrupa achados iguais (mesmo tipo e mesmo seletor) para o relatorio nao virar muro. */
function agrupar(lista) {
  const mapa = new Map();
  for (const x of lista) {
    const k = `${x.t.replace(/[\d.]+:1/, 'N:1')}|${x.s}`;
    if (!mapa.has(k)) { mapa.set(k, { ...x, n: 0 }); }
    mapa.get(k).n++;
  }
  return [...mapa.values()];
}

/**
 * Le a ADMIN_API_KEY do .env local para entrar nas telas protegidas.
 *
 * PROPOSITO: /telemetria fica atras de login; sem sessao a varredura mediria a
 * tela de login N vezes e diria que o dashboard esta sadio sem nunca te-lo visto.
 * INVARIANTE: le apenas o .env LOCAL desta maquina; nunca recebe segredo por
 * linha de comando (ficaria no historico do shell) e nunca imprime o valor.
 * FALHA: sem arquivo ou sem a chave devolve null, e a varredura registra em
 * relatorio que aquelas telas foram medidas deslogadas.
 */
function lerChaveLocal() {
  // Segredo desta maquina nao viaja. Base remota mede as telas protegidas como
  // visitante deslogado — cobertura menor, e dita no relatorio.
  if (!LOCAL) { return null; }
  try {
    const linha = readFileSync(join(RAIZ, '.env'), 'utf8').match(/^ADMIN_API_KEY=(.+)$/m);
    return linha ? linha[1].trim() : null;
  } catch {
    return null;
  }
}

// --- laco principal --------------------------------------------------------
const cabecalho = () => {
  writeFileSync(RELATORIO, '');
  log(`  VARREDURA — Framework de Redes`);
  log(`  base ${BASE}  ·  ${INV.paginas.length} paginas · ${INV.variantes.length} variantes · ${INV.apis.length} APIs`);
  log(`  inventario: scripts/inventario.json  ·  relatorio: scripts/relatorio-varredura.txt`);
};

if (!VIGIAR) {
  cabecalho();
  const { estado, achados } = await varrer();
  log(`\n${'='.repeat(70)}`);
  if (estado === 'nao-verificado') {
    log('  VEREDITO: NAO VERIFICADO (codigo 2) — a ausencia de achado aqui nao e aprovacao.');
    process.exit(2);
  }
  if (achados.length === 0) {
    log('  VEREDITO: nenhum defeito encontrado NO ESCOPO VARRIDO (codigo 0).');
    process.exit(0);
  }
  log(`  ${achados.length} ACHADO(S)\n${'='.repeat(70)}`);
  achados.forEach((a) => log(`   · ${a}`));
  log(`\n  VEREDITO: ${achados.length} achado(s) (codigo 1).`);
  process.exit(1);
} else {
  // Modo tempo real: repete a varredura e mostra so o que apareceu de novo.
  const jaVistos = new Set();
  let ciclo = 0;
  for (;;) {
    ciclo++;
    cabecalho();
    log(`\n  ciclo ${ciclo} — ${new Date().toISOString().replace('T', ' ').slice(0, 19)}`);
    const { estado, achados } = await varrer();
    if (estado === 'nao-verificado') {
      log('  NAO VERIFICADO neste ciclo — nova tentativa no proximo.');
    } else {
      const novos = achados.filter((a) => !jaVistos.has(a));
      const sumidos = [...jaVistos].filter((a) => !achados.includes(a));
      achados.forEach((a) => jaVistos.add(a));
      sumidos.forEach((a) => jaVistos.delete(a));
      log(`\n  ciclo ${ciclo}: ${achados.length} achado(s) · ${novos.length} novo(s) · ${sumidos.length} resolvido(s)`);
      novos.forEach((a) => log(`   + NOVO      ${a}`));
      sumidos.forEach((a) => log(`   - RESOLVIDO ${a}`));
    }
    await new Promise((r) => setTimeout(r, INTERVALO * 1000));
  }
}
