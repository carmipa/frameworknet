/** Validação viva das páginas de Transporte/Rede (TCP, UDP, IPv4, IPv6, ARP, ICMP). */
import { chromium } from "playwright";
import { mkdir } from "fs/promises";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const BASE = (process.argv[2] || "http://localhost:8080").replace(/\/$/, "");
const OUT = path.join(__dirname, "..", "screenshots", "aprofundamentos");
const SLUGS = ["tcp", "udp", "ipv4", "ipv6", "arp", "icmp"];
const ACENTO = /[áàâãéêíóôõúüç]/i;

const browser = await chromium.launch();
await mkdir(OUT, { recursive: true });
let problemas = 0;

for (const slug of SLUGS) {
  const page = await browser.newPage({ viewport: { width: 1280, height: 1000 } });
  const erros = [];
  page.on("console", (m) => { if (m.type() === "error") erros.push(m.text()); });
  page.on("pageerror", (e) => erros.push("pageerror: " + e.message));

  await page.goto(`${BASE}/protocolos/${slug}`, { waitUntil: "networkidle" });

  let svg = "SEM-SVG";
  try { await page.waitForSelector(".aprof-mermaid svg", { timeout: 8000 }); svg = "SVG"; }
  catch { problemas++; }

  const campos = await page.locator(".aprof-regua-campo").count();
  const tabelas = await page.locator(".aprof-tabela").count();
  const bodyText = await page.locator("body").innerText();
  const acento = ACENTO.test(bodyText);
  if (!acento) problemas++;
  if (campos === 0) problemas++;
  const errosReais = erros.filter((e) => !/favicon/i.test(e));
  if (errosReais.length) problemas++;

  await page.screenshot({ path: path.join(OUT, `${slug}.png`), fullPage: true });
  console.log(`${slug.padEnd(6)} mermaid=${svg} regua=${campos}campos tabelas=${tabelas} acento=${acento ? "OK" : "AUSENTE"} erros=${errosReais.length ? errosReais.join(" | ") : "0"}`);
  await page.close();
}

await browser.close();
console.log(problemas === 0 ? "\nVALIDACAO VIVA (Transporte/Rede): TUDO OK" : `\nVALIDACAO VIVA: ${problemas} PROBLEMA(S)`);
process.exit(problemas === 0 ? 0 : 1);
