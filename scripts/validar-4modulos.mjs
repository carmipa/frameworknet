/* Testes de tela dos 4 módulos novos: renderização + interativos funcionando. */
import { chromium } from "playwright";
import { mkdir } from "fs/promises";
import path from "path";
import { fileURLToPath } from "url";
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const BASE = (process.argv[2] || "http://localhost:8080").replace(/\/$/, "");
const OUT = path.join(__dirname, "..", "screenshots", "referencias");
const browser = await chromium.launch();
await mkdir(OUT, { recursive: true });
let prob = 0;
const erros = [];
function check(cond, msg) { if (!cond) { prob++; erros.push(msg); } }

async function shot(page, name) { await page.screenshot({ path: path.join(OUT, name + ".png"), fullPage: true }); }

// ---- Criptografia: hash playground + força ----
{
  const p = await browser.newPage({ viewport: { width: 1280, height: 1200 } });
  const ce = []; p.on("console", m => { if (m.type() === "error") ce.push(m.text()); }); p.on("pageerror", e => ce.push("pageerror:" + e.message));
  await p.goto(`${BASE}/criptografia`, { waitUntil: "domcontentloaded" });
  await p.fill("#cripto-in", "abc");
  await p.waitForFunction(() => {
    const r = document.querySelector('#cripto-out tr[data-alg="SHA-256"] .cripto-val');
    return r && r.textContent && r.textContent.length === 64;
  }, { timeout: 8000 }).catch(() => {});
  const sha = await p.$eval('#cripto-out tr[data-alg="SHA-256"] .cripto-val', el => el.textContent);
  // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
  check(sha === "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "cripto SHA-256(abc) errado: " + sha);
  await p.selectOption("#forca-alg", "md5");
  const forca = await p.$eval("#forca-out", el => el.textContent);
  check(/Quebrado/i.test(forca), "força MD5 deveria ser Quebrado: " + forca);
  const cf = ce.filter(e => !/favicon|google|translate/i.test(e));
  check(cf.length === 0, "cripto console erros: " + cf.join("|"));
  await shot(p, "criptografia");
  console.log("criptografia: SHA-256(abc)=" + (sha.slice(0, 12)) + "... força=" + (/Quebrado/i.test(forca) ? "OK" : "X") + " erros=" + cf.length);
  await p.close();
}
// ---- Wi-Fi: channel planner ----
{
  const p = await browser.newPage({ viewport: { width: 1280, height: 1200 } });
  const ce = []; p.on("console", m => { if (m.type() === "error") ce.push(m.text()); }); p.on("pageerror", e => ce.push("pageerror:" + e.message));
  await p.goto(`${BASE}/wifi`, { waitUntil: "domcontentloaded" });
  await p.click("#wifi-recomendar");
  const rec = await p.$eval("#wifi-resultado", el => el.textContent);
  check(/Nenhuma sobreposi/i.test(rec), "wifi 1/6/11 deveria não sobrepor: " + rec);
  // clicar canal 2 e 3 (sobrepõem) após limpar — locator re-resolve após cada re-render
  await p.click("#wifi-limpar");
  await p.locator(".wifi-canal").nth(1).click(); // canal 2
  await p.locator(".wifi-canal").nth(2).click(); // canal 3
  const conf = await p.$eval("#wifi-resultado", el => el.textContent);
  check(/sobrepondo/i.test(conf), "wifi 2+3 deveria sobrepor: " + conf);
  const cf = ce.filter(e => !/favicon|google|translate/i.test(e));
  check(cf.length === 0, "wifi console erros: " + cf.join("|"));
  await shot(p, "wifi");
  console.log("wifi: 1/6/11=" + (/Nenhuma sobreposi/i.test(rec) ? "OK" : "X") + " 2+3conflito=" + (/sobrepondo/i.test(conf) ? "OK" : "X") + " erros=" + cf.length);
  await p.close();
}
// ---- Ferramentas: command builder ----
{
  const p = await browser.newPage({ viewport: { width: 1280, height: 1200 } });
  const ce = []; p.on("console", m => { if (m.type() === "error") ce.push(m.text()); }); p.on("pageerror", e => ce.push("pageerror:" + e.message));
  await p.goto(`${BASE}/ferramentas`, { waitUntil: "domcontentloaded" });
  await p.selectOption("#cmd-tool", "nmap");
  await p.fill("#cmd-alvo", "192.168.0.1");
  await p.check("#opt-sS");
  const cmd = await p.$eval("#cmd-saida", el => el.textContent);
  check(/^nmap/.test(cmd) && cmd.includes("-sS") && cmd.includes("192.168.0.1"), "cmd builder nmap errado: " + cmd);
  const cf = ce.filter(e => !/favicon|google|translate/i.test(e));
  check(cf.length === 0, "ferramentas console erros: " + cf.join("|"));
  await shot(p, "ferramentas");
  console.log("ferramentas: cmd=\"" + cmd + "\" erros=" + cf.length);
  await p.close();
}
// ---- Camadas: Geral + um aprofundamento com Mermaid ----
{
  const p = await browser.newPage({ viewport: { width: 1280, height: 1200 } });
  const ce = []; p.on("console", m => { if (m.type() === "error") ce.push(m.text()); }); p.on("pageerror", e => ce.push("pageerror:" + e.message));
  await p.goto(`${BASE}/camadas`, { waitUntil: "domcontentloaded" });
  const linhas = await p.locator(".aprof-tabela tbody tr").count();
  check(linhas >= 7, "camadas tabela deveria ter >=7 linhas: " + linhas);
  await shot(p, "camadas");
  await p.goto(`${BASE}/camadas/osi`, { waitUntil: "domcontentloaded" });
  let svg = false; try { await p.waitForSelector(".aprof-mermaid svg", { timeout: 10000 }); svg = true; } catch {}
  check(svg, "camadas/osi mermaid não renderizou");
  await shot(p, "camadas-osi");
  const cf = ce.filter(e => !/favicon|google|translate/i.test(e));
  console.log("camadas: linhas=" + linhas + " osi-mermaid=" + (svg ? "SVG" : "X") + " erros=" + cf.length);
  await p.close();
}
await browser.close();
console.log(prob === 0 ? "\nTESTES DE TELA: TUDO OK" : "\n" + prob + " PROBLEMA(S):\n - " + erros.join("\n - "));
process.exit(prob === 0 ? 0 : 1);
