// Verificação no Chromium das abas da trilha e do desbloqueio nível a nível. 0 passou, 1 reprovou, 2 não verificou.
// O progresso é montado direto no localStorage (o mesmo formato que a lição grava), com o exterior bloqueado.
import { chromium } from 'playwright';

const BASE = process.argv[2] || 'http://localhost:8095';
const falhas = [];
const ok = [];
const confere = (c, d) => (c ? ok : falhas).push(d);
const FUNDAMENTOS = ['fundamentos.binario', 'fundamentos.hexadecimal', 'fundamentos.camadas'];
const IPV4 = ['ipv4.mascara', 'ipv4.subredes'];

let browser;
try {
  browser = await chromium.launch();
} catch (e) {
  console.log('NAO VERIFICOU: chromium não abriu', e.message);
  process.exit(2);
}

async function contexto(opcoes = {}) {
  const ctx = await browser.newContext({ viewport: { width: opcoes.largura || 1400, height: 900 } });
  await ctx.route('**/*', (r) => (new URL(r.request().url()).hostname === 'localhost' ? r.continue() : r.abort()));
  if (opcoes.semArmazenamento) {
    await ctx.addInitScript(() => {
      Object.defineProperty(window, 'localStorage', { get() { throw new Error('bloqueado pela política'); } });
    });
  }
  const page = await ctx.newPage();
  const erros = [];
  page.on('pageerror', (e) => erros.push('pageerror: ' + e.message));
  page.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) erros.push('console: ' + m.text()); });
  page.on('response', (r) => { if (new URL(r.url()).hostname === 'localhost' && r.status() >= 400) erros.push('local ' + r.status() + ' ' + r.url()); });
  return { ctx, page, erros };
}

async function concluir(page, ids) {
  await page.evaluate((lista) => {
    lista.forEach((id) => localStorage.setItem('academia.progresso.v1.' + id,
      JSON.stringify({ acertos: 3, tentativas: 3, concluidaEm: '2026-10-01T12:00:00.000Z' })));
  }, ids);
}

async function estadosDasAbas(page) {
  return page.$$eval('[data-acad-aba]', (abas) => abas.map((a) => {
    const partes = a.querySelectorAll('[data-acad-aba-estado] > span');
    return a.getAttribute('data-acad-aba') + ':' + (partes[0] ? partes[0].textContent : '') + ' ' + (partes[1] ? partes[1].textContent : '');
  }));
}

try {
  // ------------------------------------------------ aluno novo
  {
    const { ctx, page, erros } = await contexto();
    await page.goto(BASE + '/academia', { waitUntil: 'load' });
    const estados = await estadosDasAbas(page);
    confere(JSON.stringify(estados) === JSON.stringify(['fundamentos:lock_open liberado',
      'ipv4:lock bloqueado', 'transporte:lock bloqueado', 'enlace:schedule em breve']), 'aluno novo: ' + estados.join(' | '));
    confere(await page.isVisible('[data-acad-abas-lista]'), 'lista de abas visível');
    const visiveis = await page.$$eval('[data-acad-painel]', (ps) => ps.filter((p) => p.offsetParent !== null).map((p) => p.getAttribute('data-acad-painel')));
    confere(JSON.stringify(visiveis) === '["fundamentos"]', 'só o painel do nível atual aparece: ' + visiveis.join(','));
    // aba bloqueada abre e diz o motivo VISÍVEL; links das lições desligados
    await page.click('[data-acad-aba="transporte"]');
    const motivo = await page.textContent('#painel-transporte [data-acad-painel-bloqueio-texto]');
    confere(await page.isVisible('#painel-transporte [data-acad-painel-bloqueio]') && motivo.includes('Fundamentos'),
      'transporte bloqueado mostra o motivo apontando Fundamentos: ' + motivo);
    const hrefs = await page.$$eval('#painel-transporte .acad-licao-link', (as) => as.map((a) => a.getAttribute('href')));
    confere(hrefs.length === 2 && hrefs.every((h) => h === null), 'links de lição de nível bloqueado ficam desligados');
    const urlAntes = page.url();
    // clique direto no elemento (clique "forçado" por coordenada passa por cima do menu do site e mede outra coisa)
    await page.$eval('#painel-transporte .acad-licao-link', (a) => a.click());
    await page.waitForTimeout(300);
    confere(page.url() === urlAntes, 'clicar numa lição bloqueada não navega');
    // teclado: setas e Home/End, só a ativa no Tab
    await page.focus('[data-acad-aba="transporte"]');
    await page.keyboard.press('ArrowRight');
    confere(await page.evaluate(() => document.activeElement.getAttribute('data-acad-aba')) === 'enlace', 'seta para a direita vai para Enlace');
    await page.keyboard.press('Home');
    confere(await page.evaluate(() => document.activeElement.getAttribute('data-acad-aba')) === 'fundamentos', 'Home volta para Fundamentos');
    const tabindex = await page.$$eval('[data-acad-aba]', (as) => as.map((a) => a.getAttribute('tabindex')).join(''));
    confere(tabindex === '0-1-1-1', 'só a aba ativa entra no Tab: ' + tabindex);
    const larguraIcone = await page.$eval('[data-acad-aba="ipv4"] [data-acad-aba-estado] .material-symbols-outlined', (el) => el.getBoundingClientRect().width);
    confere(larguraIcone > 0 && larguraIcone < 30, `ícone de cadeado é glifo com o exterior bloqueado (${larguraIcone.toFixed(1)}px)`);
    confere(erros.length === 0, 'landing sem erro: ' + erros.join(' | '));

    // ------------------------------------------------ concluir Fundamentos desbloqueia IPv4 (e só ele)
    await concluir(page, FUNDAMENTOS.slice(0, 2));
    await page.reload({ waitUntil: 'load' });
    confere((await estadosDasAbas(page))[1] === 'ipv4:lock bloqueado', 'A1: duas de três lições não desbloqueiam');
    await concluir(page, FUNDAMENTOS);
    await page.reload({ waitUntil: 'load' });
    const depois = await estadosDasAbas(page);
    confere(depois[0] === 'fundamentos:task_alt concluído' && depois[1] === 'ipv4:lock_open liberado' && depois[2] === 'transporte:lock bloqueado',
      'Fundamentos concluído libera IPv4: ' + depois.join(' | '));
    const ativa = await page.$eval('[data-acad-aba][aria-selected="true"]', (a) => a.getAttribute('data-acad-aba'));
    confere(ativa === 'ipv4', 'a aba inicial é o nível em que o aluno está: ' + ativa);
    const registrados = await page.evaluate(() => localStorage.getItem('academia.niveis.v1.desbloqueados'));
    confere(registrados === '["ipv4"]', 'desbloqueio registrado: ' + registrados);

    // não regride: some uma lição concluída de Fundamentos (como uma lição nova) e o IPv4 continua liberado
    await page.evaluate(() => localStorage.removeItem('academia.progresso.v1.fundamentos.camadas'));
    await page.reload({ waitUntil: 'load' });
    confere((await estadosDasAbas(page))[1] === 'ipv4:lock_open liberado', 'desbloqueio não regride');

    // página de lição de nível bloqueado: aviso visível com o caminho
    await page.goto(BASE + '/academia/transporte/aperto', { waitUntil: 'load' });
    confere(await page.isVisible('[data-acad-bloqueio="transporte"]'), 'lição de nível bloqueado mostra o aviso');
    const link = await page.getAttribute('[data-acad-bloqueio-link]', 'href');
    confere(link === '/academia/ipv4', 'o aviso leva ao nível que falta concluir: ' + link);
    confere(await page.isVisible('#provar-form'), 'a lição continua legível (quem chega por buscador não vê tela vazia)');
    await page.goto(BASE + '/academia/ipv4/mascara', { waitUntil: 'load' });
    confere(!(await page.isVisible('[data-acad-bloqueio="ipv4"]')), 'lição de nível liberado não mostra aviso');

    // apagar o progresso tranca de volta, na hora
    await page.goto(BASE + '/academia', { waitUntil: 'load' });
    page.once('dialog', (d) => d.accept());
    await page.click('#apagar-progresso');
    const apagado = await estadosDasAbas(page);
    confere(apagado[1] === 'ipv4:lock bloqueado' && (await page.evaluate(() => localStorage.getItem('academia.niveis.v1.desbloqueados'))) === null,
      'apagar o progresso tranca de volta sem recarregar: ' + apagado.join(' | '));
    confere(erros.length === 0, 'fluxo sem erro: ' + erros.join(' | '));
    await ctx.close();
  }

  // ------------------------------------------------ navegação dentro do nível: sub-abas e volta
  {
    const { ctx, page, erros } = await contexto();
    await page.goto(BASE + '/academia/fundamentos/binario', { waitUntil: 'load' });
    const subabas = await page.$$eval('.acad-nav-licoes a', (as) => as.map((a) => a.textContent.replace(/\s+/g, ' ').trim()));
    confere(subabas.length === 4, 'sub-abas: visão do nível + 3 lições — ' + subabas.join(' | '));
    await page.click('.acad-nav-licoes a[href="/academia/fundamentos/camadas"]');
    await page.waitForURL('**/fundamentos/camadas');
    confere(await page.$eval('.acad-subaba.active', (a) => a.getAttribute('href')) === '/academia/fundamentos/camadas', 'sub-aba leva à outra lição e a marca');
    await page.click('.acad-nav-licoes a[href="/academia/fundamentos"]');
    await page.waitForURL(/\/academia\/fundamentos\/?$/);
    confere(true, 'sub-aba "Visão do nível" volta para a lista do nível');
    const ipv4Nav = await page.$eval('[data-acad-nav-nivel="ipv4"]', (a) => ({ href: a.getAttribute('href'), estado: a.querySelector('[data-acad-aba-estado]').textContent }));
    confere(ipv4Nav.href === null && ipv4Nav.estado.includes('bloqueado'), 'na navegação o nível bloqueado não abre e diz por quê: ' + JSON.stringify(ipv4Nav));
    await page.click('.acad-nav-niveis a[href="/academia#trilha"]');
    await page.waitForURL('**/academia/**');
    confere(await page.isVisible('[data-acad-abas-lista]'), '"Academia · início" volta para as abas');
    // concluir a lição na própria página atualiza a sub-aba sem recarregar
    await page.goto(BASE + '/academia/fundamentos/binario', { waitUntil: 'load' });
    await page.evaluate(() => {
      localStorage.setItem('academia.progresso.v1.fundamentos.binario', JSON.stringify({ acertos: 2, tentativas: 2, concluidaEm: null }));
      window.AcademiaProgresso.registrarTentativa('fundamentos.binario', true);
    });
    const selo = await page.textContent('[data-acad-subaba-selo="fundamentos.binario"]');
    confere(selo.includes('concluída'), 'concluir na página pinta a sub-aba na hora: ' + selo);
    const icones = await page.$$eval('.acad-nav .material-symbols-outlined', (els) => els.map((e) => e.getBoundingClientRect().width));
    confere(icones.length > 8 && icones.every((w) => w > 0 && w < 30), `ícones da navegação são glifos com o exterior bloqueado (${icones.length})`);
    confere(erros.length === 0, 'navegação sem erro: ' + erros.join(' | '));
    await ctx.close();
  }

  // ------------------------------------------------ outra aba do navegador conclui: esta repinta
  {
    const { ctx, page } = await contexto();
    await page.goto(BASE + '/academia', { waitUntil: 'load' });
    const outra = await ctx.newPage();
    await outra.goto(BASE + '/academia/fundamentos', { waitUntil: 'load' });
    await concluir(outra, FUNDAMENTOS);
    await page.waitForTimeout(300);
    confere((await estadosDasAbas(page))[1] === 'ipv4:lock_open liberado', 'conclusão em outra aba repinta esta (tela velha)');
    await ctx.close();
  }

  // ------------------------------------------------ navegador que não guarda progresso
  {
    const { ctx, page, erros } = await contexto({ semArmazenamento: true });
    await page.goto(BASE + '/academia', { waitUntil: 'load' });
    const estados = await estadosDasAbas(page);
    confere(estados.slice(0, 3).every((e) => e.endsWith('liberado')), 'sem armazenamento tudo liberado: ' + estados.join(' | '));
    confere(await page.isVisible('[data-acad-abas-aviso]'), 'e a tela diz por quê');
    confere(erros.length === 0, 'sem armazenamento, sem erro: ' + erros.join(' | '));
    await ctx.close();
  }

  // ------------------------------------------------ sem JavaScript: tudo visível em lista (buscador)
  {
    const ctx = await browser.newContext({ javaScriptEnabled: false });
    const page = await ctx.newPage();
    await page.goto(BASE + '/academia', { waitUntil: 'load' });
    const paineis = await page.$$eval('[data-acad-painel]', (ps) => ps.filter((p) => p.offsetParent !== null).length);
    confere(paineis === 4 && !(await page.isVisible('[data-acad-abas-lista]')), 'sem JS os quatro níveis aparecem em lista');
    await ctx.close();
  }

  // ------------------------------------------------ celular sem estouro
  for (const largura of [390, 320]) {
    const ctx = await browser.newContext({ viewport: { width: largura, height: 900 } });
    const page = await ctx.newPage();
    await page.goto(BASE + '/academia', { waitUntil: 'load' });
    let sobra = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    confere(sobra <= 0, `landing em ${largura}px sem estouro (sobra ${sobra})`);
    await page.goto(BASE + '/academia/transporte/aperto', { waitUntil: 'load' });
    sobra = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    confere(sobra <= 0, `lição com a navegação em ${largura}px sem estouro (sobra ${sobra})`);
    await ctx.close();
  }
} catch (e) {
  const onde = (e.stack || '').split(/\r?\n/).find((l) => l.includes('abas-navegador')) || '';
  falhas.push('exceção: ' + e.message.split(/\r?\n/)[0] + ' @ ' + onde.trim());
} finally {
  await browser.close();
}

ok.forEach((d) => console.log('ok   ' + d));
falhas.forEach((d) => console.log('FALHA ' + d));
console.log(`\n${ok.length} ok, ${falhas.length} falhas`);
process.exit(ok.length === 0 ? 2 : falhas.length ? 1 : 0);
