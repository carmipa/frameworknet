/**
 * Gera o globo 3D local da página de Localização (auditoria SEC-05).
 *
 * PROPÓSITO DE NEGÓCIO: o globo vinha do esm.sh (código montado na hora por terceiro, sem como
 *   conferir integridade) e as texturas do jsdelivr. Aqui ele vira um arquivo do próprio site, gerado
 *   das versões travadas no package-lock.json, com as licenças ao lado.
 * INVARIANTES: uma instância só do three no pacote (o globo e as nuvens usam a mesma); versões exatas
 *   do package.json/package-lock.json; a saída é sobrescrita inteira a cada geração (sem resto velho).
 * FALHA: dependência ausente (faltou `npm ci`) ou erro do esbuild encerra com código 1 sem gravar o
 *   pacote; a cópia das texturas falha alto se o arquivo de origem sumir.
 *
 * Uso:  cd scripts/vendor-globo && npm ci && node construir.mjs
 */
import { build } from 'esbuild';
import sharp from 'sharp';
import { createHash } from 'node:crypto';
import { copyFileSync, mkdirSync, readFileSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
const destino = join(aqui, '..', '..', 'src', 'main', 'resources', 'META-INF', 'resources', 'localizacao', 'vendor');
const nm = join(aqui, 'node_modules');

rmSync(destino, { recursive: true, force: true });
mkdirSync(join(destino, 'texturas'), { recursive: true });
mkdirSync(join(destino, 'licencas'), { recursive: true });

await build({
  entryPoints: [join(aqui, 'entrada.mjs')],
  bundle: true,
  format: 'esm',
  minify: true,
  target: 'es2020',
  legalComments: 'eof',
  outfile: join(destino, 'globo-three.mjs'),
  logLevel: 'warning',
});

// Texturas em WebP (as nuvens tinham 5 MB em PNG): a transparência das nuvens se mantém.
const texturas = {
  'earth-blue-marble.webp': ['three-globe/example/img/earth-blue-marble.jpg', { quality: 82 }],
  'earth-topology.webp': ['three-globe/example/img/earth-topology.png', { lossless: true }],
  // Camada decorativa a 2048 px (a original tem 4096): a 55% de opacidade a diferença não aparece.
  'clouds.webp': ['three-globe/example/clouds/clouds.png', { quality: 75, alphaQuality: 80 }, 2048],
};
for (const [nome, [origem, opcoes, largura]] of Object.entries(texturas)) {
  const imagem = sharp(join(nm, origem));
  if (largura) imagem.resize({ width: largura });
  await imagem.webp(opcoes).toFile(join(destino, 'texturas', nome));
}

// Toda dependência que entrou no pacote leva a licença junto (todas MIT ou equivalente).
const lock = JSON.parse(readFileSync(join(aqui, 'package-lock.json'), 'utf8'));
const licencas = [];
for (const [caminho, info] of Object.entries(lock.packages)) {
  if (!caminho.startsWith('node_modules/') || info.dev) continue;
  const nome = caminho.slice('node_modules/'.length);
  try {
    copyFileSync(join(aqui, caminho, 'LICENSE'), join(destino, 'licencas', nome.replace('/', '__') + '.LICENSE'));
  } catch {
    // Sem arquivo LICENSE no pacote: a licença declarada fica registrada na lista.
  }
  licencas.push(`${nome}@${info.version} — ${info.license || 'sem licença declarada'}`);
}

const sha = (p) => createHash('sha256').update(readFileSync(p)).digest('hex');
console.log('globo-three.mjs', sha(join(destino, 'globo-three.mjs')));
for (const nome of Object.keys(texturas)) console.log(nome, sha(join(destino, 'texturas', nome)));
console.log('\nDependências embutidas:\n  ' + licencas.join('\n  '));
