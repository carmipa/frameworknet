/**
 * Catraca das dicas (regra 6-A): todo título, rótulo, campo, botão e cabeçalho de coluna tem tooltip acessível.
 *
 * PROPOSITO DE NEGOCIO: em 22/10/2026 as ~1.400 dicas do site passaram para o tooltip canonico (aria-describedby
 * permanente, foco, toque, Esc), mas ainda ha elementos do escopo sem texto de dica nenhum — sobretudo titulos e
 * cabecalhos de coluna do conteudo didatico. Escrever esse texto e trabalho de conteudo. Ate la, esta catraca
 * impede que a divida CRESCA: elemento novo sem dica reprova; o que ja estava na linha de base passa.
 *
 * INVARIANTES:
 *  - o instrumento e a varredura do componente canonico (scripts/dica-varredura.js, copia sem edicao);
 *  - a linha de base guarda IDENTIDADE (pagina + etiqueta + problema + texto do elemento), nunca so contagem (A4);
 *  - divida que some (resolvida) e listada, para a linha de base ser regravada com --gravar e so descer;
 *  - o Google Tradutor fica bloqueado (carga em volume manda a rede para o captcha).
 *
 * COMPORTAMENTO EM CASO DE FALHA: sai 2 (NAO VERIFICOU) quando o servidor nao responde, o inventario nao existe ou a
 * calibracao falha (o instrumento nao acusou o botao sem dica injetado, ou acusou o botao com dica canonica); sai 1
 * quando ha elemento NOVO sem dica; sai 0 quando nada piorou.
 *
 * USO:  node scripts/verificar-dicas.mjs [http://localhost:8081] [--gravar]
 */
import { chromium } from 'playwright';
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const AQUI = dirname(fileURLToPath(import.meta.url));
const BASE = process.argv.slice(2).find((a) => a.startsWith('http')) || 'http://localhost:8081';
const GRAVAR = process.argv.includes('--gravar');
const LINHA_DE_BASE = join(AQUI, 'dicas-linha-base.json');
const VARREDURA = readFileSync(join(AQUI, 'dica-varredura.js'), 'utf8').replace(/if \(typeof module[\s\S]*$/, '');
const GOOGLE = /^https?:\/\/([a-z0-9-]+\.)*(google\.com|googleapis\.com|gstatic\.com)\//i;

if (!existsSync(join(AQUI, 'inventario.json'))) {
  console.log('NAO VERIFICOU: scripts/inventario.json ausente (rode node scripts/inventario.mjs)');
  process.exit(2);
}
const paginas = JSON.parse(readFileSync(join(AQUI, 'inventario.json'), 'utf8')).paginas;

const navegador = await chromium.launch();
async function abrir(rota) {
  const contexto = await navegador.newContext({ viewport: { width: 1440, height: 900 } });
  await contexto.route(GOOGLE, (r) => r.abort());
  const pagina = await contexto.newPage();
  const resp = await pagina.goto(BASE + rota, { waitUntil: 'load', timeout: 45000 });
  await pagina.waitForTimeout(400);
  return { contexto, pagina, status: resp ? resp.status() : 0 };
}
const varrer = (pagina) => pagina.evaluate((src) => {
  eval(src); // eslint-disable-line no-eval
  // Numero no texto do elemento vira "#": a Academia sorteia valores a cada carga, e a identidade nao pode mudar
  // com o sorteio (senao o mesmo elemento conta como "novo" e "resolvido" ao mesmo tempo).
  return varreduraDica().map((f) => `${f.tag || '-'}|${f.problema.replace(/\d+/g, '#')}|${(f.elemento || '').replace(/\s+/g, ' ').replace(/\d+/g, '#').trim()}`);
}, VARREDURA);

// Calibracao (A1): o botao sem dica injetado TEM de aparecer; o botao com a marcacao canonica nao.
{
  const { contexto, pagina, status } = await abrir('/');
  if (status !== 200) { console.log('NAO VERIFICOU: / respondeu ' + status); process.exit(2); }
  await pagina.evaluate(() => {
    const ruim = document.createElement('button'); ruim.textContent = 'CalibracaoSemDica'; document.body.appendChild(ruim);
    const bom = document.createElement('span'); bom.className = 'dica';
    bom.innerHTML = '<button type="button" aria-describedby="d-calib">CalibracaoComDica</button>'
      + '<span id="d-calib" role="tooltip" class="dica__texto">Explica o que o botao de calibracao faz.</span>';
    document.body.appendChild(bom);
  });
  const achados = await varrer(pagina);
  await contexto.close();
  const pegouRuim = achados.some((a) => a.includes('CalibracaoSemDica'));
  const poupouBom = !achados.some((a) => a.includes('CalibracaoComDica'));
  if (!pegouRuim || !poupouBom) {
    console.log(`NAO VERIFICOU: calibracao falhou (acusou o sem dica=${pegouRuim}, poupou o com dica=${poupouBom})`);
    process.exit(2);
  }
  console.log('calibracao: botao sem dica acusado, botao com dica canonica aceito');
}

/**
 * Identidade de cada elemento sem dica. Nas licoes da Academia o exercicio e sorteado a cada carga (outro rotulo,
 * outro valor): ali a identidade e a CLASSE — etiqueta + problema + ordinal — e a catraca compara quantos de cada
 * classe ha (A4: ocorrencia OU classe rastreavel). No resto do site, o texto do elemento identifica.
 */
function identidades(rota, lista) {
  if (!rota.startsWith('/academia/')) {
    return [...new Set(lista)].sort();
  }
  const porClasse = {};
  return lista.map((x) => {
    const classe = x.split('|').slice(0, 2).join('|');
    porClasse[classe] = (porClasse[classe] || 0) + 1;
    return `${classe}|#${porClasse[classe]}`;
  }).sort();
}

const atual = {};
let naoAbriram = 0;
for (const rota of paginas) {
  try {
    const { contexto, pagina, status } = await abrir(rota);
    if (status !== 200) { naoAbriram++; console.log(`  ${rota} NAO VERIFICOU (status ${status})`); }
    else { atual[rota] = identidades(rota, await varrer(pagina)); }
    await contexto.close();
  } catch (e) {
    naoAbriram++;
    console.log(`  ${rota} NAO VERIFICOU (${String(e).slice(0, 80)})`);
  }
}
await navegador.close();

const total = Object.values(atual).reduce((n, l) => n + l.length, 0);
if (GRAVAR) {
  writeFileSync(LINHA_DE_BASE, JSON.stringify(atual, null, 1) + '\n');
  console.log(`linha de base gravada: ${total} elemento(s) sem dica em ${Object.keys(atual).length} pagina(s)`);
  process.exit(naoAbriram ? 2 : 0);
}
const base = existsSync(LINHA_DE_BASE) ? JSON.parse(readFileSync(LINHA_DE_BASE, 'utf8')) : {};
let novos = 0;
let resolvidos = 0;
for (const [rota, lista] of Object.entries(atual)) {
  const antes = new Set(base[rota] || []);
  const agora = new Set(lista);
  const n = lista.filter((x) => !antes.has(x));
  const r = [...antes].filter((x) => !agora.has(x));
  novos += n.length;
  resolvidos += r.length;
  if (n.length) { console.log(`  ${rota}: ${n.length} NOVO(S) sem dica — ${n.slice(0, 4).join(' ; ')}`); }
}
console.log(`\nsem dica: ${total} (novos ${novos}, resolvidos desde a linha de base ${resolvidos}, paginas nao verificadas ${naoAbriram})`);
if (novos > 0) { process.exit(1); }
if (naoAbriram > 0) { console.log('NAO VERIFICOU: houve pagina que nao abriu — isso nao e aprovacao.'); process.exit(2); }
if (resolvidos > 0) { console.log('divida desceu: regrave a linha de base com --gravar para a catraca acompanhar.'); }
process.exit(0);
