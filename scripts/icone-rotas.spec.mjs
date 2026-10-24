// icone-rotas.spec.mjs v1.0 (2026-10-24) — componente compartilhado: não editar no projeto; mudança só na origem.
// Roda a varredura de ícones (rótulo sem ícone reprova; títulos editoriais e exceções declaradas só são listados) em
// cada rota do projeto, no HTML que o usuário recebe — inclusive o que o JavaScript monta depois do carregamento.
// Uso:  BASE_URL=http://localhost:8080 ROTAS=/,/clientes,/pedidos npx playwright test icone-rotas.spec.mjs
// Marcações: data-icone-excecao="motivo" (fora da contagem; motivo registrado) e data-titulo-editorial em h1-h6.
import { test, expect } from '@playwright/test';
import fs from 'node:fs'; import path from 'node:path'; import { fileURLToPath } from 'node:url';
const dir = path.dirname(fileURLToPath(import.meta.url));
const varSrc = fs.readFileSync(path.join(dir, 'icone-varredura.js'), 'utf8');
test.use({ launchOptions: process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {} });
const BASE = process.env.BASE_URL, ROTAS = (process.env.ROTAS || '/').split(',');
if (!BASE) throw new Error('Defina BASE_URL (ex.: http://localhost:8080).');
for (const rota of ROTAS) {
  test(`ícones em ${rota}: nenhum rótulo sem ícone`, async ({ page }) => {
    await page.goto(BASE.replace(/\/$/, '') + rota); await page.waitForLoadState('networkidle');
    await page.addScriptTag({ content: varSrc });
    const falhas = await page.evaluate(() => varreduraIcone());
    const avisos = await page.evaluate(() => avisosIcone());
    for (const a of avisos) { test.info().annotations.push({ type: 'aviso de ícone', description: `${a.elemento}: ${a.problema}` }); console.log(`[aviso de ícone] ${rota} ${a.elemento}: ${a.problema}`); }
    const total = await page.evaluate(() => document.querySelectorAll('label, th, caption, legend, summary, button, h1, h2, h3, h4, h5, h6').length);
    console.log(`[ícones] ${rota}: ${total} rótulos no escopo básico, ${falhas.length} sem ícone, ${avisos.length} declarados fora da contagem`);
    expect(falhas.map((f) => `${f.tipo} "${f.elemento}": ${f.problema}`), 'rótulos sem ícone (label, coluna, título de área, botão, menu, legend, summary, KPI)').toEqual([]);
  });
}
