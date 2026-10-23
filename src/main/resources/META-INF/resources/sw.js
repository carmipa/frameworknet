/*
 * Service worker do Framework de Redes.
 *
 * PROPOSITO DE NEGOCIO
 *     Existe para tornar o site instalavel (o botao na barra de endereco e o
 *     atalho na area de trabalho) e para dar uma tela decente quando a conexao
 *     cai. NAO existe para acelerar o site.
 *
 * INVARIANTES DO DOMINIO
 *     - Estrategia NETWORK-FIRST para navegacao e para os estaticos do proprio
 *       site. Isto e deliberado: a aplicacao recebe deploy com frequencia, e um
 *       service worker cache-first serviria a versao anterior ate o usuario
 *       limpar o cache a mao. Cache aqui e rede de seguranca, nunca fonte
 *       primaria.
 *     - So intercepta GET de MESMA ORIGEM. Requisicao a CDN, a API externa ou
 *       com qualquer outro metodo passa direto, sem o service worker no meio.
 *     - NUNCA cacheia rota autenticada, dado de telemetria nem pagina com dado
 *       da sessao/visitante: NUNCA_CACHEAR (prefixos) e NUNCA_CACHEAR_TRECHOS
 *       (toda /api/ e todo export de modulo) ficam de fora, porque o cache do
 *       browser sobrevive ao logout e ao fechar a janela e guardaria no disco
 *       conteudo protegido ou o historico/IP de quem usou o PC antes.
 *     - Cada versao usa um nome de cache proprio; caches antigos sao apagados no
 *       activate, entao o deploy nao deixa lixo acumulado.
 *
 * COMPORTAMENTO EM CASO DE FALHA
 *     Rede indisponivel devolve a copia em cache; sem copia, devolve a pagina
 *     /offline.html para navegacao e um erro 503 sintetico para o resto. Nenhuma
 *     falha do service worker impede a aplicacao de funcionar online.
 */
/* v3: expurga caches anteriores, que guardavam export/API e páginas com dado pessoal da sessão.
   v4: a casca offline passa a levar o offline.css (a página offline aparecia sem estilo, FRONT-14).
   v5: CSS em largura cheia e rem (Regra 27) e Bootstrap/mermaid/Chart.js/Leaflet servidos pelo próprio site —
       mesma origem, então entram no cache na primeira visita e o menu funciona offline.
   v6: a página offline passa a sair do template das páginas de erro (mesmo visual de todas elas);
       a casca leva o CSS, a fonte, o script e as bandeiras que ela usa, e o offline.css sai. */
const VERSAO = 'framework-net-v6';
const CACHE_ESTATICO = `${VERSAO}-estatico`;
const PAGINA_OFFLINE = '/offline.html';

/* Casca minima: so o que faz a tela offline existir por si so. */
const PRE_CACHE = [
  PAGINA_OFFLINE,
  '/web/css/app.css',
  '/web/css/aed-command-center.css',
  '/pwa/icone-192.png',
  // O que a página offline (gerada do template das páginas de erro) carrega do próprio site.
  '/icone.png?v=20260710c',
  '/paginaErros/css/erro.css?v=20261023',
  '/paginaErros/js/erro-matrix.js?v=20260810',
  '/web/js/i18n-translate.js?v=20261002',
  '/web/img/bandeiras/br.png?v=20261001',
  '/web/img/bandeiras/us.png?v=20261001',
  '/web/img/bandeiras/es.png?v=20261001',
  // Fonte de ícones local: sem ela, offline, o glifo aparece como a palavra do nome.
  '/web/css/material-symbols.css?v=20261022',
  '/web/fonts/material-symbols-outlined.woff2?v=20260925',
];

/* Prefixos que nunca entram no cache — conteudo autenticado ou volatil. */
// /analise renderiza o histórico da SESSÃO e /informacoes o IP/geo do visitante: no Cache Storage
// eles sobreviveriam ao fechar o navegador num PC compartilhado de laboratório.
const NUNCA_CACHEAR = ['/telemetria', '/admin', '/login', '/export', '/history', '/health',
  '/analise', '/informacoes'];

/* Trechos em QUALQUER posicao do caminho: toda API e todo export de modulo (ex.: /ipv6/export,
   /localizacao/api/inspecao com IP e cabecalhos). Regra por familia, nao lista de rotas: rota nova
   ja nasce fora do cache. O Cache Storage sobrevive no disco do PC de laboratorio. */
const NUNCA_CACHEAR_TRECHOS = ['/api/', '/export'];

self.addEventListener('install', (evento) => {
  evento.waitUntil(
    caches
      .open(CACHE_ESTATICO)
      .then((cache) => cache.addAll(PRE_CACHE))
      // Falha de pre-cache nao pode impedir a instalacao: o site funciona online
      // de qualquer jeito, e sem instalar nao ha nem opcao de atalho.
      .catch(() => undefined)
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (evento) => {
  evento.waitUntil(
    caches
      .keys()
      .then((nomes) =>
        Promise.all(
          nomes
            .filter((nome) => nome.startsWith('framework-net-') && !nome.startsWith(VERSAO))
            .map((nome) => caches.delete(nome)),
        ),
      )
      .then(() => self.clients.claim()),
  );
});

function ehProtegida(url) {
  return NUNCA_CACHEAR.some((prefixo) => url.pathname.startsWith(prefixo))
    || NUNCA_CACHEAR_TRECHOS.some((trecho) => url.pathname.includes(trecho));
}

self.addEventListener('fetch', (evento) => {
  const req = evento.request;
  if (req.method !== 'GET') {
    return;
  }

  const url = new URL(req.url);
  if (url.origin !== self.location.origin || ehProtegida(url)) {
    return;
  }

  evento.respondWith(
    fetch(req)
      .then((resposta) => {
        // Só guarda resposta completa e bem-sucedida: cachear 206/opaque
        // devolveria pagina truncada depois, offline.
        if (resposta && resposta.status === 200 && resposta.type === 'basic') {
          const copia = resposta.clone();
          caches.open(CACHE_ESTATICO).then((cache) => cache.put(req, copia)).catch(() => undefined);
        }
        return resposta;
      })
      .catch(() =>
        caches.match(req).then((emCache) => {
          if (emCache) {
            return emCache;
          }
          if (req.mode === 'navigate') {
            return caches.match(PAGINA_OFFLINE);
          }
          return new Response('', { status: 503, statusText: 'Offline' });
        }),
      ),
  );
});
