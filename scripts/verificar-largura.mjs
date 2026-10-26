/**
 * Catraca da largura: nenhuma tela rola para o lado, em nenhum estado — inclusive depois de calcular.
 *
 * PROPOSITO DE NEGOCIO: a pessoa no celular usa a calculadora, aperta Executar e a pagina passa a rolar para o lado,
 * porque uma tabela do resultado ficou mais larga que a tela. A medicao de largura por pagina so via o estado inicial e
 * as abas; os paineis de resultado ficavam de fora. Esta catraca usa o mesmo percurso das catracas de dicas e icones
 * (pagina, abas e um calculo realista em cada painel de resultado) e mede, em cada estado, a rolagem lateral da pagina e
 * os elementos que passam da janela sem um ancestral que role ou corte.
 *
 * INVARIANTES:
 *  - larguras medidas: 320, 390 e 1366 px; rolagem lateral tem de ser 0 e nenhum elemento pode passar da janela;
 *  - elemento dentro de quem rola ou corta (tabela em .table-responsive, bloco de codigo) nao conta: a rolagem e dele;
 *  - balao de dica, elemento fixo e conteudo so para leitor de tela nao contam;
 *  - o Google Tradutor fica bloqueado e todo pedido para fora do servidor medido e abortado (percurso comum).
 *
 * COMPORTAMENTO EM CASO DE FALHA: sai 2 (NAO VERIFICOU) quando o servidor nao responde, o inventario nao existe, uma
 * pagina ou estado nao abre, ou a calibracao falha — um bloco de 150% injetado TEM de ser acusado e a mesma pagina sem
 * ele nao pode ter achado; sai 1 com rolagem ou vazamento em qualquer estado; sai 0 sem nenhum.
 *
 * USO:  node scripts/verificar-largura.mjs [http://localhost:8081] [--so=/rota1,/rota2]
 */
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { NAO_MEDIDOS, criarPercurso } from './site-percurso.mjs';

const AQUI = dirname(fileURLToPath(import.meta.url));
const ARGS = process.argv.slice(2);
const BASE = ARGS.find((a) => a.startsWith('http')) || 'http://localhost:8081';
const SO = (ARGS.find((a) => a.startsWith('--so=')) || '').slice(5).split(',').filter(Boolean);
const CHAVE = process.env.FRAMEWORK_CHAVE_LOCAL || '';
const LARGURAS = [320, 390, 1366];

if (!existsSync(join(AQUI, 'inventario.json'))) {
  console.log('NAO VERIFICOU: scripts/inventario.json ausente (rode node scripts/inventario.mjs)');
  process.exit(2);
}
const inventario = JSON.parse(readFileSync(join(AQUI, 'inventario.json'), 'utf8'));
const todas = [...inventario.paginas, ...(inventario.variantes || []), '/login/?modo=contingencia'];
const paginas = SO.length ? todas.filter((r) => SO.includes(r)) : todas;

/** Rolagem lateral e elementos fora da janela, no estado atual da pagina. */
const medida = () => {
  const de = document.scrollingElement || document.documentElement;
  const fora = [];
  for (const el of document.querySelectorAll('body *')) {
    const r = el.getBoundingClientRect();
    if (!r.width || !r.height || getComputedStyle(el).position === 'fixed'
      || el.closest('.dica__texto, [role=tooltip], .visually-hidden, .visually-hidden-focusable')) { continue; }
    if (r.right > de.clientWidth + 1 || r.left < -1) {
      let p = el.parentElement;
      let cortado = false;
      while (p && p !== document.body) {
        if (/(auto|scroll|hidden|clip)/.test(getComputedStyle(p).overflowX)) { cortado = true; break; }
        p = p.parentElement;
      }
      if (!cortado) { fora.push(`${el.tagName.toLowerCase()}${el.classList[0] ? '.' + el.classList[0] : ''}`); }
    }
  }
  return { rolagem: Math.max(0, de.scrollWidth - de.clientWidth), fora: [...new Set(fora)].slice(0, 4) };
};

const varrer = async (pagina) => {
  const achados = [];
  for (const w of LARGURAS) {
    await pagina.setViewportSize({ width: w, height: 900 });
    await pagina.waitForTimeout(250);
    const m = await pagina.evaluate(medida);
    if (m.rolagem || m.fora.length) { achados.push(`${w}px|rolagem ${m.rolagem}|${m.fora.join(' ')}`); }
  }
  await pagina.setViewportSize({ width: 1440, height: 900 });
  await pagina.waitForTimeout(150);
  return achados;
};

let percurso;
try {
  percurso = await criarPercurso({ base: BASE, chave: CHAVE, varrer, marcaAba: 'data-largura-aba-vista' });
} catch (e) {
  console.log('NAO VERIFICOU: ' + String(e).split('\n')[0]);
  process.exit(2);
}
const { navegador, abrir, medirRota } = percurso;

// Calibracao (A1): a mesma pagina, sem e com um bloco de 150% de largura.
{
  const { contexto, pagina, status } = await abrir('/sobre');
  if (status !== 200) { console.log('NAO VERIFICOU: /sobre respondeu ' + status); process.exit(2); }
  const limpa = await varrer(pagina);
  await pagina.evaluate(() => {
    const d = document.createElement('div');
    d.className = 'calibracao-largura';
    d.style.cssText = 'width:150%;height:3rem';
    document.querySelector('main').appendChild(d);
  });
  const doente = await varrer(pagina);
  await contexto.close();
  if (limpa.length || !doente.some((a) => a.includes('calibracao-largura'))) {
    console.log(`NAO VERIFICOU: calibracao falhou (pagina limpa com achado=${limpa.length}, bloco de 150% acusado=${doente.length})`);
    process.exit(2);
  }
  console.log('calibracao: bloco de 150% acusado; a mesma pagina sem ele, sem achado');
}

let achadosTotais = 0;
let naoAbriram = 0;
let estadosFalhos = 0;
const alvos = paginas.map((r) => [r, false]);
if (CHAVE && (!SO.length || SO.includes('/telemetria'))) { alvos.push(['/telemetria', true]); }
for (const [rota, comSessao] of alvos) {
  const chave = comSessao ? rota + ' (logado)' : rota;
  try {
    const r = await medirRota(rota, null, comSessao);
    if (r.status !== 200) { naoAbriram++; console.log(`  ${chave} NAO VERIFICOU (status ${r.status})`); continue; }
    if (r.falhas.length) { estadosFalhos += r.falhas.length; console.log(`  ${chave} estado(s) NAO VERIFICADO(S): ${r.falhas.join(' ; ')}`); }
    for (const a of r.achados) { achadosTotais++; console.log(`  ${chave}: ${a}`); }
  } catch (e) {
    naoAbriram++;
    console.log(`  ${chave} NAO VERIFICOU (${String(e).slice(0, 80)})`);
  }
}
await navegador.close();
for (const [rota, estados] of Object.entries(NAO_MEDIDOS).filter(([r]) => !SO.length || SO.includes(r))) {
  for (const [estado, motivo] of Object.entries(estados)) { console.log(`  nao medido: ${rota} ${estado} — ${motivo}`); }
}
console.log(`\nlargura: ${alvos.length - naoAbriram} pagina(s) em ${LARGURAS.join('/')} px | achados ${achadosTotais} | `
  + `paginas nao verificadas ${naoAbriram} | estados nao verificados ${estadosFalhos}`);
if (achadosTotais > 0) { process.exit(1); }
if (naoAbriram > 0 || estadosFalhos > 0) { console.log('NAO VERIFICOU: houve pagina ou estado que nao abriu — isso nao e aprovacao.'); process.exit(2); }
process.exit(0);
