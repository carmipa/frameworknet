/* dica-varredura.js v1.0 (2026-10-05) — fonte canônica: instrucoes/tooltip-canonico/. Lista elementos do escopo SEM tooltip acessível. Retorna [] quando está tudo certo.
   Exceção: data-dica-excecao="motivo" (motivo precisa estar registrado em decisoes/). */
function varreduraDica(raiz = document) {
  const falhas = [];
  const visivel = (el) => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
  const nome = (el) => (el.getAttribute('aria-label') || el.textContent || el.getAttribute('name') || el.id || el.tagName).trim().slice(0, 40);
  const tooltipDe = (el) => {
    const ids = (el.getAttribute('aria-describedby') || '').split(/\s+/).filter(Boolean);
    return ids.map((i) => document.getElementById(i)).find((n) => n && n.getAttribute('role') === 'tooltip') || null;
  };
  const ctrl = 'input:not([type=hidden]), select, textarea, button:not(.dica__gatilho), [role=button], [role=menuitem], [role=tab], [role=switch]';
  const texto = 'label, th, h1, h2, h3, h4, h5, h6, [role=columnheader]';
  raiz.querySelectorAll(`${ctrl}, ${texto}`).forEach((el) => {
    if (!visivel(el) || el.hasAttribute('data-dica-excecao') || el.closest('.dica__texto')) return;
    const eCtrl = el.matches(ctrl);
    let t = null, ok = false;
    if (eCtrl) { t = tooltipDe(el); ok = !!t; }
    else {
      const d = el.querySelector('.dica') || (el.nextElementSibling?.matches('.dica') ? el.nextElementSibling : null) || el.closest('.campo')?.querySelector('.dica');
      t = d?.querySelector('.dica__texto'); ok = !!t;
      const g = d?.querySelector('.dica__gatilho');
      if (ok && (!g || !(g.getAttribute('aria-label') || '').trim())) { falhas.push({ elemento: nome(el), problema: 'gatilho sem aria-label' }); return; }
    }
    if (!ok) { falhas.push({ elemento: nome(el), tag: el.tagName.toLowerCase(), problema: 'sem tooltip' }); return; }
    const txt = t.textContent.trim();
    if (!txt) falhas.push({ elemento: nome(el), problema: 'tooltip vazio' });
    else if (txt.length > 160) falhas.push({ elemento: nome(el), problema: `tooltip com ${txt.length} caracteres (máx. 160)` });
    else if (txt.toLowerCase() === (el.textContent || '').trim().toLowerCase()) falhas.push({ elemento: nome(el), problema: 'tooltip repete o rótulo' });
  });
  return falhas;
}
if (typeof module !== 'undefined') module.exports = { varreduraDica };
