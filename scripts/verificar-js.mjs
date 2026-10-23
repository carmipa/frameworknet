/**
 * Guarda de sintaxe dos arquivos JavaScript servidos pelo site.
 *
 * PROPOSITO: um erro de sintaxe num .js nao aparece em teste de backend, nao
 * quebra o build, nao muda status HTTP e nao aparece em log de servidor. O
 * navegador simplesmente para de executar o arquivo INTEIRO — e a pagina fica
 * "morta" sem nenhum sinal. Foi o que aconteceu com o dashboard da telemetria:
 * uma string com quebra de linha nao escapada derrubou graficos, KPIs e tabelas
 * de uma vez, e o unico sintoma foi "parou de funcionar".
 *
 * INVARIANTE: todo .js entregue ao navegador precisa ser analisavel. Arquivo
 * minificado de terceiro (*.min.js) fica de fora — nao e nosso e ja vem testado.
 *
 * COMPORTAMENTO EM CASO DE FALHA: sai com codigo 1 listando arquivo, linha e
 * mensagem. Falha fechada: nao conseguir analisar tambem reprova.
 *
 * USO:  node scripts/verificar-js.mjs
 */
import { readdirSync, statSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { join, relative } from 'node:path';

const RAIZ = 'src/main/resources/META-INF/resources';

function arquivosJs(dir) {
  const achados = [];
  for (const nome of readdirSync(dir)) {
    const caminho = join(dir, nome);
    if (statSync(caminho).isDirectory()) {
      achados.push(...arquivosJs(caminho));
    } else if (nome.endsWith('.js') && !nome.endsWith('.min.js')) {
      achados.push(caminho);
    }
  }
  return achados;
}

const arquivos = arquivosJs(RAIZ).sort();
let problemas = 0;

for (const arquivo of arquivos) {
  const curto = relative(RAIZ, arquivo).replace(/\\/g, '/');
  try {
    execFileSync(process.execPath, ['--check', arquivo], { stdio: 'pipe' });
    console.log(`  ok     ${curto}`);
  } catch (erro) {
    problemas++;
    const saida = String(erro.stderr || erro.message).split('\n').slice(0, 3).join('\n         ');
    console.log(`  FALHA  ${curto}\n         ${saida}`);
  }
}

console.log(
  problemas === 0
    ? `\n${arquivos.length} arquivo(s) JavaScript sem erro de sintaxe.`
    : `\n${problemas} arquivo(s) com erro de sintaxe — a pagina que os carrega esta quebrada.`,
);
process.exit(problemas === 0 ? 0 : 1);
