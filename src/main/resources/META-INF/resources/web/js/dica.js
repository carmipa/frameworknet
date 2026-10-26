/* dica.js v1.5 (2026-10-25) — comportamento do tooltip. Componente compartilhado: não editar no projeto; mudança só na origem.
   Sem dependências. Delegação de eventos: vale para conteúdo dinâmico.
   v1.1 (Paulo, 18/10/2026: "o tooltip está ficando muito tempo sobre os locais, cobrindo os locais de digitação"):
   - a dica de um CAMPO nunca cobre o campo: com o foco ou o mouse no campo, ela fica acima dele (ou abaixo, se não
     couber em cima), e não mais embaixo do "?", que fica justamente sobre a caixa de texto;
   - começar a digitar dispensa a dica daquele campo (como o Esc); ela volta no próximo foco;
   - a dica de quem já tinha o foco quando o script carregou (autofocus) é posicionada; antes ficava no canto 0,0.
   v1.2 (auditoria do painel, 18/10/2026: em cima da caixa, a dica cobria 100% do RÓTULO do campo — no autofoco da
   entrada a pessoa chegava sem ver "Usuário"): a dica de um campo fica em cima do BLOCO do campo (o .campo, com o
   rótulo e a caixa), ou embaixo da caixa se não couber em cima. Nem o rótulo nem a caixa ficam cobertos.
   v1.3 (revisão de boa-fé, 18/10/2026: com o campo focado, o mouse no RÓTULO ou no ícone tirava a âncora do campo e
   a dica descia para baixo do "?" — 84% a 96% sobre a caixa de senha, e o clique na caixa caía na dica): dentro do
   bloco do campo, fora do "?", a âncora continua sendo o campo. No "?" ela segue colada a ele (dá para ir com o
   mouse até o texto — WCAG 1.4.13). O dica.css v1.1 faz a dica que não está sob o mouse deixar passar o toque.
   v1.4 (teste de mãos, 24/10/2026: Tab até o "?" de um campo, Esc e um clique fora — a dica daquele campo não abria mais
   no mouse até a pessoa clicar na caixa): o "?" de um campo fica dentro de dois envoltórios (a .dica e o .campo) e o Esc
   marca os dois; a saída do foco e do ponteiro só desmarcava o mais próximo. Agora desmarca todos os que ficaram para trás.
   v1.5 (25/10/2026): a marca "?" ao lado de um controle com texto (.dica__marca) alterna a dica no toque, como o
   gatilho; no mouse o hover já cuida, e no teclado quem abre é o foco do próprio controle. Aberta pela marca, a dica
   nasce embaixo DELA (não no começo do controle): assim o mouse desce da marca ao texto sem passar pelo vizinho. */
(() => {
  const W = '.dica, .campo';
  const CAMPO = 'input:not([type=hidden]), select, textarea';
  const tip = (w) => w.querySelector('.dica__texto');
  const ancoras = new WeakMap();   // envoltório -> o campo em que a dica está ancorada (quando há um)
  const marcas = new WeakMap();    // envoltório -> a marca "?" que abriu a dica (quando foi ela)

  function posicionar(w) {
    const t = tip(w); if (!t) return;
    const campo = ancoras.get(w);
    const noCampo = campo && campo.isConnected && w.contains(campo);
    const marca = !noCampo && marcas.get(w);
    const ancora = (noCampo ? campo : (marca && marca.isConnected && w.contains(marca) ? marca : (t.closest('.dica') || w))).getBoundingClientRect();
    const m = 8, g = 6, vw = document.documentElement.clientWidth, vh = innerHeight;
    t.style.left = '0px'; t.style.top = '0px';
    const r = t.getBoundingClientRect();
    let x = ancora.left; if (x + r.width > vw - m) x = vw - m - r.width; if (x < m) x = m;
    let y, cima;
    if (noCampo) {
      // Campo: em cima do bloco dele (rótulo + caixa) se couber; senão embaixo da caixa. Nunca por cima da caixa em
      // que a pessoa escreve nem do rótulo que diz o que escrever.
      const topo = w.matches('.campo') ? Math.min(w.getBoundingClientRect().top, ancora.top) : ancora.top;
      cima = topo - g - r.height >= m;
      y = cima ? topo - g - r.height : ancora.bottom + g;
    } else {
      y = ancora.bottom + g; cima = false;
      if (y + r.height > vh - m && ancora.top - g - r.height >= m) { y = ancora.top - g - r.height; cima = true; }
    }
    t.style.left = x + 'px'; t.style.top = y + 'px';
    const d = t.closest('.dica') || w; if (cima) d.setAttribute('data-lado', 'cima'); else d.removeAttribute('data-lado');
  }
  const ancorar = (w, alvo) => {
    // No bloco do campo (rótulo, ícone, caixa), fora do "?": a âncora é a caixa do bloco, mesmo com o mouse no rótulo.
    const c = (alvo && alvo.closest?.(CAMPO)) || (w.matches('.campo') ? w.querySelector(CAMPO) : null);
    if (c && w.contains(c) && !c.closest('.dica__gatilho')) ancoras.set(w, c); else ancoras.delete(w);
    const m = alvo && alvo.closest?.('.dica__marca');
    if (m && w.contains(m)) marcas.set(w, m); else marcas.delete(w);
  };
  const abrir  = (w, alvo) => { ancorar(w, alvo); w.removeAttribute('data-fechada'); posicionar(w); };
  const fechar = (w) => { w.removeAttribute('data-aberta'); w.setAttribute('data-fechada', ''); };

  document.addEventListener('pointerover', (e) => {
    const w = e.target.closest?.(W); if (!w) return;
    const t = tip(w), de = e.relatedTarget;
    // Dentro do balão: fica onde está (ele não pode fugir de baixo do mouse — WCAG 1.4.13).
    if (t && t.contains(e.target)) { w.removeAttribute('data-fechada'); return; }
    // Passando por cima do campo no caminho do "?" até o balão: também fica onde está.
    const daDica = de && (t?.contains(de) || (de.closest?.('.dica') && w.contains(de.closest('.dica'))));
    if (daDica && e.target.closest?.(CAMPO)) return;
    abrir(w, e.target);
  });
  document.addEventListener('focusin',     (e) => { const w = e.target.closest?.(W); if (w) abrir(w, e.target); });
  // O "?" de um campo mora em dois envoltórios (.dica dentro do .campo): sair dele desmarca todos os que ficaram para trás.
  const envoltorios = (el) => { const l = []; for (let w = el?.closest?.(W); w; w = w.parentElement?.closest(W)) l.push(w); return l; };
  document.addEventListener('pointerout',  (e) => { envoltorios(e.target).forEach((w) => { if (!w.contains(e.relatedTarget)) w.removeAttribute('data-fechada'); }); });
  document.addEventListener('focusout',    (e) => { envoltorios(e.target).forEach((w) => { if (!w.contains(e.relatedTarget)) { w.removeAttribute('data-fechada'); w.removeAttribute('data-aberta'); } }); });

  // Digitar (ou escolher uma opção) dispensa a dica do campo: ela já foi lida no foco, e a pessoa precisa ver o que escreve.
  document.addEventListener('input', (e) => {
    if (!e.target.closest?.(CAMPO)) return;
    const w = e.target.closest(W); if (w) fechar(w);
  });

  // Esc dispensa sem mover o foco (WCAG 1.4.13)
  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape') return;
    document.querySelectorAll(W).forEach((w) => { if (w.matches(':hover, :focus-within, [data-aberta]')) fechar(w); });
  });

  // Rolagem/redimensionamento: reposiciona as dicas visíveis
  const reposicionar = () => document.querySelectorAll('.dica:hover, .dica:focus-within, .campo:focus-within, [data-aberta]').forEach(posicionar);
  addEventListener('scroll', reposicionar, true); addEventListener('resize', reposicionar);

  // Toque/caneta e teclado no gatilho "?" (e toque na marca "?"): alterna. Mouse não alterna (o hover já cuida).
  let tipoPonteiro = 'mouse';
  document.addEventListener('pointerdown', (e) => {
    tipoPonteiro = e.pointerType || 'mouse';
    document.querySelectorAll('[data-aberta]').forEach((w) => { if (!w.contains(e.target)) fechar(w); });
  }, true);
  document.addEventListener('click', (e) => {
    const g = e.target.closest?.('.dica__gatilho, .dica__marca'); if (!g) return;
    if (e.detail !== 0 && tipoPonteiro === 'mouse') return;
    const w = g.closest(W);
    if (w.hasAttribute('data-aberta')) fechar(w); else { w.setAttribute('data-aberta', ''); abrir(w, g); }
  });

  // Foco que chegou antes do script (autofocus): a dica já aparece pelo CSS e precisa de posição.
  const jaFocado = document.activeElement && document.activeElement !== document.body ? document.activeElement : null;
  const wFocado = jaFocado?.closest?.(W); if (wFocado) abrir(wFocado, jaFocado);
})();
