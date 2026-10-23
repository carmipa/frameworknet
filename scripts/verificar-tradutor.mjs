/**
 * VERIFICAÇÃO DO TRADUTOR (Google Translate) NO NAVEGADOR REAL.
 *
 * PROPÓSITO DE NEGÓCIO: a tradução é o caminho de quem não lê português. Teste de servidor não prova
 * nada aqui: o element.js é do Google e a tradução acontece no navegador. Este roteiro prova, num
 * Chromium de verdade, que (1) a página TRADUZ e os ícones continuam ícones; (2) quando o Google não
 * entrega o script — limite de uso que o manda para o captcha, rede bloqueada ou silêncio — a pessoa
 * vê um aviso no idioma que escolheu, com caminhos de saída, em vez de uma bandeira que não faz nada;
 * (3) as bandeiras do seletor vêm do próprio site; (4) as páginas de erro do proxy têm o script.
 *
 * INVARIANTES DO DOMÍNIO:
 *  - só UMA carga toca o Google de verdade (a do item 1); todas as outras bloqueiam o Google —
 *    roteiro automatizado em volume é o que dispara o captcha para o endereço inteiro;
 *  - captcha ou Google fora do ar no item 1 é NÃO VERIFICOU (2), nunca aprovação nem reprovação;
 *  - o detector do aviso é calibrado contra a versão ANTERIOR do script (o commit fixo 36e8ddd, de
 *    antes do aviso existir, ou o arquivo passado em --calibrar=<caminho>): ela tem de reprovar, senão
 *    o detector não discrimina. Não é o HEAD: depois do commit do aviso, o HEAD já o tem.
 *
 * COMPORTAMENTO EM CASO DE FALHA: sai 0 (passou), 1 (reprovou) ou 2 (não verificou: Chromium não
 * abriu, servidor fora, Google em captcha no item 1, ou calibração impossível). Cada linha diz o que
 * mediu.
 *
 * USO: node scripts/verificar-tradutor.mjs [http://localhost:8081] [--calibrar=<js antigo>]
 */
import { chromium } from 'playwright';
import { readFileSync, existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const AQUI = dirname(fileURLToPath(import.meta.url));
const RAIZ = join(AQUI, '..');
const BASE = process.argv.slice(2).find((a) => a.startsWith('http')) || 'http://localhost:8081';
const CALIBRAR = (process.argv.slice(2).find((a) => a.startsWith('--calibrar=')) || '').split('=')[1];
const GOOGLE = /(^|\.)(translate\.google\.com|translate\.googleapis\.com|translate-pa\.googleapis\.com|www\.google\.com)$/;

const ok = [];
const falhas = [];
const naoVerificado = [];
const confere = (c, d) => (c ? ok : falhas).push(d);

let browser;
try {
  browser = await chromium.launch();
} catch (e) {
  console.log('NAO VERIFICOU: o Chromium não abriu —', e.message);
  process.exit(2);
}

async function contexto({ idioma = null, bloquearGoogle = true, elementJs = null, scriptAntigo = null } = {}) {
  const ctx = await browser.newContext();
  if (idioma) {
    await ctx.addCookies([{ name: 'googtrans', value: '/pt/' + idioma, url: BASE }]);
  }
  await ctx.route('**/*', async (route) => {
    const url = new URL(route.request().url());
    if (scriptAntigo && url.pathname === '/web/js/i18n-translate.js') {
      return route.fulfill({ status: 200, contentType: 'text/javascript', body: scriptAntigo });
    }
    if (elementJs && /translate_a\/element\.js/.test(url.href)) {
      return elementJs(route);
    }
    if (bloquearGoogle && GOOGLE.test(url.hostname)) {
      return route.abort();
    }
    return route.continue();
  });
  const page = await ctx.newPage();
  const console_ = [];
  page.on('console', (m) => console_.push(m.text()));
  page.on('pageerror', (e) => console_.push('pageerror: ' + e.message));
  return { ctx, page, console_ };
}

const avisoVisivel = (page) => page.evaluate(() => {
  const a = document.querySelector('.aed-tradutor-aviso');
  if (!a) { return null; }
  const r = a.getBoundingClientRect();
  return { texto: a.textContent, lang: a.getAttribute('lang'), visivel: r.width > 0 && r.height > 0,
    botoes: [...a.querySelectorAll('button, a')].map((b) => b.textContent || b.getAttribute('aria-label')) };
});

try {
  try {
    const r = await fetch(BASE + '/');
    if (!r.ok) { throw new Error('HTTP ' + r.status); }
  } catch (e) {
    console.log('NAO VERIFICOU: servidor fora em', BASE, '—', e.message);
    process.exit(2);
  }

  // 1. Tradução de verdade (única carga que toca o Google)
  {
    const { ctx, page, console_ } = await contexto({ idioma: 'en', bloquearGoogle: false });
    let captcha = false;
    page.on('response', (r) => { if (/google\.com\/sorry/.test(r.url()) || (r.status() >= 300 && /sorry/.test(r.headers().location || ''))) { captcha = true; } });
    const original = await (async () => {
      const c2 = await browser.newContext();
      await c2.route('**/*', (rt) => (GOOGLE.test(new URL(rt.request().url()).hostname) ? rt.abort() : rt.continue()));
      const p2 = await c2.newPage();
      await p2.goto(BASE + '/', { waitUntil: 'load' });
      const v = await p2.evaluate(() => ({
        icones: [...document.querySelectorAll('.material-symbols-outlined')].map((e) => e.textContent.trim()),
        menu: [...document.querySelectorAll('.aed-topnav .aed-nav-link > span:last-child')].map((e) => e.textContent.trim()),
        lang: document.documentElement.getAttribute('lang'),
      }));
      await c2.close();
      return v;
    })();
    await page.goto(BASE + '/', { waitUntil: 'load' });
    const traduziu = await page.waitForFunction(() => document.documentElement.classList.contains('translated-ltr'),
      null, { timeout: 20000 }).then(() => true).catch(() => false);
    if (!traduziu && captcha) {
      naoVerificado.push('1. tradução real: o Google respondeu com captcha (limite de uso do endereço) — sem veredito');
    } else {
      confere(traduziu, '1. a página traduz para inglês (html.translated-ltr)');
      await page.waitForTimeout(1500);
      const depois = await page.evaluate(() => ({
        icones: [...document.querySelectorAll('.material-symbols-outlined')].map((e) => e.textContent.trim()),
        menu: [...document.querySelectorAll('.aed-topnav .aed-nav-link > span:last-child')].map((e) => e.textContent.trim()),
        lang: document.documentElement.getAttribute('lang'),
      }));
      const mudou = depois.menu.filter((t, i) => t !== original.menu[i]);
      confere(mudou.length >= 3, `1. o texto do menu mudou de idioma (${mudou.slice(0, 4).join(' / ')})`);
      confere(JSON.stringify(depois.icones) === JSON.stringify(original.icones),
        `1. os ${original.icones.length} ícones continuam com o nome do glifo (nenhum traduzido)`);
      // O HTML servido declara o idioma REAL (regra de i18n §5b); depois de traduzir, quem troca o
      // lang para o de destino é o próprio Google — e isso é o correto para a página traduzida.
      confere(/^pt/i.test(original.lang || ''), `1. o HTML servido declara <html lang> em português (${original.lang})`);
      const csp = console_.filter((t) => /Content Security Policy/.test(t))
        .filter((t) => !(BASE.startsWith('http:') && /http:\/\/translate\.google\.com\/gen204/.test(t)));
      confere(csp.length === 0, '1. nenhuma violação de CSP durante a tradução' + (csp.length ? ': ' + csp[0].slice(0, 160) : ''));
      if (!avisoVisivel) { /* noop */ }
      confere(!(await avisoVisivel(page)), '1. sem aviso de falha quando a tradução funciona');
    }
    await ctx.close();
  }

  // 2. Google manda para o captcha (o CSP bloqueia o redirecionamento): aviso imediato no idioma pedido
  const captcha = (route) => route.fulfill({ status: 302, headers: { location: 'https://www.google.com/sorry/index?continue=x' } });
  {
    const { ctx, page } = await contexto({ idioma: 'en', elementJs: captcha });
    await page.goto(BASE + '/', { waitUntil: 'load' });
    await page.waitForTimeout(1500);
    const a = await avisoVisivel(page);
    confere(a && a.visivel, '2. captcha do Google: aviso visível em até 1,5 s');
    confere(a && a.lang === 'en' && /did not respond/.test(a.texto), '2. o aviso está em inglês, o idioma pedido: ' + (a ? a.texto.slice(0, 70) : '—'));
    confere(a && a.botoes.some((b) => /Try again/.test(b)) && a.botoes.some((b) => /Stay in Portuguese/.test(b)),
      '2. caminhos de saída: tentar de novo e ficar em português');
    confere(a && !a.botoes.some((b) => /Google Translate/.test(b || '')) === BASE.startsWith('http:'),
      '2. "abrir no Google Tradutor" só aparece em endereço público https (aqui: ' + (BASE.startsWith('http:') ? 'ausente' : 'presente') + ')');
    await page.click('.aed-tradutor-aviso button:has-text("Stay in Portuguese")');
    await page.waitForLoadState('load');
    await page.waitForTimeout(1500);
    const cookie = (await ctx.cookies()).find((c) => c.name === 'googtrans' && c.value);
    confere(!cookie && !(await avisoVisivel(page)), '2. "ficar em português" apaga a escolha e o aviso some');
    await ctx.close();
  }

  // 3. A tradução chega depois do aviso: o aviso sai, porque deixou de ser verdade
  {
    const { ctx, page } = await contexto({ idioma: 'es', elementJs: captcha });
    await page.goto(BASE + '/academia/', { waitUntil: 'load' });
    await page.waitForTimeout(1200);
    const antes = await avisoVisivel(page);
    await page.evaluate(() => document.documentElement.classList.add('translated-ltr'));
    await page.waitForTimeout(300);
    confere(antes && antes.lang === 'es' && !(await avisoVisivel(page)), '3. aviso em espanhol some quando a tradução chega atrasada');
    await ctx.close();
  }

  // 4. Sem idioma pedido: falha do Google não incomoda ninguém
  {
    const { ctx, page } = await contexto({ idioma: null, elementJs: captcha });
    await page.goto(BASE + '/', { waitUntil: 'load' });
    await page.waitForTimeout(1500);
    confere(!(await avisoVisivel(page)), '4. sem tradução pedida, nenhum aviso (português é o padrão)');
    const bandeiras = await page.$$eval('.aed-lang-btn img', (imgs) => imgs.map((i) => ({ src: i.getAttribute('src'), w: i.naturalWidth })));
    confere(bandeiras.length === 3 && bandeiras.every((b) => b.src.startsWith('/web/img/bandeiras/') && b.w > 0),
      '4. as 3 bandeiras do seletor vêm do próprio site e carregam: ' + bandeiras.map((b) => b.src.split('/').pop()).join(', '));
    await ctx.close();
  }

  // 5. Google que não responde (pedido pendurado): a página continua funcionando e o aviso vem pelo prazo
  {
    const { ctx, page } = await contexto({ idioma: 'en', elementJs: () => new Promise(() => {}) });
    const montou = await page.goto(BASE + '/calculadora', { waitUntil: 'domcontentloaded', timeout: 8000 })
      .then(() => true).catch(() => false);
    confere(montou, '5. Google mudo: o DOMContentLoaded acontece (o element.js não segura a página)');
    if (montou) {
      const abas = await page.$$('[data-calc-tab]');
      if (abas.length > 1) {
        const alvo = await abas[1].getAttribute('data-calc-tab');
        await abas[1].click();
        await page.waitForTimeout(400);
        const ativo = await page.evaluate((a) => {
          const painel = document.querySelector(`[data-calc-panel="${a}"]`);
          return Boolean(painel) && getComputedStyle(painel).display !== 'none';
        }, alvo);
        confere(ativo, `5. Google mudo: as abas da calculadora trocam (aba "${alvo}")`);
      } else {
        naoVerificado.push('5. não achei abas na /calculadora para provar que os scripts rodam');
      }
      await page.waitForTimeout(11000);
      const a = await avisoVisivel(page);
      confere(a && a.visivel, '5. Google mudo: aviso aparece pelo prazo (10 s)');
    }
    await ctx.close();
  }

  // 6. Páginas de erro do proxy: bandeiras embutidas e a função de troca existe
  for (const codigo of [502, 503, 504]) {
    const arquivo = join(RAIZ, 'scripts', 'erro-proxy', codigo + '.html');
    if (!existsSync(arquivo)) {
      naoVerificado.push(`6. ${codigo}.html não existe — rode a suíte para gerar`);
      continue;
    }
    const ctx = await browser.newContext();
    await ctx.route('**/*', (r) => (r.request().url().startsWith('file:') || r.request().url().startsWith('data:') ? r.continue() : r.abort()));
    const page = await ctx.newPage();
    await page.goto(pathToFileURL(arquivo).href, { waitUntil: 'load' });
    const r = await page.evaluate(() => ({
      funcao: typeof window.aedTranslate === 'function',
      bandeiras: [...document.querySelectorAll('.lang img')].map((i) => i.naturalWidth > 0 && i.src.startsWith('data:')),
    }));
    confere(r.funcao && r.bandeiras.length === 3 && r.bandeiras.every(Boolean),
      `6. ${codigo}.html: aedTranslate existe e as 3 bandeiras estão embutidas`);
    await ctx.close();
  }

  // 0. Calibração: com o script ANTERIOR, o item 2 tem de reprovar
  {
    let antigo = null;
    try {
      antigo = CALIBRAR ? readFileSync(CALIBRAR, 'utf8')
        : execFileSync('git', ['show', '36e8ddd:src/main/resources/META-INF/resources/web/js/i18n-translate.js'], { cwd: RAIZ, encoding: 'utf8' });
    } catch { antigo = null; }
    if (!antigo || /aed-tradutor-aviso/.test(antigo)) {
      naoVerificado.push('0. calibração: não há versão anterior sem o aviso para comparar (passe --calibrar=<arquivo>)');
    } else {
      const { ctx, page } = await contexto({ idioma: 'en', elementJs: captcha, scriptAntigo: antigo });
      await page.goto(BASE + '/', { waitUntil: 'load' });
      await page.waitForTimeout(1500);
      confere(!(await avisoVisivel(page)), '0. calibração: com o script anterior o detector NÃO acha aviso (ele discrimina)');
      await ctx.close();
    }
  }
} catch (e) {
  falhas.push('exceção: ' + String(e.message || e).split(/\r?\n/)[0]);
} finally {
  await browser.close();
}

ok.forEach((d) => console.log('ok    ' + d));
naoVerificado.forEach((d) => console.log('NAO VERIFICOU ' + d));
falhas.forEach((d) => console.log('FALHA ' + d));
console.log(`\n${ok.length} ok, ${falhas.length} falhas, ${naoVerificado.length} não verificados`);
process.exit(falhas.length ? 1 : (naoVerificado.length || ok.length === 0 ? 2 : 0));
