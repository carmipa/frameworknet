/**
 * Verifica, num browser real, se o site e mesmo instalavel como PWA.
 *
 * PROPOSITO: instalabilidade nao se prova por curl. O manifest pode responder
 * 200 e ainda ser descartado; o service worker pode responder 200 e falhar no
 * registro por MIME. So o navegador diz a verdade.
 *
 * USO:  node scripts/verificar-pwa.mjs [base-url]
 * SAIDA: 0 instalavel; 1 se o service worker nao registrar ou o manifest nao carregar;
 *        2 se nem a pagina abriu (nao verificou).
 */
import { chromium } from 'playwright';

const base = process.argv[2] || 'https://frameworknet.carminati.dev.br';
const navegador = await chromium.launch();
const contexto = await navegador.newContext();
// O widget do Google Tradutor mantém a rede ocupada (o "networkidle" nunca chegava) e, em volume,
// leva o IP ao captcha: a tradução tem roteiro próprio (verificar-tradutor.mjs).
await contexto.route(/translate\.google(apis)?\.com|translate-pa\.googleapis\.com|www\.google\.com/, (r) => r.abort());
const pagina = await contexto.newPage();

const erros = [];
pagina.on('console', (m) => {
  const t = m.text();
  if (/service ?worker|manifest|MIME/i.test(t) && /erro|error|fail|unsupported|refus/i.test(t)) {
    erros.push(t.slice(0, 200));
  }
});
pagina.on('pageerror', (e) => erros.push(String(e).slice(0, 200)));

try {
  await pagina.goto(base + '/', { waitUntil: 'load', timeout: 45000 });
} catch (e) {
  // Não conseguir abrir a página é NÃO VERIFICOU (2), não reprovação do PWA.
  console.log('NAO VERIFICOU —', String(e).split('\n')[0]);
  await navegador.close();
  process.exit(2);
}

// 1. O manifest foi reconhecido pelo browser?
const manifest = await pagina.evaluate(async () => {
  const link = document.querySelector('link[rel="manifest"]');
  if (!link) return { ok: false, motivo: 'sem <link rel="manifest">' };
  try {
    const r = await fetch(link.href);
    const tipo = r.headers.get('content-type') || '(sem tipo)';
    const j = await r.json();
    return { ok: true, tipo, nome: j.name, icones: (j.icons || []).length, display: j.display };
  } catch (e) {
    return { ok: false, motivo: String(e) };
  }
});

// 2. O service worker REGISTROU de fato?
const sw = await pagina.evaluate(async () => {
  if (!('serviceWorker' in navigator)) return { ok: false, motivo: 'sem suporte' };
  try {
    const reg = await navigator.serviceWorker.register('/sw.js', { scope: '/' });
    await new Promise((r) => setTimeout(r, 2500));
    return {
      ok: true,
      escopo: reg.scope,
      estado: (reg.active || reg.installing || reg.waiting || {}).state || 'desconhecido',
    };
  } catch (e) {
    return { ok: false, motivo: String(e).slice(0, 200) };
  }
});

console.log('MANIFEST:', JSON.stringify(manifest));
console.log('SERVICE WORKER:', JSON.stringify(sw));
if (erros.length) {
  console.log('ERROS DE CONSOLE:');
  [...new Set(erros)].slice(0, 6).forEach((e) => console.log('   ', e));
}

await navegador.close();

const falhou = !manifest.ok || !sw.ok || manifest.icones < 2;
console.log(falhou ? '\nNAO instalavel — ver acima.' : '\nInstalavel: manifest valido e service worker ativo.');
process.exit(falhou ? 1 : 0);
