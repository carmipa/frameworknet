/*
 * Calculadora de Sub-redes e VLANs — comportamento da tela.
 *
 * Todo o cálculo acontece no servidor e volta como fragmento Qute trocado pelo
 * htmx. Este arquivo cuida apenas do que é puramente visual: alternar as abas,
 * mostrar o campo do critério escolhido e copiar o bloco de CLI.
 */
(function (w) {
    "use strict";

    var ABAS_VALIDAS = ["dividir", "vlan", "agregar", "faixa"];

    function mostrarAba(aba) {
        var alvo = ABAS_VALIDAS.indexOf(aba) >= 0 ? aba : "dividir";
        document.querySelectorAll(".calc-tab-trigger").forEach(function (btn) {
            btn.classList.toggle("active", btn.dataset.calcTab === alvo);
            btn.setAttribute("aria-selected", btn.dataset.calcTab === alvo ? "true" : "false");
        });
        document.querySelectorAll(".calc-tab-panel").forEach(function (painel) {
            painel.classList.toggle("is-active", painel.dataset.calcPanel === alvo);
        });
        var url = new URL(w.location.href);
        url.searchParams.set("aba", alvo);
        w.history.replaceState(null, "", url.toString());
    }

    /*
     * Só o campo do critério escolhido fica visível, para não confundir qual valor manda.
     *
     * O campo ativo precisa ganhar "required" e os inativos precisam ficar
     * "disabled" — os dois no mesmo passo da troca. Sem isso o browser não
     * bloqueia o envio vazio (required em campo escondido trava o form em
     * silêncio, então não dá para deixá-lo fixo no HTML), o erro só aparece
     * depois da ida ao servidor e ainda registra um evento ERROR na telemetria
     * para o que é simplesmente um campo em branco.
     */
    function ligarCriterios() {
        var radios = document.querySelectorAll("input[name='criterio'][data-alvo]");
        if (!radios.length) {
            return;
        }
        var aplicar = function () {
            radios.forEach(function (radio) {
                var grupo = document.getElementById(radio.dataset.alvo);
                if (!grupo) {
                    return;
                }
                var ativo = radio.checked;
                grupo.classList.toggle("d-none", !ativo);
                grupo.querySelectorAll("input, select").forEach(function (campo) {
                    campo.disabled = !ativo;
                    campo.required = ativo;
                });
            });
        };
        radios.forEach(function (radio) {
            radio.addEventListener("change", aplicar);
        });
        aplicar();
    }

    /*
     * O botão de copiar nasce dentro do fragmento trocado pelo htmx, então a
     * escuta fica no documento (delegação) em vez de no elemento — senão o
     * primeiro swap já deixaria o botão sem handler.
     */
    function ligarCopia() {
        document.addEventListener("click", function (evento) {
            var botao = evento.target.closest(".calc-copiar");
            if (!botao) {
                return;
            }
            var origem = document.getElementById(botao.dataset.alvo);
            if (!origem || !navigator.clipboard) {
                return;
            }
            navigator.clipboard.writeText((window.FieldTooltips ? window.FieldTooltips.textoLimpo(origem) : origem.textContent) || "").then(function () {
                // O rótulo é text node solto ao lado do <span> do ícone, então
                // querySelector("span:last-child") pegaria o ÍCONE e o trocaria
                // por "Copiado", fazendo o ícone sumir. Mexer no text node.
                var rotulo = null;
                botao.childNodes.forEach(function (no) {
                    if (no.nodeType === Node.TEXT_NODE && no.textContent.trim()) {
                        rotulo = no;
                    }
                });
                if (!rotulo) {
                    return;
                }
                var original = rotulo.textContent;
                rotulo.textContent = " Copiado";
                w.setTimeout(function () {
                    rotulo.textContent = original;
                }, 1500);
            });
        });
    }

    function iniciar() {
        var raiz = document.querySelector(".calc-wrap");
        if (!raiz) {
            return;
        }
        document.querySelectorAll(".calc-tab-trigger").forEach(function (btn) {
            btn.addEventListener("click", function () {
                mostrarAba(btn.dataset.calcTab);
            });
        });
        mostrarAba(raiz.dataset.abaAtiva || "dividir");
        ligarCriterios();
        ligarCopia();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", iniciar);
    } else {
        iniciar();
    }
})(window);
