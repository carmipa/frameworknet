(function () {
    const topologyDetails = window.__topologyDetails || {};
    let selectedTopologyNodeId = null;
    const esc = (w) => (window.HtmlEscape && window.HtmlEscape.esc) ? window.HtmlEscape.esc(w) : String(w == null ? "" : w).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);  // fallback também escapa (BACK-16)

    function applySelectedTopologyNode() {
        const allNodes = document.querySelectorAll(".topology-wrap .mermaid svg g.node");
        allNodes.forEach((node) => node.classList.remove("topology-node-selected"));
        if (!selectedTopologyNodeId) return;
        allNodes.forEach((node) => {
            const nodeIdAttr = node.getAttribute("id") || "";
            if (nodeIdAttr.includes("-" + selectedTopologyNodeId + "-") || nodeIdAttr.endsWith(selectedTopologyNodeId)) {
                node.classList.add("topology-node-selected");
            }
        });
    }

    window.showTopologyDetail = function (nodeId) {
        const box = document.getElementById("topology-detail-box");
        if (!box) return;
        selectedTopologyNodeId = nodeId;
        applySelectedTopologyNode();
        const detail = topologyDetails[nodeId];
        if (!detail) {
            box.innerHTML = '<span class="text-warning">Sem detalhes para o elemento ' + esc(nodeId) + '.</span>';
            return;
        }
        if (detail.type === "router") {
            box.innerHTML =
                '<div><strong class="text-light">' + esc(detail.title) + '</strong> · <span class="text-info">LAN ' + esc(detail.network) + '</span></div>' +
                '<div>Gateway: <strong>' + esc(detail.gateway) + '</strong> | Máscara: <strong>' + esc(detail.mask) + '</strong> | Wildcard: <strong>' + esc(detail.wildcard) + '</strong></div>' +
                '<div>Faixa de hosts: <strong>' + esc(detail.host_range) + '</strong></div>' +
                '<div>Capacidade: <strong>' + esc(detail.hosts_required) + '</strong> necessários / <strong>' + esc(detail.hosts_supported) + '</strong> suportados</div>';
            return;
        }
        if (detail.type === "switch") {
            box.innerHTML =
                '<div><strong class="text-light">' + esc(detail.title) + '</strong> · <span class="text-info">LAN ' + esc(detail.network || "") + '</span></div>' +
                '<div>Gateway da LAN: <strong>' + esc(detail.gateway || "—") + '</strong></div>' +
                '<div class="small text-secondary mb-0">' + esc(detail.note_l2 || "Dois PCs ligados ao switch para testes de ping entre localidades (Packet Tracer).") + '</div>';
            return;
        }
        if (detail.type === "host") {
            let hostHtml =
                '<div><strong class="text-light">' + esc(detail.title) + '</strong></div>' +
                '<div>LAN: <span class="text-info">' + esc(detail.network || "") + '</span> · Gateway: <strong>' + esc(detail.gateway || "—") + '</strong></div>';
            if (detail.suggested_ip) {
                hostHtml += '<div>IP no diagrama (referência DHCP/estático): <strong class="text-warning">' + esc(detail.suggested_ip) + '</strong></div>';
            }
            hostHtml += '<div class="small text-secondary mb-0">Obter IP por DHCP no roteador; usar ping para outras filiais.</div>';
            box.innerHTML = hostHtml;
            return;
        }
        box.innerHTML =
            '<div><strong class="text-light">' + esc(detail.title) + '</strong> · <span class="text-warning">WAN ' + esc(detail.network) + '</span></div>' +
            '<div>Máscara: <strong>' + esc(detail.mask) + '</strong> | Wildcard: <strong>' + esc(detail.wildcard) + '</strong></div>' +
            '<div>Lado A: <strong>' + esc(detail.endpoint_a) + '</strong></div>' +
            '<div>Lado B: <strong>' + esc(detail.endpoint_b) + '</strong></div>';
    };

    // securityLevel "strict" (mantido: defesa contra rótulo malicioso) DESLIGA as diretivas `click`
    // do Mermaid — o painel "clique em um roteador" nunca abria. O clique é ligado aqui, depois da
    // renderização, pelo id do nó (…flowchart-<id>-<n>), só para ids que têm detalhe no mapa.
    function ligarCliquesDaTopologia() {
        document.querySelectorAll(".topology-wrap .mermaid svg g.node").forEach((node) => {
            // Mermaid 10: "flowchart-R_1-0"; Mermaid 11: "mermaid-<ts>-flowchart-R_1-0".
            const achado = /flowchart-(.+)-\d+$/.exec(node.getAttribute("id") || "");
            const bruto = achado ? achado[1] : "";
            if (!bruto || !topologyDetails[bruto] || node.dataset.cliqueLigado === "1") return;
            node.dataset.cliqueLigado = "1";
            node.style.cursor = "pointer";
            node.setAttribute("tabindex", "0");
            node.setAttribute("role", "button");
            node.setAttribute("aria-label", "Detalhes de " + bruto);
            node.addEventListener("click", () => window.showTopologyDetail(bruto));
            node.addEventListener("keydown", (ev) => {
                if (ev.key === "Enter" || ev.key === " ") {
                    ev.preventDefault();
                    window.showTopologyDetail(bruto);
                }
            });
        });
        applySelectedTopologyNode();
    }

    if (typeof mermaid !== "undefined") {
        mermaid.initialize({ startOnLoad: false, theme: "dark", securityLevel: "strict", flowchart: { useMaxWidth: false }, sequence: { useMaxWidth: false }, state: { useMaxWidth: false }, class: { useMaxWidth: false }, er: { useMaxWidth: false }, mindmap: { useMaxWidth: false } });
        Promise.resolve(mermaid.run({ querySelector: ".topology-wrap .mermaid" }))
            .then(ligarCliquesDaTopologia)
            .catch((erro) => console.warn("Diagrama de topologia não renderizou:", erro));
    }

    const container = document.getElementById("locations-container");
    const addButton = document.getElementById("btn-add-location");
    if (!container || !addButton) return;

    function updateRemoveButtonsState() {
        const rows = [...container.querySelectorAll(".location-row")];
        rows.forEach((row) => {
            const removeButton = row.querySelector(".btn-remove-location");
            if (removeButton) removeButton.disabled = rows.length <= 1;
        });
    }

    function bindAllHostsQtyInputs(root) {
        const scope = root || document;
        if (window.FormInputs) {
            window.FormInputs.init(scope);
            return;
        }
        scope.querySelectorAll('input[name="loc_hosts"]').forEach((input) => {
            input.addEventListener("input", () => {
                const digits = input.value.replace(/\D/g, "");
                if (input.value !== digits) {
                    input.value = digits;
                }
            });
        });
    }

    function createLocationRow(defaultName, defaultHosts) {
        const row = document.createElement("div");
        row.className = "row g-2 align-items-end location-row mb-2";
        row.innerHTML =
            '<div class="col-lg-7 col-md-12">' +
            '<label class="form-label text-secondary" title="Identifica a unidade no plano de endereços, nos scripts dos roteadores e na topologia gerada">Nome da localidade</label>' +
            '<input type="text" name="loc_name" aria-label="Nome da localidade" class="form-control" value="' + (defaultName || "") + '" data-bs-toggle="tooltip" data-bs-placement="top" title="Nome da unidade (ex.: Matriz, Filial I, CPD)." required>' +
            '</div>' +
            '<div class="col-lg-3 col-md-6">' +
            '<label class="form-label text-secondary" title="Quantos dispositivos a LAN desta localidade precisa; o cálculo escolhe a menor sub-rede que os comporta">Qtd. hosts <span class="text-secondary">(inteiro)</span></label>' +
            '<input type="text" name="loc_hosts" aria-label="Qtd. hosts (inteiro)" class="form-control input-numeric" inputmode="numeric" maxlength="6" value="' + (defaultHosts || "") + '" placeholder="ex.: 400" data-bs-toggle="tooltip" data-bs-placement="top" title="Quantidade de hosts necessária (número inteiro). Não use endereço IP aqui." required>' +
            '</div>' +
            '<div class="col-lg-2 col-md-6 d-grid">' +
            '<div class="form-label text-secondary spacer-label d-none d-md-block" aria-hidden="true">&nbsp;</div>' +
            '<button type="button" class="aed-btn aed-btn-danger btn-remove-location" data-bs-toggle="tooltip" data-bs-placement="top" title="Tira esta localidade do cenário; fica desabilitado quando só resta uma, porque o cálculo precisa de ao menos uma"><span class="material-symbols-outlined" translate="no" aria-hidden="true">delete</span> Remover</button>' +
            '</div>';
        return row;
    }

    addButton.addEventListener("click", () => {
        const total = container.querySelectorAll(".location-row").length;
        const row = createLocationRow("Filial " + total, "");
        container.appendChild(row);
        bindAllHostsQtyInputs(row);
        if (window.FieldTooltips) {
            window.FieldTooltips.init(row);
        }
        updateRemoveButtonsState();
    });

    container.addEventListener("click", (event) => {
        const button = event.target.closest(".btn-remove-location");
        if (!button) return;
        const rows = container.querySelectorAll(".location-row");
        if (rows.length <= 1) return;
        button.closest(".location-row").remove();
        updateRemoveButtonsState();
    });

    updateRemoveButtonsState();
    bindAllHostsQtyInputs(document);
    if (window.FieldTooltips) {
        window.FieldTooltips.init(document);
    }
    document.getElementById("btn-limpar-resolucao")?.addEventListener("click", () => {
        window.location.assign("/resolucao-problemas");
    });
})();
