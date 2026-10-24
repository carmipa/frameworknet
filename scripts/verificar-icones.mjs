/**
 * Catraca dos icones: todo rotulo da interface tem icone junto, e todo icone desenha o glifo (nunca o nome dele).
 *
 * PROPOSITO DE NEGOCIO: o icone ao lado do rotulo reforca o significado — coluna, campo, titulo de area, botao, item
 * de menu, aba, legenda de tabela, bloco expansivel, KPI. Sem ele a tela fica irregular (uma coluna com icone, a
 * vizinha sem), e com a fonte de icones ausente ou com um nome de glifo que ela nao conhece, o navegador escreve o
 * NOME ("calendar_month") como palavra no meio do rotulo. Esta catraca mede o site inteiro, no HTML que a pessoa
 * recebe (inclusive o que o JavaScript monta), e reprova os dois defeitos.
 *
 * UNIVERSO: o mesmo percurso da catraca das dicas (scripts/site-percurso.mjs) — cada pagina do inventario e as
 * variantes, no estado inicial, em cada aba e no painel de resultado de um calculo realista.
 *
 * INVARIANTES:
 *  - o criterio de "rotulo sem icone" e o da varredura do componente compartilhado (scripts/icone-varredura.js, copia
 *    sem edicao): esta catraca so decide o universo e soma; nao reimplementa o criterio;
 *  - data-icone-excecao="motivo" tira o elemento da contagem, e o motivo nao pode ser vazio;
 *  - glifo que nao desenha (largura acima de 1,6 vez o tamanho da fonte: o navegador escreveu o nome) e defeito —
 *    medido com todo pedido para fora do servidor abortado, entao a fonte que desenha e a servida pelo proprio site;
 *  - um elemento pertence a UM estado so: o primeiro em que ficou visivel.
 *
 * COMPORTAMENTO EM CASO DE FALHA: sai 2 (NAO VERIFICOU) quando o servidor nao responde, o inventario nao existe, uma
 * pagina ou estado nao pode ser medido, ou a calibracao falha — o instrumento tem de separar, na fronteira, o rotulo
 * sem icone do mesmo rotulo com icone, o "?" da dica do icone, a excecao com e sem motivo, o glifo que existe do nome
 * que nao existe; sai 1 quando ha rotulo sem icone, excecao sem motivo ou glifo que nao desenha; sai 0 sem nenhum.
 *
 * USO:  node scripts/verificar-icones.mjs [http://localhost:8081] [--so=/rota1,/rota2] [--json=saida.json]
 */
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { NAO_MEDIDOS, criarPercurso } from './site-percurso.mjs';

const AQUI = dirname(fileURLToPath(import.meta.url));
const ARGS = process.argv.slice(2);
const BASE = ARGS.find((a) => a.startsWith('http')) || 'http://localhost:8081';
const ORIGEM = new URL(BASE).origin;
const SO = (ARGS.find((a) => a.startsWith('--so=')) || '').slice(5).split(',').filter(Boolean);
const SAIDA_JSON = (ARGS.find((a) => a.startsWith('--json=')) || '').slice(7);
const CHAVE = process.env.FRAMEWORK_CHAVE_LOCAL || '';
const VARREDURA = readFileSync(join(AQUI, 'icone-varredura.js'), 'utf8').replace(/if \(typeof module[\s\S]*$/, '');
// O escopo (que elemento e rotulo) e lido da propria varredura, para a contagem nunca divergir do criterio.
const ESCOPO = (VARREDURA.match(/const SEL_ESCOPO = '([^']+)'/) || [])[1];
if (!ESCOPO) { console.log('NAO VERIFICOU: escopo da varredura de icones nao encontrado'); process.exit(2); }

if (!existsSync(join(AQUI, 'inventario.json'))) {
  console.log('NAO VERIFICOU: scripts/inventario.json ausente (rode node scripts/inventario.mjs)');
  process.exit(2);
}
const inventario = JSON.parse(readFileSync(join(AQUI, 'inventario.json'), 'utf8'));
const todas = [...inventario.paginas, ...(inventario.variantes || []), '/login/?modo=contingencia'];
const paginas = SO.length ? todas.filter((r) => SO.includes(r)) : todas;

/**
 * Mede o que esta visivel e ainda nao foi atribuido a estado nenhum (window.__icVistos guarda os elementos ja
 * medidos, por referencia). Devolve "sem-icone|tipo|nome", "excecao-sem-motivo|nome", "glifo-nao-desenha|nome" e uma
 * linha "ok|rotulos|excecoes|editoriais" com a contagem do estado.
 */
const varrer = (pagina) => pagina.evaluate(async ({ src, escopo }) => {
  eval(src); // eslint-disable-line no-eval
  await document.fonts.ready;
  const vistos = window.__icVistos || (window.__icVistos = new Set());
  const visivel = (el) => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
  const novos = (s) => [...document.querySelectorAll(s)].filter((el) => !vistos.has(el) && visivel(el));
  const raiz = { querySelectorAll: novos };
  const limpo = (t) => (t || '').replace(/\s+/g, ' ').trim();
  const achados = varreduraIcone(raiz).map((f) => `sem-icone|${f.tipo}|${limpo(f.elemento)}`);
  let exc = 0; let edi = 0;
  for (const a of avisosIcone(raiz)) {
    if (a.problema.startsWith('exceção declarada')) {
      exc++;
      if (!limpo(a.problema.slice('exceção declarada:'.length))) { achados.push(`excecao-sem-motivo|${limpo(a.elemento)}`); }
    } else { edi++; }
  }
  const rotulos = novos(escopo).length;
  // O glifo e medido numa copia solta (sem largura, preenchimento ou flex do lugar onde ele esta): desenhado, ocupa
  // cerca de 1 em; com o nome escrito por extenso, varias vezes isso.
  const largura = new Map();
  const mede = (nome) => {
    if (!largura.has(nome)) {
      const s = document.createElement('span');
      s.className = 'material-symbols-outlined';
      s.style.cssText = 'position:absolute;visibility:hidden;font-size:20px;padding:0;margin:0;width:auto;display:inline-block';
      s.textContent = nome;
      document.body.appendChild(s);
      largura.set(nome, s.getBoundingClientRect().width);
      s.remove();
    }
    return largura.get(nome);
  };
  for (const g of novos('.material-symbols-outlined')) {
    const nome = limpo(g.textContent);
    if (nome && mede(nome) > 20 * 1.6) { achados.push(`glifo-nao-desenha|${nome}`); }
  }
  novos(escopo + ', [data-icone-excecao], .material-symbols-outlined').forEach((el) => vistos.add(el));
  achados.push(`ok|${rotulos}|${exc}|${edi}`);
  return achados;
}, { src: VARREDURA, escopo: ESCOPO });

let percurso;
try {
  percurso = await criarPercurso({ base: BASE, chave: CHAVE, varrer, marcaAba: 'data-icone-aba-vista' });
} catch (e) {
  console.log('NAO VERIFICOU: ' + String(e).split('\n')[0]);
  process.exit(2);
}
const { navegador, abrir, medirRota } = percurso;

// Calibracao na fronteira: pares com o MESMO sinal, um que tem de ser acusado e outro que tem de passar.
{
  const { contexto, pagina, status } = await abrir('/sobre');
  if (status !== 200) { console.log('NAO VERIFICOU: /sobre respondeu ' + status); process.exit(2); }
  await pagina.evaluate(() => {
    const g = (nome) => `<span class="material-symbols-outlined" aria-hidden="true" translate="no">${nome}</span>`;
    const d = document.createElement('div');
    d.innerHTML = '<table><tr><th>CalibSem</th>'
      + `<th>CalibDica <span class="dica"><button type="button" class="dica__gatilho" aria-label="x">${g('help')}</button></span></th>`
      + `<th>${g('lan')} CalibCom</th></tr></table>`
      + `<article><div>${g('layers')}</div><div><h3>CalibCartao</h3></div></article>`
      + '<h2 data-titulo-editorial>CalibEditorial</h2>'
      + '<button type="button" data-icone-excecao="valor do dado">CalibExcecao</button>'
      + '<button type="button" data-icone-excecao=" ">CalibSemMotivo</button>'
      + `<p>${g('home')} ${g('nome_que_nao_existe_xyz')}</p>`;
    document.querySelector('main').appendChild(d);
  });
  const achados = await varrer(pagina);
  await contexto.close();
  const esperado = {
    'sem-icone|th|CalibSem': true, 'sem-icone|th|CalibDica': true, 'sem-icone|th|CalibCom': false,
    'sem-icone|h3|CalibCartao': false, 'sem-icone|h2|CalibEditorial': false, 'sem-icone|button|CalibExcecao': false,
    'excecao-sem-motivo|CalibSemMotivo': true, 'glifo-nao-desenha|home': false, 'glifo-nao-desenha|nome_que_nao_existe_xyz': true,
  };
  const errados = Object.entries(esperado).filter(([k, v]) => achados.includes(k) !== v).map(([k]) => k);
  if (errados.length) {
    console.log('NAO VERIFICOU: calibracao falhou em ' + errados.join(' ; '));
    process.exit(2);
  }
  console.log('calibracao: th sem icone e so com o "?" acusados, th com icone e titulo de cartao aceitos, editorial e '
    + 'excecao fora da contagem, excecao sem motivo acusada, glifo que existe aceito e nome que nao existe acusado');
}

const resultado = {};
const contagem = {};
const falhasDeEstado = {};
let naoAbriram = 0;
const alvos = paginas.map((r) => [r, false]);
if (CHAVE && (!SO.length || SO.includes('/telemetria'))) { alvos.push(['/telemetria', true]); }
for (const [rota, comSessao] of alvos) {
  const chave = comSessao ? rota + ' (logado)' : rota;
  try {
    const r = await medirRota(rota, null, comSessao);
    if (r.status !== 200) { naoAbriram++; console.log(`  ${chave} NAO VERIFICOU (status ${r.status})`); continue; }
    const c = { rotulos: 0, excecoes: 0, editoriais: 0 };
    for (const a of r.achados.filter((x) => x.split('|')[1] === 'ok')) {
      const [, , n, e, d] = a.split('|');
      c.rotulos += +n; c.excecoes += +e; c.editoriais += +d;
    }
    contagem[chave] = c;
    resultado[chave] = r.achados.filter((x) => x.split('|')[1] !== 'ok');
    if (r.falhas.length) {
      falhasDeEstado[chave] = r.falhas;
      console.log(`  ${chave} estado(s) NAO VERIFICADO(S): ${r.falhas.join(' ; ')}`);
    }
    for (const a of resultado[chave]) { console.log(`  ${chave}: ${a}`); }
  } catch (e) {
    naoAbriram++;
    console.log(`  ${chave} NAO VERIFICOU (${String(e).slice(0, 80)})`);
  }
}
await navegador.close();

if (SAIDA_JSON) { writeFileSync(SAIDA_JSON, JSON.stringify({ resultado, contagem, falhasDeEstado }, null, 1) + '\n'); }
for (const [rota, estados] of Object.entries(NAO_MEDIDOS).filter(([r]) => !SO.length || SO.includes(r))) {
  for (const [estado, motivo] of Object.entries(estados)) { console.log(`  nao medido: ${rota} ${estado} — ${motivo}`); }
}
if (percurso.externos()) { console.log(`  pedidos do navegador para fora de ${ORIGEM} abortados: ${percurso.externos()}`); }
const todos = Object.values(resultado).flat();
const soma = (k) => Object.values(contagem).reduce((n, c) => n + c[k], 0);
const conta = (t) => todos.filter((a) => a.split('|')[1] === t).length;
const nSem = conta('sem-icone'); const nMotivo = conta('excecao-sem-motivo'); const nGlifo = conta('glifo-nao-desenha');
const estadosFalhos = Object.values(falhasDeEstado).reduce((n, l) => n + l.length, 0);
console.log(`\nicones: ${Object.keys(resultado).length} pagina(s) | rotulos ${soma('rotulos')} | sem icone ${nSem} | `
  + `excecoes ${soma('excecoes')} (sem motivo ${nMotivo}) | editoriais ${soma('editoriais')} | glifos que nao desenham ${nGlifo} | `
  + `paginas nao verificadas ${naoAbriram} | estados nao verificados ${estadosFalhos}`);
if (nSem + nMotivo + nGlifo > 0) { process.exit(1); }
if (naoAbriram > 0 || estadosFalhos > 0) { console.log('NAO VERIFICOU: houve pagina ou estado que nao abriu — isso nao e aprovacao.'); process.exit(2); }
process.exit(0);
