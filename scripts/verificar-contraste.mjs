import { chromium } from 'playwright';
// Varredura de contraste WCAG AA (texto normal 4,5:1; grande 3:1). Fundo = pilha de cores do elemento
// até a primeira opaca; gradiente em ancestral entra como PIOR caso (cada parada vira um fundo candidato);
// fundo com imagem (url) e texto dentro de SVG são NÃO VERIFICADOS, nunca aprovados.
// Uso: node verificar-contraste.mjs http://localhost:8080 [rota1,rota2,...]  (sem rotas: lê o /sitemap.xml)
// Sai 1 se algum texto medido ficar abaixo de AA; 2 se nada foi medido (instrumento cego).
const BASE = process.argv[2] || 'http://localhost:8095';
let rotas = process.argv[3] ? process.argv[3].split(',') : null;
if (!rotas) {
  const xml = await (await fetch(BASE + '/sitemap.xml')).text();
  rotas = [...xml.matchAll(/<loc>https?:\/\/[^/]+([^<]*)<\/loc>/g)].map(m => m[1] || '/');
}
const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 900 } });
await ctx.route(/translate\.google|www\.google\.com/, r => r.abort());
const p = await ctx.newPage();
let total = 0, totalNv = 0, totalMedidos = 0;
for (const r of rotas) {
  await p.goto(BASE + r, { waitUntil: 'load' }); await p.waitForTimeout(400);
  const res = await p.evaluate(() => {
    const cores = s => [...s.matchAll(/rgba?\(([^)]+)\)/g)].map(m => { const v = m[1].split(/[ ,\/]+/).filter(Boolean).map(Number); return { r: v[0], g: v[1], b: v[2], a: v.length > 3 ? v[3] : 1 }; });
    const lum = c => { const f = x => { x /= 255; return x <= 0.03928 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4; }; return 0.2126 * f(c.r) + 0.7152 * f(c.g) + 0.0722 * f(c.b); };
    const sobre = (c, base) => ({ r: c.r * c.a + base.r * (1 - c.a), g: c.g * c.a + base.g * (1 - c.a), b: c.b * c.a + base.b * (1 - c.a) });
    // Lista de fundos candidatos (do mais externo ao elemento); null = não verificável.
    const fundos = el => {
      const camadas = [];
      for (let e = el; e && e !== document.documentElement; e = e.parentElement) {
        const cs = getComputedStyle(e);
        const img = e === document.body ? 'none' : cs.backgroundImage;
        if (img !== 'none') { if (/url\(/.test(img)) return null; camadas.push({ grad: cores(img) }); }
        const c = cores(cs.backgroundColor)[0];
        if (c && c.a > 0) camadas.push({ cor: c });
        if (c && c.a >= 1) break;
      }
      let cand = [{ r: 6, g: 8, b: 15 }];
      for (let i = camadas.length - 1; i >= 0; i--) {
        const k = camadas[i];
        if (k.cor) cand = cand.map(bg => sobre(k.cor, bg));
        else if (k.grad.length) cand = cand.flatMap(bg => k.grad.map(g => sobre(g, bg)));
      }
      return cand;
    };
    const out = [];
    for (const el of document.querySelectorAll('body *')) {
      if (![...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim())) continue;
      const cs = getComputedStyle(el); if (cs.visibility === 'hidden' || cs.display === 'none' || !el.getClientRects().length) continue;
      if (el.closest('[aria-hidden="true"]') || el.classList.contains('material-symbols-outlined')) continue;
      // Visualmente oculto (visually-hidden: caixa de 1 px recortada) não é texto na tela: o link "Pular" só aparece
      // com o foco, e aí tem 17,8:1. Medido como estava, ele reprovava 84 páginas (UX-15, auditoria de 22/10/2026).
      { const r = el.getBoundingClientRect(); if (r.width <= 1 || r.height <= 1) continue; }
      if (el.closest('svg')) { out.push({ nv: true }); continue; } // fundo é o fill do SVG, que o CSS não mostra
      const c = cores(cs.color)[0]; if (!c) continue;
      const bgs = fundos(el); if (!bgs) { out.push({ nv: true }); continue; }
      let pior = 99;
      for (const bg of bgs) { const fg = sobre(c, bg); const L1 = lum(fg), L2 = lum(bg); pior = Math.min(pior, (Math.max(L1, L2) + 0.05) / (Math.min(L1, L2) + 0.05)); }
      const px = parseFloat(cs.fontSize), grande = px >= 24 || (px >= 18.66 && +cs.fontWeight >= 700);
      out.push({ ok: pior >= (grande ? 3 : 4.5), cr: pior.toFixed(2), cor: cs.color, txt: el.textContent.trim().slice(0, 40), cls: el.className.toString().slice(0, 50) });
    }
    return out;
  });
  const nv = res.filter(x => x.nv).length, medidos = res.filter(x => !x.nv), reais = medidos.filter(x => !x.ok);
  total += reais.length; totalNv += nv; totalMedidos += medidos.length;
  console.log(`${r}: ${reais.length} abaixo de AA de ${medidos.length} medidos (nao verificados: ${nv})`);
  const grupos = {}; for (const x of reais) { (grupos[x.cor] = grupos[x.cor] || []).push(x); }
  for (const [k, v] of Object.entries(grupos)) console.log(`   ${k} x${v.length} ex: "${v[0].txt}" (${v[0].cr}) .${v[0].cls}`);
}
console.log(`TOTAL=${total} MEDIDOS=${totalMedidos} NAO_VERIFICADOS=${totalNv}`);
await b.close();
process.exit(totalMedidos === 0 ? 2 : (total ? 1 : 0));
