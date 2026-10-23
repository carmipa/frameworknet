/**
 * Validação viva das páginas de aprofundamento novas: abre cada uma num Chromium
 * real, coleta erros de console/página, confirma que o Mermaid virou SVG (quando
 * há diagrama), que a régua de bits apareceu (quando há cabeçalho) e tira
 * screenshot full-page. Uso: node scripts/validar-aprofundamentos.mjs
 */
import { chromium } from "playwright";
import { mkdir } from "fs/promises";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const BASE = (process.argv[2] || "http://localhost:8080").replace(/\/$/, "");
const OUT = path.join(__dirname, "..", "screenshots", "aprofundamentos");

const PAGINAS = [
    { slug: "http", diagrama: true, regua: false, acento: "requisição" },
    { slug: "ftp", diagrama: true, regua: true, acento: "transferência" },
    { slug: "smtp", diagrama: true, regua: false, acento: "eletrônico" },
    { slug: "telnet", diagrama: true, regua: false, acento: "não" },
    { slug: "handshake", diagrama: true, regua: false, acento: "conexão" },
];

const browser = await chromium.launch();
await mkdir(OUT, { recursive: true });
let problemas = 0;

for (const p of PAGINAS) {
    const page = await browser.newPage({ viewport: { width: 1280, height: 1000 } });
    const erros = [];
    page.on("console", (m) => { if (m.type() === "error") erros.push(m.text()); });
    page.on("pageerror", (e) => erros.push("pageerror: " + e.message));

    const url = `${BASE}/protocolos/${p.slug}`;
    await page.goto(url, { waitUntil: "networkidle" });

    // Mermaid: espera o SVG dentro de .aprof-mermaid (render é assíncrono).
    let svgOk = "n/a";
    if (p.diagrama) {
        try {
            await page.waitForSelector(".aprof-mermaid svg", { timeout: 8000 });
            svgOk = "SVG";
        } catch { svgOk = "SEM-SVG"; problemas++; }
    }

    const reguaCount = await page.locator(".aprof-regua-campo").count();
    const reguaOk = p.regua ? (reguaCount > 0 ? `${reguaCount} campos` : "AUSENTE") : "n/a";
    if (p.regua && reguaCount === 0) problemas++;

    const tabelas = await page.locator(".aprof-tabela").count();
    const conceitos = await page.locator(".aprof-conceito").count();
    const bodyText = await page.locator("body").innerText();
    const acentoOk = bodyText.includes(p.acento) ? "OK" : `SEM '${p.acento}'`;
    if (!bodyText.includes(p.acento)) problemas++;
    // erros de console ruído-livres (ignora 401 de auth que não ocorre aqui)
    const errosReais = erros.filter((e) => !/favicon/i.test(e));
    if (errosReais.length) problemas++;

    await page.screenshot({ path: path.join(OUT, `${p.slug}.png`), fullPage: true });
    console.log(`${p.slug.padEnd(10)} mermaid=${svgOk} regua=${reguaOk} tabelas=${tabelas} conceitos=${conceitos} acento=${acentoOk} erros=${errosReais.length ? errosReais.join(" | ") : "0"}`);
    await page.close();
}

await browser.close();
console.log(problemas === 0 ? "\nVALIDACAO VIVA: TUDO OK" : `\nVALIDACAO VIVA: ${problemas} PROBLEMA(S)`);
process.exit(problemas === 0 ? 0 : 1);
