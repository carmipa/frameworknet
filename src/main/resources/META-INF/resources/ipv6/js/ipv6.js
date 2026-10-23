/*
 * Calculadora IPv6 — comportamento visual do módulo (próprio da página, desacoplado).
 * O cálculo acontece no servidor e volta como fragmento trocado pelo htmx; aqui ficam só a
 * navegação por abas (mesmo padrão da Análise IPv4) e o botão Limpar.
 */
(function () {
    "use strict";

    function limparFormulario(botao) {
        var form = botao.closest("form");
        if (!form) {
            return;
        }
        form.querySelectorAll("input[type='text']").forEach(function (campo) {
            campo.value = "";
        });
        var alvo = form.getAttribute("hx-target");
        if (alvo) {
            var saida = document.querySelector(alvo);
            if (saida) {
                saida.innerHTML =
                    '<div class="calc-placeholder"><span class="material-symbols-outlined" aria-hidden="true" translate="no">' +
                    'backspace</span><p class="mb-0">Campos limpos. Informe novos valores e calcule.</p></div>';
            }
        }
        var primeiro = form.querySelector("input[type='text']");
        if (primeiro) {
            primeiro.focus();
        }
    }

    function bootAbas() {
        var root = document.querySelector(".ipv6-wrap");
        if (!root) {
            return;
        }
        var gatilhos = root.querySelectorAll(".tab-trigger");
        var paineis = root.querySelectorAll(".tab-panel");

        function mostrar(id) {
            gatilhos.forEach(function (g) {
                g.classList.toggle("active", g.dataset.tab === id);
            });
            paineis.forEach(function (p) {
                p.classList.toggle("active", p.dataset.tabPanel === id);
            });
        }

        gatilhos.forEach(function (g) {
            g.addEventListener("click", function () {
                mostrar(g.dataset.tab);
            });
        });
        mostrar(root.getAttribute("data-active-tab") || "analise");
    }

    document.addEventListener("click", function (evento) {
        var botao = evento.target.closest("[data-limpar]");
        if (botao) {
            limparFormulario(botao);
        }
    });

    // Linha criada depois do carregamento não passou pelo FormInputs/FieldTooltips: sem isto o
    // VLAN ID aceitava letras e o erro só aparecia no servidor.
    function ativarLinha(row) {
        if (window.FormInputs && window.FormInputs.init) {
            window.FormInputs.init(row);
        }
        if (window.FieldTooltips && window.FieldTooltips.init) {
            window.FieldTooltips.init(row);
        }
    }

    // Localidades dinâmicas do Projetar: adicionar/remover linhas (como no IPv4).
    function novaLinhaLocal() {
        var row = document.createElement("div");
        row.className = "row g-2 align-items-end proj-local-row mb-2";
        row.innerHTML =
            '<div class="col-lg-10 col-9"><input type="text" name="local" class="aed-input form-control" ' +
            'placeholder="Nome da localidade / VLAN" autocomplete="off" required></div>' +
            '<div class="col-lg-2 col-3 d-grid"><button type="button" class="aed-btn aed-btn-danger btn-sm proj-remove-local" ' +
            'title="Remover esta localidade"><span class="material-symbols-outlined" aria-hidden="true" translate="no">delete</span></button></div>';
        return row;
    }

    document.addEventListener("click", function (evento) {
        var add = evento.target.closest("#proj-add-local");
        if (add) {
            var cont = document.getElementById("proj-locais-container");
            if (cont) {
                var row = novaLinhaLocal();
                cont.appendChild(row);
                ativarLinha(row);
                var inp = row.querySelector("input");
                if (inp) {
                    inp.focus();
                }
            }
            return;
        }
        var rem = evento.target.closest(".proj-remove-local");
        if (rem) {
            var cont2 = document.getElementById("proj-locais-container");
            var rows = cont2 ? cont2.querySelectorAll(".proj-local-row") : [];
            if (rows.length > 1) {
                rem.closest(".proj-local-row").remove();
            } else {
                // mantém ao menos uma linha; só limpa o campo
                var only = rem.closest(".proj-local-row").querySelector("input");
                if (only) {
                    only.value = "";
                }
            }
        }
    });

    // Baixar / copiar o plano do Projetar (texto vem no fragmento htmx).
    document.addEventListener("click", function (evento) {
        var baixar = evento.target.closest("[data-baixar-plano]");
        if (baixar) {
            var el = document.getElementById("ipv6-plano-texto");
            var texto = el ? el.value : "";
            if (!texto) {
                return;
            }
            var blob = new Blob([texto], { type: "text/plain;charset=utf-8" });
            var url = URL.createObjectURL(blob);
            var a = document.createElement("a");
            a.href = url;
            a.download = "plano-ipv6.txt";
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
            return;
        }
        var copiar = evento.target.closest("[data-copiar-plano]");
        if (copiar) {
            var el2 = document.getElementById("ipv6-plano-texto");
            var t2 = el2 ? el2.value : "";
            if (t2) {
                var rotulo = copiar.innerHTML; // preserva o rótulo original (Copiar plano / configuração)
                navigator.clipboard.writeText(t2).then(
                    function () { copiar.innerHTML = HtmlEscape.icone("check", "Copiado"); },
                    function () { copiar.innerHTML = HtmlEscape.icone("error", "Falhou"); }
                );
                setTimeout(function () { copiar.innerHTML = rotulo; }, 1500);
            }
        }
    });

    // VLANs dinâmicas da Calculadora: adicionar/remover linhas (id + nome), como no Projetar.
    function novaLinhaVlan() {
        var row = document.createElement("div");
        row.className = "row g-2 align-items-end vlan-row mb-2";
        row.innerHTML =
            '<div class="col-lg-3 col-4"><input type="text" name="vlanId" class="aed-input form-control input-numeric" ' +
            'inputmode="numeric" maxlength="4" placeholder="ID" required></div>' +
            '<div class="col-lg-7 col-6"><input type="text" name="vlanNome" class="aed-input form-control" ' +
            'placeholder="Nome da VLAN" autocomplete="off" required></div>' +
            '<div class="col-lg-2 col-2 d-grid"><button type="button" class="aed-btn aed-btn-danger btn-sm vlan-remove" ' +
            'title="Remover"><span class="material-symbols-outlined" aria-hidden="true" translate="no">delete</span></button></div>';
        return row;
    }

    document.addEventListener("click", function (evento) {
        var add = evento.target.closest("#vlan-add");
        if (add) {
            var cont = document.getElementById("vlan-container");
            if (cont) {
                var row = novaLinhaVlan();
                cont.appendChild(row);
                ativarLinha(row);
                var inp = row.querySelector("input");
                if (inp) {
                    inp.focus();
                }
            }
            return;
        }
        var rem = evento.target.closest(".vlan-remove");
        if (rem) {
            var cont2 = document.getElementById("vlan-container");
            var rows = cont2 ? cont2.querySelectorAll(".vlan-row") : [];
            if (rows.length > 1) {
                rem.closest(".vlan-row").remove();
            } else {
                rem.closest(".vlan-row").querySelectorAll("input").forEach(function (i) { i.value = ""; });
            }
        }
    });

    // Diagrama de arquitetura: renderiza o Mermaid que veio no fragmento htmx.
    function renderizarMermaid(escopo) {
        if (!window.mermaid) {
            return;
        }
        var alvos = (escopo || document).querySelectorAll(".mermaid:not([data-processed])");
        if (!alvos.length) {
            return;
        }
        try {
            window.mermaid.run({ nodes: alvos });
        } catch (e) {
            /* diagrama é auxiliar; não quebra a página */
        }
    }

    // ---------- Histórico da Análise (client-side, localStorage — só neste navegador) ----------
    var HIST_KEY = "ipv6-analise-hist";

    function histLer() {
        try {
            var raw = window.localStorage.getItem(HIST_KEY);
            var arr = raw ? JSON.parse(raw) : [];
            return Array.isArray(arr) ? arr : [];
        } catch (e) {
            return [];
        }
    }

    function histSalvar(endereco) {
        if (!endereco) {
            return;
        }
        try {
            var arr = histLer().filter(function (it) { return it.e !== endereco; });
            arr.unshift({ e: endereco, t: Date.now() });
            if (arr.length > 20) {
                arr = arr.slice(0, 20);
            }
            window.localStorage.setItem(HIST_KEY, JSON.stringify(arr));
        } catch (e) {
            /* modo privado / storage indisponível — histórico apenas não persiste */
        }
    }

    function escaparHtml(s) {
        return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }

    function histRender() {
        var body = document.getElementById("ipv6-hist-body");
        if (!body) {
            return;
        }
        var arr = histLer();
        if (!arr.length) {
            body.innerHTML = '<tr><td colspan="3" class="text-secondary small">Sem histórico ainda — faça uma análise.</td></tr>';
            return;
        }
        body.innerHTML = arr.map(function (it) {
            var quando = new Date(it.t).toLocaleString("pt-BR");
            var e = escaparHtml(it.e);
            return "<tr class=\"small\"><td class=\"text-secondary\">" + escaparHtml(quando) + "</td>"
                + "<td class=\"font-monospace\">" + e + "</td>"
                + "<td><button type=\"button\" class=\"aed-btn aed-btn-neutral btn-sm\" data-replay=\"" + e
                + "\"><span class=\"material-symbols-outlined\">replay</span> Replay</button></td></tr>";
        }).join("");
    }

    document.addEventListener("click", function (evento) {
        var rep = evento.target.closest("[data-replay]");
        if (rep) {
            var end = rep.getAttribute("data-replay");
            var trig = document.querySelector('.tab-trigger[data-tab="analise"]');
            if (trig) {
                trig.click();
            }
            var campo = document.getElementById("ipv6Endereco");
            if (campo) {
                campo.value = end;
            }
            var form = document.getElementById("formCalc");
            var btn = form && form.querySelector('button[type="submit"]');
            if (btn) {
                // Com uma análise ainda em andamento o botão está desabilitado (hx-disabled-elt) e o
                // clique se perdia: o campo mostrava o endereço do replay e a tela, o resultado anterior.
                if (btn.disabled) {
                    document.addEventListener("htmx:afterRequest", function () {
                        setTimeout(function () { btn.click(); }, 0);
                    }, { once: true });
                } else {
                    btn.click();
                }
            }
            return;
        }
        var limpar = evento.target.closest("#ipv6-hist-limpar");
        if (limpar) {
            try { window.localStorage.removeItem(HIST_KEY); } catch (e) { /* ignore */ }
            histRender();
        }
    });

    document.addEventListener("htmx:afterSwap", function (e) {
        if (e.target && e.target.id === "saidaProjetar") {
            renderizarMermaid(e.target);
        }
        if (e.target && e.target.id === "saidaCalc") {
            // Registra o endereço que o SERVIDOR analisou (vem no próprio fragmento), não o que está
            // no campo agora — o aluno pode ter digitado outro enquanto a resposta chegava.
            var analisado = e.target.querySelector(".texto-copia-oculto");
            var endereco = analisado ? (analisado.getAttribute("data-endereco") || "").trim() : "";
            if (endereco) {
                histSalvar(endereco);
                histRender();
            }
        }
    });

    // Copiar resultado da Análise: lê a textarea oculta que vem dentro do fragmento htmx trocado.
    function bootCopiar() {
        var btn = document.getElementById("btn-copiar-resultado");
        if (!btn) {
            return;
        }
        btn.addEventListener("click", function () {
            var el = resultadoDaAbaAtiva();
            var texto = el ? el.value : "";
            if (!texto) {
                btn.textContent = "Sem resultado";
                setTimeout(function () { btn.innerHTML = '<span class="material-symbols-outlined" translate="no" aria-hidden="true">content_copy</span> Copiar resultado'; }, 1500);
                return;
            }
            navigator.clipboard.writeText(texto).then(
                function () { btn.innerHTML = HtmlEscape.icone("check", "Copiado"); },
                function () { btn.innerHTML = HtmlEscape.icone("error", "Falhou"); }
            );
            setTimeout(function () { btn.innerHTML = '<span class="material-symbols-outlined" translate="no" aria-hidden="true">content_copy</span> Copiar resultado'; }, 1500);
        });
    }

    /** Resultado (textarea oculta) da aba visível; sem resultado nela, o da aba Análise. */
    function resultadoDaAbaAtiva() {
        var ativo = document.querySelector(".ipv6-wrap .tab-panel.active .texto-copia-oculto");
        return ativo || document.querySelector('.ipv6-wrap .tab-panel[data-tab-panel="analise"] .texto-copia-oculto');
    }

    // Mantém os links de Exportar JSON/PDF apontando para o endereço do resultado que o aluno está
    // vendo (antes: sempre o campo da aba Análise, mesmo na aba Domínio).
    function sincronizarExport() {
        var linkJson = document.getElementById("ipv6-export-json");
        var linkPdf = document.getElementById("ipv6-export-pdf");
        if (!linkJson && !linkPdf) {
            return;
        }
        var resultado = resultadoDaAbaAtiva();
        var campo = document.getElementById("ipv6Endereco");
        var endereco = resultado ? resultado.getAttribute("data-endereco") : (campo ? campo.value : "");
        var valor = encodeURIComponent(endereco || "");
        if (linkJson) {
            linkJson.setAttribute("href", "/ipv6/export/json?endereco=" + valor);
        }
        if (linkPdf) {
            linkPdf.setAttribute("href", "/ipv6/export/pdf?endereco=" + valor);
        }
    }

    document.addEventListener("htmx:afterSwap", function () {
        sincronizarExport();
    });
    document.addEventListener("click", function (e) {
        if (e.target.closest(".ipv6-wrap .tab-trigger")) {
            setTimeout(sincronizarExport, 0);
        }
    });

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", function () {
            bootAbas();
            bootCopiar();
            sincronizarExport();
            histRender();
        });
    } else {
        bootAbas();
        bootCopiar();
        sincronizarExport();
        histRender();
    }
})();
