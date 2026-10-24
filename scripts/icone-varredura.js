/* icone-varredura.js v1.1 (2026-10-24) — componente compartilhado: não editar no projeto; mudança só na origem.
   1.1: o nome do glifo (Material Symbols/Icons) num invólucro antes do rótulo não conta como texto do invólucro, e
   seta de texto (U+2190 a U+21FF) não vale como ícone.
   Ícone junto de TODO rótulo: label, cabeçalho e legenda de tabela, título de área (página, seção, card, modal, aba),
   botão e link que age como botão, item de menu e navegação, legend, summary, KPI e selo de estado.
   varreduraIcone()  -> FALHAS (reprovam a entrega). Retorna [] quando está tudo certo. Acusa todo rótulo visível do
                        escopo acima que não tem ícone: nem dentro dele, nem imediatamente antes dele, nem declarado no
                        próprio elemento (data-icon), nem desenhado por CSS (::before/::after com conteúdo).
   avisosIcone()     -> AVISOS (não reprovam): títulos declarados editoriais (data-titulo-editorial) e exceções
                        declaradas (data-icone-excecao), para a contagem da entrega.
   O gatilho "?" de ajuda (classe dica__gatilho, glifo help) e o balão de dica NÃO contam como ícone do rótulo.
   Marcações respeitadas: data-icone-excecao="motivo" (fora da contagem; o motivo tem de estar registrado) e
   data-titulo-editorial em h1-h6 (título que é frase para ler, não nome de área). */
function varreduraIcone(raiz = document) {
  const falhas = [];
  const GLIFOS_DICA = new Set(['help', 'help_outline', 'question_mark', 'info_outline']);
  const SEL_ICONE = 'svg, img, [class*="material-symbols"], [class*="material-icons"], i[class], .icon, .ico, .fic, [class*="icone"], [class*="glifo"], [class*="bi-"], [class*="fa-"], [class*="lucide"], [class*="feather"], [data-icon], [data-icone], [data-glifo]';
  // KPI e selo de estado entram pelo papel (role=status/alert) ou pela classe kpi/metrica; um selo só com número não é rótulo.
  const SEL_ESCOPO = 'label, th, caption, legend, summary, button, h1, h2, h3, h4, h5, h6, [role=button], [role=tab], [role=menuitem], a.btn, a[class*="btn-"], a[class*="button"], nav a, [role=navigation] a, [role=menubar] a, [role=tablist] a, .breadcrumb a, .kpi, .metrica, [role=status], [role=alert]';
  const RE_EMOJI = /[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}]/u;
  const cs = (el, pseudo) => { try { return getComputedStyle(el, pseudo || null); } catch (e) { return null; } };
  const visivel = (el) => {
    if (el.closest('[hidden], template, [aria-hidden="true"]')) return false;
    const s = cs(el); return !s || (s.display !== 'none' && s.visibility !== 'hidden');
  };
  const deDica = (el) => !!el.closest('.dica__gatilho, .dica__texto, [role=tooltip]');
  const ehIconeReal = (c) => {
    if (deDica(c)) return false;
    if (/material-(symbols|icons)/.test(c.className || '') && GLIFOS_DICA.has((c.textContent || '').trim())) return false;
    return true;
  };
  const temIconeDentro = (el) => [...el.querySelectorAll(SEL_ICONE)].some(ehIconeReal);
  const ehElementoIcone = (el) => !!el && el.matches(SEL_ICONE) && ehIconeReal(el);
  // Texto do invólucro sem o nome dos glifos: em Material Symbols o nome do ícone é texto do DOM, não do rótulo.
  const semTexto = (n) => { const c = n.cloneNode(true); c.querySelectorAll(SEL_ICONE).forEach((x) => x.remove()); return !(c.textContent || '').trim(); };
  const temIconeAntes = (el) => {
    const irmao = el.previousElementSibling;
    if (irmao && (ehElementoIcone(irmao) || (irmao.children.length <= 2 && !irmao.matches(SEL_ESCOPO) && temIconeDentro(irmao) && semTexto(irmao)))) return true;
    const pai = el.parentElement;
    if (pai && pai.firstElementChild === el && !pai.matches(SEL_ESCOPO)) {
      const antesDoPai = pai.previousElementSibling;
      if (antesDoPai && (ehElementoIcone(antesDoPai) || (temIconeDentro(antesDoPai) && semTexto(antesDoPai)))) return true;
    }
    return false;
  };
  const temIconeCss = (el) => ['::before', '::after'].some((p) => {
    const s = cs(el, p); const c = s && s.content;
    return !!c && c !== 'none' && c !== 'normal' && c !== '""' && c !== "''";
  });
  const textoProprio = (el) => {
    const c = el.cloneNode(true);
    c.querySelectorAll('.dica__texto, .dica__gatilho, [aria-hidden="true"], svg, img').forEach((n) => n.remove());
    return (c.textContent || '').replace(/\s+/g, ' ').trim();
  };
  const nome = (el) => (textoProprio(el) || el.getAttribute('aria-label') || el.id || el.tagName.toLowerCase()).slice(0, 40);
  const tipo = (el) => el.tagName.toLowerCase();
  const pular = (el) => !visivel(el) || deDica(el) || el.closest('td') || el.hasAttribute('data-icone-excecao') || el.closest('[data-icone-excecao]') ||
    (/^h[1-6]$/.test(tipo(el)) && el.hasAttribute('data-titulo-editorial'));

  raiz.querySelectorAll(SEL_ESCOPO).forEach((el) => {
    if (pular(el)) return;
    if (el.matches(SEL_ESCOPO) && el.parentElement && el.parentElement.closest(SEL_ESCOPO) && !/^(th|label|button|summary)$/.test(tipo(el))) return; // rótulo dentro de rótulo (ex.: span kpi dentro de h2): conta o de fora
    const temIcone = el.matches('[data-icon], [data-icone], [data-glifo]') || temIconeDentro(el) || RE_EMOJI.test(textoProprio(el)) || temIconeAntes(el) || temIconeCss(el);
    if (!temIcone) falhas.push({ elemento: nome(el), tipo: tipo(el), problema: 'rótulo sem ícone' });
  });
  return falhas;
}

function avisosIcone(raiz = document) {
  const avisos = [];
  raiz.querySelectorAll('h1[data-titulo-editorial], h2[data-titulo-editorial], h3[data-titulo-editorial], h4[data-titulo-editorial], h5[data-titulo-editorial], h6[data-titulo-editorial]')
    .forEach((el) => avisos.push({ elemento: (el.textContent || '').trim().slice(0, 40), problema: 'título declarado editorial (sem ícone por decisão)' }));
  raiz.querySelectorAll('[data-icone-excecao]')
    .forEach((el) => avisos.push({ elemento: (el.textContent || '').trim().slice(0, 40) || el.tagName.toLowerCase(), problema: 'exceção declarada: ' + el.getAttribute('data-icone-excecao') }));
  return avisos;
}

if (typeof module !== 'undefined') module.exports = { varreduraIcone, avisosIcone };
