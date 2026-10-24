// dica-rotas.spec.mjs v1.2 (2026-10-24) — componente compartilhado: não editar no projeto; mudança só na origem.
// Roda a varredura das dicas (falhas reprovam; avisos só são listados) e o axe (WCAG 2.2 AA) em cada rota do projeto.
// Uso:  BASE_URL=http://localhost:8080 ROTAS=/,/clientes,/pedidos npx playwright test dica-rotas.spec.mjs
// Exceções: data-dica-excecao="motivo" no elemento, com o motivo registrado.
import { test, expect } from '@playwright/test';
import fs from 'node:fs'; import path from 'node:path'; import { fileURLToPath } from 'node:url';
const dir = path.dirname(fileURLToPath(import.meta.url));
const axeSrc = fs.readFileSync(path.join(dir, 'node_modules/axe-core/axe.min.js'), 'utf8');
const varSrc = fs.readFileSync(path.join(dir, 'dica-varredura.js'), 'utf8');
test.use({ launchOptions: process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {} });
const BASE = process.env.BASE_URL, ROTAS = (process.env.ROTAS || '/').split(',');
if (!BASE) throw new Error('Defina BASE_URL (ex.: http://localhost:8080).');
for (const rota of ROTAS) {
  test(`dicas em ${rota}: nenhuma falha de dica e nenhuma violação axe`, async ({ page }) => {
    await page.goto(BASE.replace(/\/$/, '') + rota); await page.waitForLoadState('networkidle');
    await page.addScriptTag({ content: varSrc }); await page.addScriptTag({ content: axeSrc });
    const falhas = await page.evaluate(() => varreduraDica());
    const avisos = await page.evaluate(() => avisosDica());
    for (const a of avisos) { test.info().annotations.push({ type: 'aviso de dica', description: `${a.elemento}: ${a.problema}` }); console.log(`[aviso de dica] ${rota} ${a.elemento}: ${a.problema}`); }
    expect(falhas, 'falhas de dica (dica que falta onde há o que explicar, ou dica de má qualidade)').toEqual([]);
    const r = await page.evaluate(() => axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a','wcag2aa','wcag21a','wcag21aa','wcag22aa'] } }));
    expect(r.violations.map(v => v.id + ': ' + v.nodes.map(n => n.target).join('|'))).toEqual([]);
  });
}
