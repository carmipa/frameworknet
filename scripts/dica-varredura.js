/* dica-varredura.js v2.0 (2026-11-04) — componente compartilhado: não editar no projeto; mudança só na origem.
   Tooltip SÓ ONDE É NECESSÁRIO. "?" à vista só em campo e em sigla, unidade ou métrica; sem "?" em controle só com ícone
   e em desabilitado; sem dica em botão, link, menu ou aba habilitados com texto, nem em título.
   varreduraDica()  -> FALHAS (reprovam a entrega). Retorna [] quando está tudo certo. Acusa:
     1. controle só com ícone (sem texto visível) sem nome acessível ou sem dica;
     2. controle desabilitado (aria-disabled="true" ou o atributo disabled num botão) sem dica dizendo o motivo;
     3. campo com restrição DECLARADA (pattern, min, max, minlength, step ou type de data/hora/número) sem dica;
     4. qualidade de TODA dica existente: vazia, acima de 160 caracteres, repetindo o rótulo, gatilho "?" sem aria-label;
     5. dica de campo sem "?" à vista;
     8. marca "?" (.dica__marca) ao lado de um controle;
     9. dica em botão, link, menu ou aba habilitados com texto;
     10. "?" em título, legenda ou resumo, ou dentro de menu, navegação ou abas.
   avisosDica()     -> AVISOS (não reprovam; são para conferir): dica em card CLICÁVEL, dica em texto corrido (o "?" ou o
                       trecho com dica dentro de um parágrafo; botão e campo não contam) e "?" fora de campo, que só fica
                       se explica sigla, unidade ou métrica (a máquina não sabe se o termo é autoexplicativo).
   v1.2: o nome acessível conta o <label> associado ao controle (um interruptor role=switch com <label for> era acusado
   como "sem nome"); o aviso de card vale só para card clicável — painel que usa a classe "card" como moldura de
   formulário ou tabela não é card a explicar, e acusava toda dica de campo e de coluna dentro dele.
   v1.3: o elemento logo depois de um título só conta como "dica em título" quando é o gatilho "?"; um botão ou selo
   com dica própria ao lado do título (ex.: "Limpar histórico") é controle, não o título.
   v1.4: falha nova, dica sem "?" à vista (item 5). O aviso de título passa a valer só quando a dica repete o texto
   visível que vem logo depois do título (70% ou mais das palavras de 4 letras ou mais); dica de título que acrescenta
   algo deixa de ser aviso.
   v1.5: a falha 5 só vale para controle que está na tela agora; o que está escondido (ele ou um ancestral, como um menu
   recolhido) não é avaliado — a marca some junto com o controle. Quem mede abre os blocos fechados antes de varrer.
   v1.6: num bloco de campo com DUAS dicas, o "?" de uma não vale mais para a outra (o código da cor passava pelo "?"
   do nome da cor); e um campo com formato declarado só herda a dica do bloco quando o bloco tem uma dica só.
   Campo de texto não conta como "controle só com ícone" (era dispensado do "?" por não ter texto próprio).
   v1.7: marcação FORA do padrão (a .dica envolve o controle) passa a reprovar, em vez de escapar da conferência:
     6. dica dentro do próprio controle (a classe .dica no botão, no link ou no campo): o texto da dica entra no nome
        que o leitor de tela lê, e a conferência do "?" errava o nome e a isenção do que está escondido;
     7. texto de dica (role=tooltip) fora de qualquer .dica: nem o "?" nem o texto são conferidos.
   Os dois aparecem em adaptadores que geram a dica a partir do title; o conserto é o adaptador gerar a marcação do kit.
   v2.0: inverte a conferência do "?": em vez de exigi-lo em toda dica, reprova o que está fora do lugar (itens 8 a 10).
   A dica em título deixa de ser aviso e passa a reprovar.
   Exceção: data-dica-excecao="motivo" no elemento, com o motivo registrado. */
const DICA_TITULO = 'h1, h2, h3, h4, h5, h6, [role=heading], legend, summary, caption';
// "?" de campo: dentro do bloco do campo ou do rótulo, ou o campo aponta para o mesmo texto da dica.
const dicaDeCampo = (g, t) => !!g.closest('.campo, label') ||
  (!!t.id && !!document.querySelector(`:is(input, select, textarea)[aria-describedby~="${CSS.escape(t.id)}"]`));
const tituloDaDica = (g) => g.closest(DICA_TITULO) ||
  (g.parentElement?.previousElementSibling?.matches(DICA_TITULO) ? g.parentElement.previousElementSibling : null);

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
    // Sem aria-describedby, o campo herda a dica do bloco só quando o bloco tem UMA dica (senão seria a de outro campo).
    const doBloco = el.closest('.campo')?.querySelectorAll('.dica .dica__texto') || [];
    return doBloco.length === 1 ? doBloco[0] : null;
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

  // Na tela agora: o que está escondido (ele ou um ancestral, como um menu recolhido) não é avaliado.
  const naTela = (el) => { const r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  const CAMPOS = 'input:not([type=hidden]), select, textarea';
  raiz.querySelectorAll('.dica').forEach((d) => {
    if (d.closest('[data-dica-excecao]')) return;
    if (d.matches(controles + ', ' + CAMPOS)) {
      if (naTela(d)) falhas.push({ elemento: nome(d), problema: 'dica dentro do próprio controle: o texto entra no nome lido pelo leitor de tela; a .dica envolve o controle' });
      return;
    }
    const t = d.querySelector('.dica__texto'); if (!t) return;
    const g = d.querySelector('.dica__gatilho');
    const dono = g || d.querySelector('[aria-describedby]') || d.parentElement;
    if (g && !(g.getAttribute('aria-label') || '').trim()) { falhas.push({ elemento: nome(d.parentElement || d), problema: 'gatilho sem aria-label' }); return; }
    const txt = t.textContent.trim();
    const rotulo = g ? textoVisivel(d.parentElement || d) : textoVisivel(dono);
    if (!txt) falhas.push({ elemento: nome(dono), problema: 'tooltip vazio' });
    else if (txt.length > 160) falhas.push({ elemento: nome(dono), problema: `tooltip com ${txt.length} caracteres (máx. 160)` });
    else if (rotulo && txt.toLowerCase() === rotulo.toLowerCase()) falhas.push({ elemento: rotulo.slice(0, 40), problema: 'tooltip repete o rótulo' });
    const controle = d.querySelector(':scope > [aria-describedby]') || d.querySelector('[aria-describedby]');
    // Controle fora da tela agora (ele ou um ancestral escondido, como um menu recolhido) não é avaliado.
    if (controle && !naTela(controle)) return;
    const marca = d.querySelector('.dica__marca');
    if (marca && visivel(marca)) { falhas.push({ elemento: nome(controle || dono), problema: 'marca "?" ao lado de controle: a tag dica não a põe mais; tire a marca' }); return; }
    if (controle && controle.matches(controles)) {
      const desab = controle.getAttribute('aria-disabled') === 'true' || (controle.matches('button') && controle.disabled);
      if (textoVisivel(controle) && !desab) falhas.push({ elemento: nome(controle), problema: 'dica em controle habilitado com texto: tire a dica ou explique em texto na tela' });
      return;
    }
    if (g) {
      if (!visivel(g)) return;
      if (dicaDeCampo(g, t)) return;
      const titulo = tituloDaDica(g);
      if (titulo) falhas.push({ elemento: textoVisivel(titulo).slice(0, 40), problema: '"?" em título: explique em texto na tela ou tire a dica' });
      else if (g.closest('nav, [role=menu], [role=menubar], [role=tablist]')) falhas.push({ elemento: nome(g.closest('nav, [role=menu], [role=menubar], [role=tablist]')), problema: '"?" em menu, navegação ou abas: tire a dica' });
      return;
    }
    // Um "?" de fora da .dica só vale se aponta para ESTE texto (aria-describedby com o id dele), não para outra dica.
    const aVistaFora = !!t.id && [...document.querySelectorAll('.dica__gatilho')].some((x) => (x.getAttribute('aria-describedby') || '').split(/\s+/).includes(t.id) && visivel(x));
    if (!aVistaFora) falhas.push({ elemento: nome(controle || dono), problema: 'dica de campo sem "?" à vista: a pessoa não sabe que ela existe' });
  });
  // Texto de dica fora de qualquer .dica (ex.: gerado por um adaptador à parte): não dá para conferir o "?" nem o texto.
  raiz.querySelectorAll('[role=tooltip]').forEach((t) => {
    if (t.closest('.dica') || t.closest('[data-dica-excecao]')) return;
    const quem = t.id ? document.querySelector(`[aria-describedby~="${CSS.escape(t.id)}"]`) : null;
    if (quem && (quem.closest('[data-dica-excecao]') || !naTela(quem))) return;
    falhas.push({ elemento: quem ? nome(quem) : (t.id || 'tooltip sem id'), problema: 'dica fora da marcação do kit (sem a .dica em volta): o "?" e o texto não são conferidos' });
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
    const g = d.querySelector(':scope > .dica__gatilho');
    if (g && (tituloDaDica(g) || g.closest('nav, [role=menu], [role=menubar], [role=tablist]'))) return; // já é falha
    const card = d.closest('article, .card, [class*="card"]');
    // Texto corrido: o "?" ou o trecho de texto com dica no meio de um parágrafo. Botão ou campo dentro de <p> é controle, não texto.
    const CTRL = 'button:not(.dica__gatilho), a[href], input, select, textarea, [role=button], [role=link]';
    const controle = d.matches(CTRL) || !!d.querySelector(':scope > :is(' + CTRL + ')');
    const paragrafo = controle ? null : d.closest('p');
    if (card && clicavel(card)) { const h = card.querySelector('h1, h2, h3, h4, h5, h6'); avisos.push({ elemento: h ? semDica(h) : String(card.className).slice(0, 40), problema: 'dica dentro de card clicável: no toque ela disputa o toque com o card' }); }
    else if (paragrafo) avisos.push({ elemento: semDica(paragrafo), problema: 'dica em texto corrido: o próprio texto é a explicação' });
    else if (g && !dicaDeCampo(g, d.querySelector('.dica__texto'))) avisos.push({ elemento: semDica(d.parentElement || d), problema: '"?" fora de campo: confira se explica sigla, unidade ou métrica' });
  });
  return avisos;
}
if (typeof module !== 'undefined') module.exports = { varreduraDica, avisosDica };
