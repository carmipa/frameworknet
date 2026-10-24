/* dica-varredura.js v1.3 (2026-10-24) — componente compartilhado: não editar no projeto; mudança só na origem.
   Tooltip só ONDE HÁ O QUE EXPLICAR: o que o rótulo visível não diz e a pessoa precisa para agir.
   varreduraDica()  -> FALHAS (reprovam a entrega). Retorna [] quando está tudo certo. Acusa:
     1. controle só com ícone (sem texto visível) sem nome acessível ou sem dica;
     2. controle desabilitado (aria-disabled="true" ou o atributo disabled num botão) sem dica dizendo o motivo;
     3. campo com restrição DECLARADA (pattern, min, max, minlength, step ou type de data/hora/número) sem dica;
     4. qualidade de TODA dica existente: vazia, acima de 160 caracteres, repetindo o rótulo, gatilho "?" sem aria-label.
   avisosDica()     -> AVISOS (não reprovam; são para remover): dica em título (h1-h6), dica em card CLICÁVEL e dica em
                       texto corrido (o "?" ou o trecho com dica dentro de um parágrafo; botão e campo não contam).
   v1.2: o nome acessível conta o <label> associado ao controle (um interruptor role=switch com <label for> era acusado
   como "sem nome"); o aviso de card vale só para card clicável — painel que usa a classe "card" como moldura de
   formulário ou tabela não é card a explicar, e acusava toda dica de campo e de coluna dentro dele.
   v1.3: o elemento logo depois de um título só conta como "dica em título" quando é o gatilho "?"; um botão ou selo
   com dica própria ao lado do título (ex.: "Limpar histórico") é controle, não o título.
   Exceção: data-dica-excecao="motivo" no elemento, com o motivo registrado. */
function varreduraDica(raiz = document) {
  const falhas = [];
  const visivel = (el) => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
  const textoVisivel = (el) => {
    const c = el.cloneNode(true);
    c.querySelectorAll('.dica__texto, [aria-hidden="true"], svg, img').forEach((n) => n.remove());
    return (c.textContent || '').replace(/\s+/g, ' ').trim();
  };
  const nomeAcessivel = (el) => (el.getAttribute('aria-label') || '').trim() ||
    (el.getAttribute('aria-labelledby') || '').split(/\s+/).filter(Boolean).map((i) => document.getElementById(i)?.textContent || '').join(' ').trim() ||
    (el.labels ? [...el.labels].map((l) => textoVisivel(l)).join(' ').trim() : '') ||
    (el.querySelector('img[alt]')?.getAttribute('alt') || '').trim();
  const nome = (el) => (textoVisivel(el) || nomeAcessivel(el) || el.getAttribute('name') || el.id || el.tagName).slice(0, 40);
  const tooltipDe = (el) => {
    const ids = (el.getAttribute('aria-describedby') || '').split(/\s+/).filter(Boolean);
    const porId = ids.map((i) => document.getElementById(i)).find((n) => n && n.getAttribute('role') === 'tooltip');
    if (porId) return porId;
    return el.closest('.campo')?.querySelector('.dica .dica__texto') || null;
  };
  const pular = (el) => !visivel(el) || el.hasAttribute('data-dica-excecao') || el.closest('.dica__texto') || el.closest('[data-dica-excecao]');

  const controles = 'button:not(.dica__gatilho), a[href], [role=button], [role=menuitem], [role=tab], [role=switch]';
  raiz.querySelectorAll(controles).forEach((el) => {
    if (pular(el)) return;
    const soIcone = !textoVisivel(el);
    const desab = el.getAttribute('aria-disabled') === 'true' || (el.matches('button') && el.disabled);
    if (soIcone && !nomeAcessivel(el)) { falhas.push({ elemento: nome(el), problema: 'controle só com ícone sem nome acessível (aria-label)' }); return; }
    if (soIcone && !el.labels?.length && !tooltipDe(el)) { falhas.push({ elemento: nome(el), problema: 'controle só com ícone sem dica' }); return; }
    if (desab && !tooltipDe(el)) {
      falhas.push({ elemento: nome(el), problema: el.disabled ? 'botão com disabled nativo: use aria-disabled="true" com dica do motivo' : 'desabilitado sem dica do motivo' });
    }
  });

  const tiposRestritos = /^(date|datetime-local|time|month|week|number)$/;
  raiz.querySelectorAll('input:not([type=hidden]):not([type=checkbox]):not([type=radio]):not([type=submit]):not([type=button]):not([type=reset]), textarea, select').forEach((el) => {
    if (pular(el)) return;
    const restrito = ['pattern', 'min', 'max', 'minlength', 'step'].some((a) => el.hasAttribute(a)) || tiposRestritos.test((el.getAttribute('type') || '').toLowerCase());
    if (restrito && !tooltipDe(el)) falhas.push({ elemento: nome(el), problema: 'campo com formato ou limite declarado sem dica' });
  });

  raiz.querySelectorAll('.dica').forEach((d) => {
    if (d.closest('[data-dica-excecao]')) return;
    const t = d.querySelector('.dica__texto'); if (!t) return;
    const g = d.querySelector('.dica__gatilho');
    const dono = g || d.querySelector('[aria-describedby]') || d.parentElement;
    if (g && !(g.getAttribute('aria-label') || '').trim()) { falhas.push({ elemento: nome(d.parentElement || d), problema: 'gatilho sem aria-label' }); return; }
    const txt = t.textContent.trim();
    const rotulo = g ? textoVisivel(d.parentElement || d) : textoVisivel(dono);
    if (!txt) falhas.push({ elemento: nome(dono), problema: 'tooltip vazio' });
    else if (txt.length > 160) falhas.push({ elemento: nome(dono), problema: `tooltip com ${txt.length} caracteres (máx. 160)` });
    else if (rotulo && txt.toLowerCase() === rotulo.toLowerCase()) falhas.push({ elemento: rotulo.slice(0, 40), problema: 'tooltip repete o rótulo' });
  });
  return falhas;
}

function avisosDica(raiz = document) {
  const avisos = [];
  const semDica = (el) => { const c = el.cloneNode(true); c.querySelectorAll('.dica').forEach((n) => n.remove()); return c.textContent.replace(/\s+/g, ' ').trim().slice(0, 40); };
  // Card clicável: o próprio card é link, está dentro de um link, ou tem um link esticado (::after posicionado) que cobre o card.
  const clicavel = (card) => card.matches('a[href], [role=link]') || !!card.closest('a[href]') ||
    [...card.querySelectorAll('a[href]')].some((a) => a.classList.contains('stretched-link') ||
      (getComputedStyle(a, '::after').content !== 'none' && getComputedStyle(a, '::after').position === 'absolute'));
  raiz.querySelectorAll('.dica').forEach((d) => {
    if (d.closest('[data-dica-excecao]') || !d.querySelector('.dica__texto')) return;
    const titulo = d.closest('h1, h2, h3, h4, h5, h6, [role=heading]') ||
      (d.querySelector(':scope > .dica__gatilho') && d.previousElementSibling?.matches('h1, h2, h3, h4, h5, h6, [role=heading]') ? d.previousElementSibling : null);
    const card = d.closest('article, .card, [class*="card"]');
    // Texto corrido: o "?" ou o trecho de texto com dica no meio de um parágrafo. Botão ou campo dentro de <p> é controle, não texto.
    const CTRL = 'button:not(.dica__gatilho), a[href], input, select, textarea, [role=button], [role=link]';
    const controle = d.matches(CTRL) || !!d.querySelector(':scope > :is(' + CTRL + ')');
    const paragrafo = controle ? null : d.closest('p');
    if (titulo) avisos.push({ elemento: semDica(titulo), problema: 'dica em título: o texto visível já explica' });
    else if (card && clicavel(card)) { const h = card.querySelector('h1, h2, h3, h4, h5, h6'); avisos.push({ elemento: h ? semDica(h) : String(card.className).slice(0, 40), problema: 'dica dentro de card clicável: no toque ela disputa o toque com o card' }); }
    else if (paragrafo) avisos.push({ elemento: semDica(paragrafo), problema: 'dica em texto corrido: o próprio texto é a explicação' });
  });
  return avisos;
}
if (typeof module !== 'undefined') module.exports = { varreduraDica, avisosDica };
