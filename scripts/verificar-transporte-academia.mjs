// Verificação no Chromium do nível Transporte (A6: a prova alcança a tela). 0 passou, 1 reprovou, 2 não verificou.
// O esperado das perguntas é calculado AQUI, pela definição do protocolo, a partir do texto da pergunta (A3).
import { chromium } from 'playwright';

const BASE = process.argv[2] || 'http://localhost:8095';
const falhas = [];
const ok = [];
const confere = (c, d) => (c ? ok : falhas).push(d);

let browser;
try {
  browser = await chromium.launch();
} catch (e) {
  console.log('NAO VERIFICOU: chromium não abriu', e.message);
  process.exit(2);
}

// bloquear=true corta todo pedido fora do localhost (prova que a lição não depende de terceiro);
// o "Failed to load resource" desses cortes é do próprio instrumento e não conta — falha de pedido
// ao localhost conta. O estouro é medido SEM bloqueio: com o CDN do site cortado, o menu do site
// inteiro estoura (medido também em /laboratorios), o que não é defeito da Academia.
async function novaPagina(largura = 1400, bloquear = true) {
  const ctx = await browser.newContext({ viewport: { width: largura, height: 900 } });
  if (bloquear) {
    await ctx.route('**/*', (route) => (new URL(route.request().url()).hostname === 'localhost' ? route.continue() : route.abort()));
  }
  const page = await ctx.newPage();
  const erros = [];
  page.on('pageerror', (e) => erros.push('pageerror: ' + e.message));
  page.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) erros.push('console: ' + m.text()); });
  page.on('requestfailed', (r) => { if (new URL(r.url()).hostname === 'localhost') erros.push('pedido local falhou: ' + r.url()); });
  page.on('response', (r) => { if (new URL(r.url()).hostname === 'localhost' && r.status() >= 400) erros.push('local ' + r.status() + ': ' + r.url()); });
  return { ctx, page, erros };
}

const numeros = (t) => (t.match(/\d+/g) || []).map(Number);

async function responder(page, valor) {
  await page.fill('#provar-resposta', String(valor));
  await page.click('#provar-form button[type=submit]');
  return (await page.textContent('#provar-feedback')).trim();
}

try {
  // ---------------------------------------------------------------- landing e nível
  {
    const { ctx, page, erros } = await novaPagina();
    await page.goto(BASE + '/academia', { waitUntil: 'load' });
    // o nível está trancado para o aluno novo: o endereço da lição fica guardado em data-acad-href
    const links = await page.$$eval('#trilha a.acad-licao-link', (as) => as.map((a) => a.getAttribute('href') || a.getAttribute('data-acad-href')));
    confere(links.includes('/academia/transporte/aperto') && links.includes('/academia/transporte/janela'), 'landing lista as duas lições de Transporte');
    const emBreve = await page.$$eval('#trilha .acad-nivel.fechado h3', (hs) => hs.map((h) => h.textContent.trim()));
    confere(emBreve.length === 1 && emBreve[0] === 'Enlace', 'só Enlace segue em breve: ' + emBreve.join(','));
    const r = await page.goto(BASE + '/academia/transporte', { waitUntil: 'load' });
    confere(r.status() === 200, 'página do nível 200');
    confere((await page.$$('.acad-licao-link')).length === 2, 'nível com 2 lições');
    confere(erros.length === 0, 'landing/nível sem erro de console: ' + erros.join(' | '));
    await ctx.close();
  }

  // ---------------------------------------------------------------- aperto de mão
  {
    const { ctx, page, erros } = await novaPagina();
    await page.goto(BASE + '/academia/transporte/aperto', { waitUntil: 'load' });
    await page.waitForSelector('#ver-setas li');
    confere((await page.$$('#ver-setas li')).length === 5, 'Ver com 5 segmentos');
    confere((await page.$$('#ver-setas li.apagada')).length === 5, 'Ver começa com tudo apagado');
    for (let i = 0; i < 2; i++) {
      await page.click('#ver-animacao [data-acad-acao="passo"]');
    }
    confere((await page.$$('#ver-setas li.apagada')).length === 3, 'dois passos acendem dois segmentos');
    const legenda = await page.textContent('#ver-animacao [data-acad-legenda]');
    confere(legenda.includes('ack 101'), 'legenda do passo 2 fala do ack 101: ' + legenda.trim());
    const icone = await page.$eval('#ver-titulo .material-symbols-outlined', (el) => el.getBoundingClientRect().width);
    confere(icone > 0 && icone < 40, `ícone é glifo com o exterior bloqueado (${icone.toFixed(1)}px)`);

    const mexer = await page.$$eval('#mexer-setas li .acad-seta-campos', (els) => els.map((e) => e.textContent));
    confere(mexer.length === 7, 'Mexer padrão: 7 segmentos');
    confere(mexer[4] === 'seq 301 · ack 151' && mexer[6] === 'seq 151 · ack 321', 'Mexer padrão: ack 151 e 321 ' + JSON.stringify(mexer));
    await page.fill('#mexer-isn-cliente', '4294967295');
    const depois = await page.$$eval('#mexer-setas li .acad-seta-campos', (els) => els.map((e) => e.textContent));
    confere(depois[1] === 'seq 300 · ack 0', 'ISN 4294967295: SYN-ACK confirma 0 — ' + depois[1]);
    confere((await page.textContent('#mexer-nota')).includes('deu a volta'), 'Mexer avisa que a sequência deu a volta');
    await page.fill('#mexer-isn-cliente', '4294967296');
    confere((await page.textContent('#mexer-isn-cliente-eco')).includes('4294967295'), 'valor acima do teto é recusado com o motivo');
    confere((await page.$$eval('#mexer-setas li .acad-seta-campos', (els) => els[1].textContent)) === 'seq 300 · ack 0', 'recusa mantém o último diagrama válido');

    // Provar: o esperado sai da definição (ISN de quem enviou + 1 + bytes), não do código da página
    let acertos = 0;
    let errouComDica = false;
    for (let rodada = 0; rodada < 6 && acertos < 3; rodada++) {
      const pergunta = (await page.textContent('#provar-pergunta')).trim();
      const n = numeros(pergunta);
      let esperado;
      if (pergunta.includes('fecha o aperto')) {
        esperado = n[1] + 1;                 // ack do ACK final = ISN do servidor + 1
      } else if (pergunta.includes('bytes de dados')) {
        esperado = n[0] + 1 + n[1];          // ISN do cliente + 1 + bytes
      } else {
        esperado = n[0] + 1;                 // SYN-ACK e seq do primeiro byte
      }
      if (!errouComDica) {
        const fb = await responder(page, esperado - 1);
        confere(fb.includes('Faltou um'), `resposta com um a menos recebe "Faltou um" (${pergunta} → ${fb})`);
        errouComDica = true;
      }
      const fb = await responder(page, esperado);
      confere(fb.startsWith('Certo'), `resposta certa aceita: ${pergunta} = ${esperado} → ${fb}`);
      acertos++;
    }
    confere((await page.textContent('#provar-placar')).includes('lição concluída'), 'três acertos concluem a lição');
    confere((await page.textContent('.acad-licao-cabeca [data-acad-selo]')).length > 0, 'selo pintado');
    confere(erros.length === 0, 'aperto sem erro de console: ' + erros.join(' | '));
    await ctx.close();
  }

  // ---------------------------------------------------------------- janela
  {
    const { ctx, page, erros } = await novaPagina();
    await page.goto(BASE + '/academia/transporte/janela', { waitUntil: 'load' });
    await page.waitForSelector('#ver-segmentos li');
    confere((await page.$$('#ver-segmentos li')).length === 6, 'Ver com 6 segmentos');
    for (let i = 0; i < 11; i++) {
      await page.click('#ver-animacao [data-acad-acao="passo"]');
    }
    confere((await page.textContent('#ver-total')).trim() === '10', 'Ver termina com 10 transmissões');
    const legenda = await page.textContent('#ver-animacao [data-acad-legenda]');
    confere(legenda.includes('10') && legenda.includes('7'), 'legenda final compara 10 com 7: ' + legenda.trim());
    confere((await page.$$('#ver-segmentos li.aceito')).length === 6, 'no fim os 6 estão aceitos');

    confere((await page.textContent('#mexer-total-gbn')).trim() === '10', 'Mexer padrão: Go-Back-N 10');
    confere((await page.textContent('#mexer-total-seletiva')).trim() === '7', 'Mexer padrão: seletiva 7');
    confere((await page.$$('#mexer-registro li')).length === 11, 'registro com 11 linhas (10 envios + 1 estouro)');
    await page.fill('#mexer-perdido', '');
    confere((await page.textContent('#mexer-total-gbn')).trim() === '6' && (await page.textContent('#mexer-total-seletiva')).trim() === '6', 'sem perda: 6 e 6');
    await page.fill('#mexer-perdido', '9');
    confere((await page.textContent('#mexer-aviso')).includes('entre 0 e 5'), 'perda fora dos segmentos é recusada com o motivo');
    await page.fill('#mexer-segmentos', '10');
    await page.fill('#mexer-janela', '3');
    await page.fill('#mexer-perdido', '8');
    confere((await page.textContent('#mexer-total-gbn')).trim() === '12', 'N=10 W=3 perda 8: Go-Back-N 12');
    await page.selectOption('#mexer-modo', 'seletiva');
    confere((await page.$$('#mexer-registro li.guardado')).length === 1, 'seletiva guarda o 9 que chegou fora de ordem');

    let acertos = 0;
    let dicaVista = false;
    for (let rodada = 0; rodada < 8 && acertos < 3; rodada++) {
      const pergunta = (await page.textContent('#provar-pergunta')).trim();
      const n = numeros(pergunta);
      let esperado;
      if (pergunta.includes('A janela é de')) {
        esperado = n[1] + n[0] - 1;                          // base + W - 1
      } else {
        const [total, , , w, k] = n;                         // "São N (do 0 ao N-1), janela W, segmento k"
        esperado = pergunta.includes('Go-Back-N') ? total + 1 + Math.min(w - 1, total - 1 - k) : total + 1;
        if (!dicaVista && pergunta.includes('Go-Back-N') && total - 1 - k > 0) {
          const fb = await responder(page, total + 1);
          confere(fb.includes('repetição seletiva'), `Go-Back-N respondido como seletiva recebe a dica certa: ${fb}`);
          dicaVista = true;
        }
      }
      const fb = await responder(page, esperado);
      confere(fb.startsWith('Certo'), `resposta certa aceita: ${pergunta} = ${esperado} → ${fb}`);
      acertos++;
    }
    confere(acertos === 3 && (await page.textContent('#provar-placar')).includes('lição concluída'), 'três acertos concluem a lição');
    confere(erros.length === 0, 'janela sem erro de console: ' + erros.join(' | '));
    await ctx.close();
  }

  // ---------------------------------------------------------------- celular: sem estouro
  for (const rota of ['/academia/transporte/aperto', '/academia/transporte/janela']) {
    for (const largura of [390, 320]) {
      const { ctx, page } = await novaPagina(largura, false);
      await page.goto(BASE + rota, { waitUntil: 'load' });
      const sobra = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      confere(sobra <= 0, `${rota} em ${largura}px sem estouro (sobra ${sobra})`);
      await ctx.close();
    }
  }
} catch (e) {
  falhas.push('exceção: ' + e.message);
} finally {
  await browser.close();
}

ok.forEach((d) => console.log('ok   ' + d));
falhas.forEach((d) => console.log('FALHA ' + d));
console.log(`\n${ok.length} ok, ${falhas.length} falhas`);
process.exit(ok.length === 0 ? 2 : falhas.length ? 1 : 0);
