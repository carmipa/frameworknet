// Campos de formulário sem nome acessível, medidos pelo PRÓPRIO navegador (auditoria FRONT-17).
// Uso: node verificar-nomes-acessiveis.mjs [baseUrl]. Sai 0 se nenhum campo visível ficou sem nome,
// 1 se algum ficou, 2 se não conseguiu rodar. O nome vem do ariaSnapshot do Playwright, que usa o
// cálculo do Chromium (label, aria-label, title...) — uma varredura de texto no template não reproduz isso.
import { chromium } from 'playwright';

const BASE = process.argv[2] || 'http://localhost:8095';
const ROTAS = ['/', '/analise', '/calculadora', '/ipv6', '/ipv6/analise', '/ipv6/resolucao', '/portas', '/protocolos',
  '/criptografia', '/camadas', '/wifi', '/ferramentas', '/ferramentas/rede', '/certificados', '/diagnostico',
  '/seguranca', '/trafego', '/localizacao', '/informacoes', '/resolucao-problemas', '/laboratorios/camadas',
  '/documentacao', '/sobre', '/admin/login'];

let browser;
try {
  browser = await chromium.launch();
} catch (e) {
  console.log('NÃO VERIFICOU —', e.message);
  process.exit(2);
}
const ctx = await browser.newContext();
await ctx.route(/translate\.google(apis)?\.com|translate-pa\.googleapis\.com|www\.google\.com/, (r) => r.abort());
const page = await ctx.newPage();
const sem = [];
let medidos = 0;
for (const rota of ROTAS) {
  const r = await page.goto(BASE + rota, { waitUntil: 'load', timeout: 45000 });
  if (!r || r.status() !== 200) {
    console.log('NÃO VERIFICOU —', rota, 'respondeu', r && r.status());
    await browser.close();
    process.exit(2);
  }
  await page.waitForTimeout(600);
  const campos = page.locator('input:visible, select:visible, textarea:visible');
  const n = await campos.count();
  for (let i = 0; i < n; i++) {
    const c = campos.nth(i);
    const tipo = await c.evaluate((el) => (el.getAttribute('type') || el.tagName).toLowerCase());
    if (['hidden', 'submit', 'button', 'reset', 'image'].includes(tipo)) continue;
    medidos++;
    const snap = (await c.ariaSnapshot()).split('\n')[0];
    if (!/"[^"]+"/.test(snap)) {
      const id = await c.evaluate((el) => el.id || el.name || el.className);
      sem.push(`${rota}: ${snap.trim()} (${id})`);
    }
  }
}
await browser.close();
console.log(`${medidos} campos visíveis medidos em ${ROTAS.length} páginas; ${sem.length} sem nome`);
sem.forEach((s) => console.log('  SEM NOME', s));
if (medidos < 30) {
  console.log('NÃO VERIFICOU — instrumento cego (poucos campos medidos)');
  process.exit(2);
}
process.exit(sem.length ? 1 : 0);
