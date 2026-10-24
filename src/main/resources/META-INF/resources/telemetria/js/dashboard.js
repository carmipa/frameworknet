(function () {
    const ITENS_POR_PAGINA = 15;
    let historicoCompleto = [];
    let consoleLinhas = [];
    let termoBusca = '';
    let paginaAtual = 1;
    let janelaMinutos = 60;
    let autoRefreshMs = 10000;
    let consolePausado = false;
    let timerAutoRefresh = null;
    let chartModulos, chartStatus, chartAtividade, chartLatencia, chartOrigem, chartClientes;

    const CORES_MODULO = ['#f59e0b', '#60a5fa', '#34d399', '#818cf8', '#f43f5e', '#2dd4bf', '#c084fc', '#fb923c'];
    const CORES_STATUS = { '2xx': '#34d399', '3xx': '#60a5fa', '4xx': '#f59e0b', '5xx': '#f43f5e' };
    const PERIODO_LABEL = {
        '15': 'últimos 15 min', '60': 'última hora', '360': 'últimas 6 h',
        '1440': 'últimas 24 h', '10080': 'últimos 7 dias', '0': 'todo o histórico'
    };

    const esc = (s) => (window.HtmlEscape && window.HtmlEscape.esc) ? window.HtmlEscape.esc(s) : String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);  // fallback também escapa (BACK-16)

    function $(id) { return document.getElementById(id); }

    function configurarTemaChart() {
        if (typeof Chart === 'undefined') return;
        Chart.defaults.color = '#8b97ad';
        Chart.defaults.font.family = "'Inter Tight', system-ui, sans-serif";
        Chart.defaults.font.size = 11;
        Chart.defaults.plugins.legend.labels.boxWidth = 8;
        Chart.defaults.plugins.legend.labels.usePointStyle = true;
        Chart.defaults.plugins.tooltip.backgroundColor = '#0c1422';
        Chart.defaults.plugins.tooltip.usePointStyle = true;
        Chart.defaults.scale.grid.color = 'rgba(255,255,255,0.05)';
    }

    function formatarHorario(iso) {
        if (!iso) return '--';
        try { return new Date(iso).toLocaleString('pt-BR'); } catch (_) { return iso; }
    }

    function ms(v) { return (v == null ? 0 : v) + ' ms'; }

    // ---------- KPIs ----------
    function atualizarMetricas(data) {
        const lat = data.latencia || {};
        const resumo = data.resumo || {};
        $('t-http-total').textContent = String(data.httpTotal || 0);
        $('t-taxa-sucesso').textContent = (data.httpTotal ? (data.taxaSucesso || 0) : 0) + '%';
        $('t-http-4xx').textContent = String(data.http4xx || 0);
        $('t-http-5xx').textContent = String(data.http5xx || 0);
        $('t-p95').textContent = ms(lat.p95);
        $('t-latencia-sub').textContent = 'P50 ' + ms(lat.p50) + ' · P99 ' + ms(lat.p99);
        $('t-total-eventos').textContent = String(resumo.totalEventos || 0);
        const nMods = (data.porModulo || []).length;
        $('t-modulos-sub').textContent = nMods + (nMods === 1 ? ' módulo' : ' módulos');

        const okTaxa = $('t-taxa-sucesso');
        okTaxa.classList.toggle('kpi-bad', data.httpTotal > 0 && (data.taxaSucesso || 0) < 90);
        $('t-http-5xx').classList.toggle('kpi-bad', (data.http5xx || 0) > 0);

        if ($('t-atualizado')) $('t-atualizado').textContent = data.atualizadoEm || '--';
        if ($('t-periodo-label')) {
            let rotulo = 'janela: ' + (PERIODO_LABEL[String(data.janelaMinutos)] || (data.janelaMinutos + ' min'));
            if (data.coberturaDesde) rotulo += ' · dados desde ' + data.coberturaDesde;
            if (data.coberturaParcial) rotulo += ' (cobertura parcial: o período pedido começa antes do evento mais antigo guardado)';
            $('t-periodo-label').textContent = rotulo;
            $('t-periodo-label').classList.toggle('text-warning', !!data.coberturaParcial);
        }
        const pasta = $('t-pasta-logs');
        if (pasta) pasta.textContent = data.pastaLogs ? 'Logs: ' + data.pastaLogs : '';
    }

    // ---------- Gráficos ----------
    function atualizarGraficos(data) {
        const modulos = data.porModulo || [];
        const labelsMod = modulos.map(m => m.modulo);
        const valoresMod = modulos.map(m => m.total);
        if (chartModulos) chartModulos.destroy();
        chartModulos = new Chart($('chart-modulos'), {
            type: 'bar',
            data: {
                labels: labelsMod,
                datasets: [{
                    label: 'Requisições', data: valoresMod,
                    backgroundColor: labelsMod.map((_, i) => CORES_MODULO[i % CORES_MODULO.length]),
                    borderRadius: 6, barPercentage: 0.7
                }]
            },
            options: {
                indexAxis: 'y', responsive: true, maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: { callbacks: { afterBody: (items) => {
                        const m = modulos[items[0].dataIndex];
                        return ['OK: ' + m.ok + '  Erros: ' + m.erro, 'P95: ' + ms(m.p95)];
                    } } }
                },
                scales: { x: { beginAtZero: true, ticks: { precision: 0 } } }
            }
        });

        const stKeys = ['2xx', '3xx', '4xx', '5xx'];
        const stVals = [data.http2xx || 0, data.http3xx || 0, data.http4xx || 0, data.http5xx || 0];
        if (chartStatus) chartStatus.destroy();
        chartStatus = new Chart($('chart-status'), {
            type: 'doughnut',
            data: {
                labels: stKeys,
                datasets: [{ data: stVals, backgroundColor: stKeys.map(k => CORES_STATUS[k]), borderColor: '#0c121c', borderWidth: 3 }]
            },
            options: {
                responsive: true, maintainAspectRatio: false, cutout: '68%',
                plugins: { legend: { position: 'bottom' } }
            },
            plugins: [{
                id: 'centerTotal',
                beforeDraw(chart) {
                    const { ctx, chartArea } = chart;
                    if (!chartArea) return;
                    const total = stVals.reduce((a, b) => a + b, 0);
                    ctx.save();
                    ctx.font = '600 1.1rem "Space Grotesk", sans-serif';
                    ctx.fillStyle = '#eef2f8';
                    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
                    ctx.fillText(String(total), (chartArea.left + chartArea.right) / 2, (chartArea.top + chartArea.bottom) / 2);
                    ctx.restore();
                }
            }]
        });

        const atividade = data.atividadePorMinuto || [];
        const labelsAtv = atividade.map(a => a.minuto);
        if (chartAtividade) chartAtividade.destroy();
        chartAtividade = new Chart($('chart-atividade'), {
            type: 'line',
            data: {
                labels: labelsAtv,
                datasets: [
                    { label: 'Requisições', data: atividade.map(a => a.total), borderColor: '#60a5fa', backgroundColor: 'rgba(96,165,250,0.12)', fill: true, tension: 0.35, pointRadius: 0 },
                    { label: 'Erros', data: atividade.map(a => a.erros), borderColor: '#f43f5e', backgroundColor: 'rgba(244,63,94,0.10)', fill: true, tension: 0.35, pointRadius: 0 }
                ]
            },
            options: {
                responsive: true, maintainAspectRatio: false,
                plugins: { legend: { display: true, position: 'bottom' } },
                scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
            }
        });

        const lat = data.latencia || {};
        if (chartLatencia) chartLatencia.destroy();
        chartLatencia = new Chart($('chart-latencia'), {
            type: 'bar',
            data: {
                labels: ['P50', 'P90', 'P95', 'P99', 'Máx'],
                datasets: [{
                    label: 'ms', data: [lat.p50 || 0, lat.p90 || 0, lat.p95 || 0, lat.p99 || 0, lat.max || 0],
                    backgroundColor: ['#34d399', '#60a5fa', '#818cf8', '#f59e0b', '#f43f5e'],
                    borderRadius: 6, barPercentage: 0.6
                }]
            },
            options: {
                responsive: true, maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: { y: { beginAtZero: true, ticks: { callback: (v) => v + ' ms' } } }
            }
        });
    }

    // ---------- Tabelas de endpoints ----------
    function metodoBadge(m) {
        const mm = (m || 'GET').toUpperCase();
        return '<span class="tele-metodo m-' + esc(mm.toLowerCase()) + '">' + esc(mm) + '</span>';
    }

    function renderTopLentos(lista) {
        const tb = $('t-top-lentos');
        if (!tb) return;
        if (!lista || lista.length === 0) {
            tb.innerHTML = '<tr><td colspan="5" class="tele-empty">Sem dados no período.</td></tr>';
            return;
        }
        tb.innerHTML = lista.map(e => `
            <tr>
                <td>${metodoBadge(e.metodo)}</td>
                <td class="col-endpoint" title="${esc(e.endpoint)}">${esc(e.endpoint)}</td>
                <td class="num">${e.chamadas}</td>
                <td class="num mono">${ms(e.p95)}</td>
                <td class="num ${e.erros > 0 ? 'txt-err' : ''}">${e.erros}</td>
            </tr>`).join('');
    }

    function renderTopErros(lista) {
        const tb = $('t-top-erros');
        if (!tb) return;
        if (!lista || lista.length === 0) {
            tb.innerHTML = '<tr><td colspan="5" class="tele-empty">Nenhum erro no período.</td></tr>';
            return;
        }
        tb.innerHTML = lista.map(e => `
            <tr>
                <td>${metodoBadge(e.metodo)}</td>
                <td class="col-endpoint" title="${esc(e.endpoint)}">${esc(e.endpoint)}</td>
                <td class="num ${e.err4xx > 0 ? 'txt-warn' : ''}">${e.err4xx}</td>
                <td class="num ${e.err5xx > 0 ? 'txt-err' : ''}">${e.err5xx}</td>
                <td class="num">${e.chamadas}</td>
            </tr>`).join('');
    }

    // ---------- Origem do tráfego (por módulo e por rota) ----------
    function renderOrigem(data) {
        const modulos = data.porModulo || [];
        const total = modulos.reduce((a, m) => a + (m.total || 0), 0);

        const canvas = $('chart-origem');
        if (canvas) {
            const labels = modulos.map(m => m.modulo);
            const valores = modulos.map(m => m.total);
            if (chartOrigem) chartOrigem.destroy();
            chartOrigem = new Chart(canvas, {
                type: 'doughnut',
                data: {
                    labels: labels,
                    datasets: [{
                        data: valores,
                        backgroundColor: labels.map((_, i) => CORES_MODULO[i % CORES_MODULO.length]),
                        borderColor: '#0c121c', borderWidth: 3
                    }]
                },
                options: {
                    responsive: true, maintainAspectRatio: false, cutout: '58%',
                    plugins: {
                        legend: { position: 'bottom' },
                        tooltip: { callbacks: { label: (i) => {
                            const v = i.parsed;
                            const pct = total > 0 ? Math.round((v * 100) / total) : 0;
                            return ' ' + i.label + ': ' + v + ' (' + pct + '%)';
                        } } }
                    }
                }
            });
        }

        const tbMod = $('tabela-origem-modulos');
        if (tbMod) {
            tbMod.innerHTML = modulos.length === 0
                ? '<tr><td colspan="6" class="tele-empty">Sem dados no período.</td></tr>'
                : modulos.map(m => {
                    const pct = total > 0 ? Math.round((m.total * 100) / total) : 0;
                    return '<tr><td>' + esc(m.modulo) + '</td>'
                        + '<td class="num">' + m.total + '</td>'
                        + '<td class="num">' + pct + '%</td>'
                        + '<td class="num">' + m.ok + '</td>'
                        + '<td class="num ' + (m.erro > 0 ? 'txt-err' : '') + '">' + m.erro + '</td>'
                        + '<td class="num mono">' + ms(m.p95) + '</td></tr>';
                }).join('');
        }

        const rotas = data.topRotas || [];
        const tbRot = $('tabela-origem-rotas');
        if (tbRot) {
            tbRot.innerHTML = rotas.length === 0
                ? '<tr><td colspan="5" class="tele-empty">Sem dados no período.</td></tr>'
                : rotas.map(e => '<tr><td>' + metodoBadge(e.metodo) + '</td>'
                    + '<td class="col-endpoint">' + esc(e.endpoint) + '</td>'
                    + '<td class="num">' + e.chamadas + '</td>'
                    + '<td class="num ' + (e.erros > 0 ? 'txt-err' : '') + '">' + e.erros + '</td>'
                    + '<td class="num mono">' + ms(e.p95) + '</td></tr>').join('');
        }

        // Acessos por país (código ISO via Cloudflare; nunca o IP).
        const paises = data.paises || {};
        const chavesP = Object.keys(paises);
        const totalP = chavesP.reduce((a, k) => a + (paises[k] || 0), 0);
        const tbPais = $('tabela-origem-paises');
        if (tbPais) {
            tbPais.innerHTML = chavesP.length === 0
                ? '<tr><td colspan="3" class="tele-empty">Sem dados no período.</td></tr>'
                : chavesP.map(k => {
                    const v = paises[k] || 0;
                    const pct = totalP > 0 ? Math.round((v * 100) / totalP) : 0;
                    const nome = k === '??' ? '?? (desconhecido)' : esc(k);
                    return '<tr><td>' + nome + '</td><td class="num">' + v + '</td><td class="num">' + pct + '%</td></tr>';
                }).join('');
        }

        // Bots × pessoas (classificado pelo User-Agent).
        const clientes = data.clientes || {};
        const canvasC = $('chart-clientes');
        if (canvasC) {
            const rotulos = Object.keys(clientes);
            const cores = rotulos.map(r => r === 'humano' ? '#34d399' : (r === 'bot' ? '#f59e0b' : '#64748b'));
            if (chartClientes) chartClientes.destroy();
            chartClientes = new Chart(canvasC, {
                type: 'doughnut',
                data: {
                    labels: rotulos,
                    datasets: [{ data: rotulos.map(r => clientes[r] || 0), backgroundColor: cores, borderColor: '#0c121c', borderWidth: 3 }]
                },
                options: { responsive: true, maintainAspectRatio: false, cutout: '58%', plugins: { legend: { position: 'bottom' } } }
            });
        }
    }

    // ---------- Console ----------
    function filtrarConsole() {
        const nivel = ($('t-console-nivel') && $('t-console-nivel').value) || '';
        const busca = (($('t-console-busca') && $('t-console-busca').value) || '').toLowerCase();
        return consoleLinhas.filter(l => {
            const s = String(l);
            if (nivel && s.indexOf('[' + nivel + ']') === -1) return false;
            if (busca && s.toLowerCase().indexOf(busca) === -1) return false;
            return true;
        });
    }

    function renderConsole() {
        const el = $('console-telemetria');
        if (!el) return;
        const linhas = filtrarConsole();
        el.textContent = '';
        if (!linhas.length) {
            el.textContent = consoleLinhas.length ? 'Nenhuma linha corresponde ao filtro.' : 'Nenhuma saída registrada ainda.';
            return;
        }
        const frag = document.createDocumentFragment();
        const re = /^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\s+\[(\w+)\]\s+([\s\S]*)$/;
        linhas.forEach((linha) => {
            const div = document.createElement('div');
            const m = re.exec(String(linha));
            if (m) {
                const nivel = m[2].toLowerCase();
                const ts = document.createElement('span'); ts.className = 'cl-ts'; ts.textContent = m[1] + ' ';
                const lvl = document.createElement('span');
                lvl.className = 'cl-lvl ' + (nivel === 'warn' || nivel === 'warning' ? 'warn' : nivel === 'error' ? 'error' : 'info');
                lvl.textContent = '[' + m[2] + '] ';
                const msg = document.createElement('span'); msg.className = 'cl-msg'; msg.textContent = m[3];
                div.appendChild(ts); div.appendChild(lvl); div.appendChild(msg);
            } else {
                div.className = 'cl-msg'; div.textContent = String(linha);
            }
            frag.appendChild(div);
        });
        el.appendChild(frag);
        if (!consolePausado) el.scrollTop = el.scrollHeight;
    }

    // ---------- Histórico ----------
    function filtrarHistorico() {
        if (!termoBusca) return historicoCompleto;
        const t = termoBusca.toLowerCase();
        return historicoCompleto.filter(e =>
            [e.evento, e.modulo, e.status, e.message, e.level, e.httpPath, e.httpMethod].join(' ').toLowerCase().includes(t));
    }

    function badgeNivel(level) {
        const l = (level || 'INFO').toUpperCase();
        if (l === 'ERROR') return 'error';
        if (l === 'WARN' || l === 'WARNING') return 'warn';
        return 'info';
    }

    function renderizarTabela() {
        const filtrados = filtrarHistorico();
        const totalPaginas = Math.max(1, Math.ceil(filtrados.length / ITENS_POR_PAGINA));
        if (paginaAtual > totalPaginas) paginaAtual = totalPaginas;
        const inicio = (paginaAtual - 1) * ITENS_POR_PAGINA;
        const pagina = filtrados.slice(inicio, inicio + ITENS_POR_PAGINA);
        const tbody = $('t-table-body');
        const count = $('t-table-count');
        const info = $('t-page-info');

        if (count) count.textContent = filtrados.length === 0 ? '0 evento(s)'
            : filtrados.length + ' evento(s) · exibindo ' + (inicio + 1) + '-' + (inicio + pagina.length);
        if (info) info.textContent = 'Página ' + paginaAtual + ' de ' + totalPaginas;
        if (!tbody) return;
        if (pagina.length === 0) {
            const msg = historicoCompleto.length === 0 ? 'Nenhum evento registrado.' : 'Nenhum evento encontrado.';
            tbody.innerHTML = '<tr><td colspan="8" class="tele-empty">' + msg + '</td></tr>';
            return;
        }
        tbody.innerHTML = pagina.map(e => {
            const st = e.httpStatus != null ? e.httpStatus : (e.status || 'ok');
            const ok = e.httpStatus != null ? (e.httpStatus < 400) : ((e.status || 'ok').toLowerCase() === 'ok');
            return `
            <tr>
                <td class="col-hora">${esc(formatarHorario(e.timestamp))}</td>
                <td><span class="tele-badge ${badgeNivel(e.level)}">${esc((e.level || 'INFO').toUpperCase())}</span></td>
                <td>${esc(e.modulo || '--')}</td>
                <td>${e.httpMethod ? metodoBadge(e.httpMethod) : '--'}</td>
                <td class="col-endpoint" title="${esc(e.httpPath || e.evento || '')}">${esc(e.httpPath || e.evento || '--')}</td>
                <td><span class="tele-status"><span class="tele-dot ${ok ? '' : 'err'}"></span>${esc(st)}</span></td>
                <td class="num mono">${e.durationMs != null ? ms(e.durationMs) : '--'}</td>
                <td class="col-msg" title="${esc(e.message || '')}">${esc(e.message || '')}</td>
            </tr>`;
        }).join('');
    }

    // ---------- Carga ----------
    async function carregarDashboard() {
        // Nao recriar os graficos enquanto o mouse le um tooltip: o ciclo
        // destroi e refaz os canvas, e o tooltip sumiria na mao do usuario.
        if (window.__telemetriaPausada) {
            return;
        }
        try {
            const resp = await fetch('/telemetria/api/dashboard?janela=' + janelaMinutos + '&console=250');
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            const data = await resp.json();
            atualizarMetricas(data);
            atualizarGraficos(data);
            renderTopLentos(data.topLentos);
            renderTopErros(data.topErros);
            renderOrigem(data);
            if (!consolePausado) {
                consoleLinhas = data.consoleLinhas || [];
                renderConsole();
            }
            historicoCompleto = (data.resumo && data.resumo.ultimosEventos) ? data.resumo.ultimosEventos : [];
            renderizarTabela();
        } catch (err) {
            const el = $('console-telemetria');
            if (el) el.textContent = 'Erro ao carregar telemetria: ' + err.message;
        }
    }

    function reagendarAutoRefresh() {
        if (timerAutoRefresh) { clearInterval(timerAutoRefresh); timerAutoRefresh = null; }
        if (autoRefreshMs > 0) timerAutoRefresh = setInterval(carregarDashboard, autoRefreshMs);
    }

    function configurarConsoleAcoes() {
        document.querySelectorAll('.btn-copy-console').forEach(btn => {
            btn.addEventListener('click', async () => {
                const alvo = $(btn.dataset.target);
                if (alvo) { try { await navigator.clipboard.writeText((window.FieldTooltips ? window.FieldTooltips.textoLimpo(alvo) : alvo.textContent) || ''); } catch (_) { /* ignore */ } }
            });
        });
        document.querySelectorAll('.btn-clear-console').forEach(btn => {
            btn.addEventListener('click', async () => {
                const alvo = $(btn.dataset.target);
                // Só diz "limpo" se o servidor limpou: um leitor recebe 403 e as linhas voltariam no
                // próximo refresh, depois de a tela afirmar o contrário.
                let status = 0;
                try {
                    const resp = await fetch('/telemetria/api/console/limpar', { method: 'POST' });
                    status = resp.status;
                } catch (_) { status = 0; }
                if (status >= 200 && status < 300) {
                    consoleLinhas = [];
                    if (alvo) alvo.textContent = 'Console limpo. Aguardando novos eventos...';
                } else if (alvo) {
                    alvo.textContent = status === 403
                        ? 'Não limpo: só o dono da telemetria pode limpar o console (código 403).'
                        : 'Não foi possível limpar o console agora' + (status ? ' (código ' + status + ').' : ' (sem conexão).');
                }
            });
        });
        const nivel = $('t-console-nivel');
        const busca = $('t-console-busca');
        if (nivel) nivel.addEventListener('change', renderConsole);
        if (busca) busca.addEventListener('input', renderConsole);
        const pausar = $('t-console-pausar');
        if (pausar) pausar.addEventListener('click', () => {
            consolePausado = !consolePausado;
            pausar.classList.toggle('active', consolePausado);
            pausar.setAttribute('aria-pressed', String(consolePausado));
            const icon = pausar.querySelector('.material-symbols-outlined');
            if (icon) icon.textContent = consolePausado ? 'play_arrow' : 'pause';
        });
    }

    function init() {
        configurarTemaChart();
        configurarConsoleAcoes();

        const periodo = $('t-periodo');
        if (periodo) periodo.addEventListener('change', () => { janelaMinutos = parseInt(periodo.value, 10) || 0; carregarDashboard(); });

        const auto = $('t-autorefresh');
        if (auto) auto.addEventListener('change', () => { autoRefreshMs = parseInt(auto.value, 10) || 0; reagendarAutoRefresh(); });

        const btnRefresh = $('btn-refresh-telemetria');
        if (btnRefresh) btnRefresh.addEventListener('click', carregarDashboard);

        const busca = $('t-table-search');
        if (busca) busca.addEventListener('input', () => { termoBusca = busca.value.trim().toLowerCase(); paginaAtual = 1; renderizarTabela(); });

        const prev = $('t-page-prev');
        const next = $('t-page-next');
        if (prev) prev.addEventListener('click', () => { if (paginaAtual > 1) { paginaAtual--; renderizarTabela(); } });
        if (next) next.addEventListener('click', () => {
            const total = Math.max(1, Math.ceil(filtrarHistorico().length / ITENS_POR_PAGINA));
            if (paginaAtual < total) { paginaAtual++; renderizarTabela(); }
        });

        carregarDashboard();
        reagendarAutoRefresh();
        window.addEventListener('beforeunload', () => { if (timerAutoRefresh) clearInterval(timerAutoRefresh); });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();

/*
 * Botao "Sincronizar dataset".
 *
 * Proposito de negocio: publicar o snapshot sanitizado no repositorio publico
 * sem que nada fique guardado no servidor. O botao so dispara; quem gera,
 * audita e envia e o backend - regra de privacidade nao mora em JavaScript.
 *
 * Invariantes: confirmacao explicita antes de disparar, porque publicar nao tem
 * desfazer. O botao e desabilitado durante o envio: clique duplo nao pode virar
 * duas tentativas de criar o mesmo snapshot.
 *
 * Comportamento em caso de falha: mostra o motivo devolvido pelo servidor. 409
 * significa snapshot do dia ja publicado - e o append-only funcionando, nao um
 * erro a contornar.
 */
(function () {
    "use strict";
    var botao = document.getElementById("btn-sincronizar-dataset");
    var caixa = document.getElementById("dataset-resultado");
    if (!botao || !caixa) {
        return;
    }

    function mostrar(cor, icone, titulo, detalhe, link) {
        caixa.hidden = false;
        caixa.className = "aed-card card-tech p-3 mb-3 border border-" + cor + " border-opacity-50";
        caixa.innerHTML =
            '<div class="d-flex align-items-start gap-2">' +
            '<span class="material-symbols-outlined text-' + cor + '">' + icone + '</span>' +
            '<div><strong class="text-' + cor + '">' + titulo + '</strong>' +
            '<div class="small text-secondary mt-1">' + detalhe + '</div>' +
            (link ? '<a class="small" href="' + link + '" target="_blank" rel="noopener">Ver no GitHub</a>' : '') +
            '</div></div>';
    }

    /*
     * Estado permanente: le o que EXISTE no repositorio publico, nao o nosso log.
     * Alerta de clique some quando a pagina recarrega; isto responde "foi
     * sincronizado?" a qualquer momento, consultando a fonte de verdade.
     */
    async function atualizarEstado() {
        try {
            var r = await fetch("/telemetria/api/dataset/estado");
            var d = await r.json();
            if (!d.consultado) {
                mostrar("warning", "help", "Não foi possível conferir o repositório",
                    d.observacao || "Sem resposta do GitHub.", "");
                return;
            }
            if (!d.snapshots.length) {
                mostrar("info", "inbox", "Nenhum snapshot publicado ainda",
                    "Destino: " + d.destino, "");
                return;
            }
            mostrar("info", "inventory_2",
                d.snapshots.length + " snapshot(s) publicado(s) · último: " + d.snapshots[0],
                "No repositório: " + d.snapshots.join(" · "),
                "https://github.com/carmipa/framework-net-telemetry-dataset/tree/main/dataset");
        } catch (err) {
            /* silencio: a ausencia do painel de estado nao pode quebrar a pagina */
        }
    }

    atualizarEstado();

    botao.addEventListener("click", async function () {
        var aviso = "Publicar um snapshot novo no repositório PÚBLICO?\n\n"
            + "Publicar não tem desfazer, e snapshot já publicado não pode ser sobrescrito.";
        if (!window.confirm(aviso)) {
            return;
        }
        var original = botao.innerHTML;
        botao.disabled = true;
        botao.innerHTML = '<span class="material-symbols-outlined" translate="no" aria-hidden="true">progress_activity</span> Publicando...';
        try {
            var r = await fetch("/telemetria/api/dataset/sincronizar", { method: "POST" });
            var d = await r.json();
            if (r.ok && d.ok) {
                mostrar("success", "cloud_done", "Snapshot " + d.snapshot + " publicado",
                    d.registros + " registro(s), " + d.visitantes + " identificador(es) consultado(s) distinto(s) · " +
                    d.arquivos.length + " arquivo(s).", d.url);
            } else if (r.status === 409) {
                mostrar("warning", "block", "Snapshot do dia já existe",
                    d.erro, "");
            } else {
                mostrar("danger", "error", "Não foi possível publicar", d.erro || ("HTTP " + r.status), "");
            }
        } catch (err) {
            mostrar("danger", "error", "Falha de rede ao publicar", String(err), "");
        } finally {
            botao.disabled = false;
            botao.innerHTML = original;
            // Reconfere no repositorio: o que vale e o que esta la, nao o que
            // a resposta do POST disse que aconteceu.
            setTimeout(atualizarEstado, 2500);
        }
    });
})();

/*
 * Botao "Imprimir" do painel de telemetria.
 *
 * Proposito de negocio: levar KPIs, graficos e tabelas para o papel ou PDF sem
 * passar pelo servidor. Nao se confunde com "Exportar": um manda a TELA para a
 * impressora, o outro baixa os DADOS em arquivo.
 *
 * Invariantes: o recorte do que sai no papel e responsabilidade do @media print
 * do CSS, nao deste arquivo. Antes de chamar a impressao os graficos sao
 * redesenhados na largura de impressao - canvas nao reflui sozinho, e sem isso
 * eles saem cortados ou em branco.
 *
 * Comportamento em caso de falha: se o Chart.js nao estiver presente, imprime
 * assim mesmo, so sem o ajuste dos graficos.
 */
(function () {
    "use strict";
    var botao = document.getElementById("btn-imprimir-telemetria");
    if (!botao) {
        return;
    }
    botao.addEventListener("click", function () {
        try {
            if (window.Chart && Chart.instances) {
                Object.keys(Chart.instances).forEach(function (k) {
                    Chart.instances[k].resize();
                });
            }
        } catch (err) {
            /* imprimir e mais importante que o ajuste do grafico */
        }
        window.print();
    });
})();

/*
 * Pausa a autoatualizacao enquanto o mouse esta sobre um grafico.
 *
 * Proposito de negocio: o painel recria os graficos a cada ciclo. Se o ciclo
 * cair no instante em que o usuario esta lendo um tooltip, o grafico e
 * destruido e o tooltip some sozinho - o que se parece com "o grafico nao
 * responde ao mouse".
 *
 * Invariantes: pausa apenas a RENDERIZACAO enquanto ha ponteiro sobre a caixa;
 * o ciclo volta ao sair. Nenhum dado deixa de ser coletado - a telemetria do
 * servidor continua registrando normalmente.
 *
 * Comportamento em caso de falha: sem as caixas na pagina, nao liga nada.
 */
(function () {
    "use strict";
    var caixas = document.querySelectorAll(".tele-chart-box");
    if (!caixas.length) {
        return;
    }
    caixas.forEach(function (caixa) {
        caixa.addEventListener("mouseenter", function () {
            window.__telemetriaPausada = true;
        });
        caixa.addEventListener("mouseleave", function () {
            window.__telemetriaPausada = false;
        });
    });
})();
