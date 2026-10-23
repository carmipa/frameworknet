// Diagramas da Academia na /documentacao (renderizados pelo Mermaid, sem erro de sintaxe) e animações do
// nível Transporte (rodam com movimento normal, desligam com movimento reduzido). 0 passou, 1 reprovou, 2 não verificou.
import { chromium } from 'playwright';

const BASE = process.argv[2] || 'http://localhost:8095';
const falhas = [];
const ok = [];
const confere = (c, d) => (c ? ok : falhas).push(d);
const browser = await chromium.launch();

try {
  // ---------------------------------------------------------------- diagramas (Mermaid vem do CDN do site: sem bloqueio)
  {
    const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
    await page.goto(BASE + '/documentacao', { waitUntil: 'load' });
    await page.waitForTimeout(4000);
    const r = await page.evaluate(() => {
      const h = [...document.querySelectorAll('h3')].find((x) => x.textContent.includes('Academia: fatias, peers e kernel'));
      if (!h) return { titulo: false };
      const svgs = [];
      let el = h.nextElementSibling;
      while (el && el.tagName !== 'H2' && el.tagName !== 'H3') {
        el.querySelectorAll('svg').forEach((s) => svgs.push({ erro: /Syntax error|Parse error/i.test(s.textContent), texto: s.textContent.slice(0, 60) }));
        if (el.tagName === 'svg') svgs.push({ erro: /Syntax error/i.test(el.textContent), texto: el.textContent.slice(0, 60) });
        el = el.nextElementSibling;
      }
      const toc = [...document.querySelectorAll('.doc-toc-link')].some((a) => a.textContent.includes('Academia'));
      return { titulo: true, svgs, toc, ancora: h.id };
    });
    confere(r.titulo, 'seção "Academia: fatias, peers e kernel" na documentação');
    confere(r.svgs && r.svgs.length === 3, 'três diagramas renderizados em SVG: ' + JSON.stringify(r.svgs));
    confere(r.svgs && r.svgs.every((s) => !s.erro), 'nenhum diagrama com erro de sintaxe do Mermaid');
    confere(r.toc, 'a seção aparece no sumário');
    // o renderizador do README não converte link para âncora: markdown cru na tela é defeito visível
    const cru = await page.evaluate(() => /\]\(#[a-z-]+\)/.test(document.querySelector('.markdown-doc').textContent));
    confere(!cru, 'nenhum link markdown cru aparecendo na documentação');
    await page.close();
  }

  // ---------------------------------------------------------------- animações do aperto de mão e da janela
  for (const reduzido of [false, true]) {
    const ctx = await browser.newContext({ reducedMotion: reduzido ? 'reduce' : 'no-preference' });
    const page = await ctx.newPage();
    await page.goto(BASE + '/academia/transporte/aperto', { waitUntil: 'load' });
    await page.click('#ver-animacao [data-acad-acao="passo"]');
    await page.click('#ver-animacao [data-acad-acao="passo"]');
    const anim = await page.$eval('#ver-setas li.destaque', (li) => {
      const st = getComputedStyle(li, '::after');
      return { nome: st.animationName, display: st.display, para: li.classList.contains('para-cliente') ? 'cliente' : 'servidor' };
    });
    if (reduzido) {
      confere(anim.nome === 'none' || anim.display === 'none', 'movimento reduzido: o ponto não viaja ' + JSON.stringify(anim));
    } else {
      confere(anim.nome === 'acad-viaja-esquerda' && anim.para === 'cliente', 'SYN-ACK: o ponto viaja do servidor para o cliente ' + JSON.stringify(anim));
      await page.click('#ver-animacao [data-acad-acao="passo"]');
      const ida = await page.$eval('#ver-setas li.destaque', (li) => getComputedStyle(li, '::after').animationName);
      confere(ida === 'acad-viaja-direita', 'ACK: o ponto viaja do cliente para o servidor (' + ida + ')');
      // no meio da viagem o ponto está entre as pontas
      await page.click('#ver-animacao [data-acad-acao="passo"]');
      await page.waitForTimeout(500);
      const meio = await page.$eval('#ver-setas li.destaque', (li) => parseFloat(getComputedStyle(li, '::after').left) / li.getBoundingClientRect().width);
      confere(meio > 0.1 && meio < 0.95, `o ponto está em movimento no meio da seta (${(meio * 100).toFixed(0)}%)`);
    }
    await page.goto(BASE + '/academia/transporte/janela', { waitUntil: 'load' });
    await page.click('#ver-animacao [data-acad-acao="passo"]');
    const pulso = await page.$eval('#ver-segmentos li.mudou', (li) => getComputedStyle(li).animationName);
    confere(reduzido ? pulso === 'none' : pulso === 'acad-pulso', (reduzido ? 'movimento reduzido: sem pulso' : 'o segmento que mudou pulsa') + ' (' + pulso + ')');
    await ctx.close();
  }
} catch (e) {
  falhas.push('exceção: ' + e.message.split(/\r?\n/)[0]);
} finally {
  await browser.close();
}
ok.forEach((d) => console.log('ok   ' + d));
falhas.forEach((d) => console.log('FALHA ' + d));
console.log(`\n${ok.length} ok, ${falhas.length} falhas`);
process.exit(ok.length === 0 ? 2 : falhas.length ? 1 : 0);
