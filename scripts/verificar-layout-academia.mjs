// Verificação de layout da Academia no navegador real (Chromium), em 4 larguras (1600, 820, 390, 320):
//  - nenhuma página estoura a largura da tela (barra horizontal);
//  - o menu fica na MESMA posição que nas outras páginas do site (referência: /laboratorios);
//  - Ver e Mexer dividem a linha sem coluna vazia ao lado; Provar ocupa a linha inteira;
//  - o botão Conferir fica alinhado ao campo de resposta;
//  - a caixa de correção vazia não aparece como uma caixa em branco.
// Nasceu da revisão de alinhamento pedida por Paulo em 01/10/2026; calibrada contra a versão anterior,
// que reprovava em estouro (394 > 390), menu deslocado e caixa vazia.
// Uso: node scripts/verificar-layout-academia.mjs [http://localhost:8081]
// Saída: 0 passou · 1 reprovou · 2 não verificou (nada medido).
import { chromium } from 'playwright';
const BASE = process.argv[2] || 'http://localhost:8081';
const rotas = ['/academia', '/academia/fundamentos', '/academia/fundamentos/binario', '/academia/fundamentos/hexadecimal',
  '/academia/fundamentos/camadas', '/academia/ipv4', '/academia/ipv4/mascara', '/academia/ipv4/subredes',
  '/academia/transporte', '/academia/transporte/aperto', '/academia/transporte/janela'];
const falhas = []; let ok = 0;
const confere = (c, d) => { if (c) ok++; else falhas.push(d); };
const b = await chromium.launch();
for (const [nome, w, h] of [['desk', 1600, 1000], ['tablet', 820, 1180], ['cel', 390, 844], ['cel-pequeno', 320, 640]]) {
  const ctx = await b.newContext({ viewport: { width: w, height: h } });
  await ctx.route('**/*', (r) => (/google/.test(new URL(r.request().url()).hostname) ? r.abort() : r.continue()));
  const p = await ctx.newPage();
  await p.goto(BASE + '/laboratorios', { waitUntil: 'load' });
  const navRef = await p.$eval('.aed-topnav', (e) => { const r = e.getBoundingClientRect(); return [Math.round(r.left), Math.round(r.right), Math.round(r.top)]; });
  for (const rota of rotas) {
    await p.goto(BASE + rota, { waitUntil: 'load' });
    const m = await p.evaluate(() => {
      const nav = document.querySelector('.aed-topnav').getBoundingClientRect();
      const r = { sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth,
        nav: [Math.round(nav.left), Math.round(nav.right), Math.round(nav.top)] };
      const ver = document.getElementById('ver'), mexer = document.getElementById('mexer'), provar = document.getElementById('provar');
      if (ver && mexer && provar) {
        const a = ver.getBoundingClientRect(), bb = mexer.getBoundingClientRect(), c = provar.getBoundingClientRect();
        r.etapas = { mesmaLinha: Math.abs(a.top - bb.top) < 2, direitaMexer: bb.right, direitaProvar: c.right, larga: ver.classList.contains('acad-etapa-larga') };
        const input = document.getElementById('provar-resposta').getBoundingClientRect();
        const botao = document.querySelector('#provar-form button[type="submit"]').getBoundingClientRect();
        r.provar = { campoBase: input.bottom, botaoBase: botao.bottom, mesmaLinha: botao.top < input.bottom };
        const fb = getComputedStyle(document.getElementById('provar-feedback'));
        r.feedbackVazioSemBorda = fb.borderTopColor === 'rgba(0, 0, 0, 0)';
      }
      return r;
    });
    confere(m.sw <= m.cw, `${nome} ${rota}: estouro horizontal ${m.sw} > ${m.cw}`);
    confere(m.nav[0] === navRef[0] && m.nav[1] === navRef[1] && m.nav[2] === navRef[2],
      `${nome} ${rota}: menu em ${m.nav} e nas outras páginas em ${navRef}`);
    if (m.etapas) {
      if (!m.etapas.larga && w >= 1100) {
        confere(m.etapas.mesmaLinha && Math.abs(m.etapas.direitaMexer - m.etapas.direitaProvar) < 2,
          `${nome} ${rota}: Ver/Mexer não ocupam a linha (Mexer termina em ${m.etapas.direitaMexer}, Provar em ${m.etapas.direitaProvar})`);
      }
      if (m.provar.mesmaLinha) {
        confere(Math.abs(m.provar.campoBase - m.provar.botaoBase) < 6, `${nome} ${rota}: botão Conferir desalinhado do campo (${m.provar.botaoBase} × ${m.provar.campoBase})`);
      }
      confere(m.feedbackVazioSemBorda, `${nome} ${rota}: caixa de correção vazia aparece com borda`);
    }
  }
}
await b.close();
falhas.forEach((f) => console.log('FALHA ' + f));
console.log(`layout: ${ok} ok, ${falhas.length} falhas`);
process.exit(falhas.length ? 1 : (ok ? 0 : 2));
