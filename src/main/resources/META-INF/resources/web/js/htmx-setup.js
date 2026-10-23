(function (w) {
    "use strict";

    // Todo POST/PUT/DELETE disparado pelo htmx leva o token CSRF no cabeçalho,
    // do mesmo jeito que o csrf-client.js já faz com o fetch nativo.
    document.body.addEventListener("htmx:configRequest", (event) => {
        const token = w.CsrfClient ? w.CsrfClient.getToken() : "";
        if (token) {
            event.detail.headers["X-CSRF-Token"] = token;
        }
    });

    // Por padrão o htmx descarta respostas de erro. Os mapeadores de exceção
    // devolvem o fragmento de erro já renderizado quando a requisição vem do
    // htmx, então o 400 também deve ser trocado no alvo.
    document.body.addEventListener("htmx:beforeSwap", (event) => {
        const status = event.detail.xhr ? event.detail.xhr.status : 0;
        if (status === 400) {
            event.detail.shouldSwap = true;
            event.detail.isError = false;
            return;
        }
        // 403/429/5xx eram descartados em silêncio (padrão do htmx 2): o spinner piscava e o
        // resultado ANTERIOR ficava na tela ao lado da entrada nova — o aluno copiava o errado.
        // Agora o alvo recebe um aviso, o que também tira da tela o resultado velho.
        const mensagem = mensagemDeErro(status);
        if (mensagem) {
            event.detail.shouldSwap = true;
            event.detail.serverResponse = avisoHtml(mensagem, status);
        }
    });

    // Sem resposta (rede caiu, servidor fora): htmx não troca nada — o aviso é posto à mão.
    document.body.addEventListener("htmx:sendError", (event) => {
        const alvo = event.detail.target;
        if (alvo) {
            alvo.innerHTML = avisoHtml("Sem conexão com o servidor. Nada foi calculado — verifique a rede e tente de novo.", 0);
        }
    });

    // 413 e os demais 4xx também eram descartados: colar uma configuração grande demais não
    // mostrava nada e o resultado ANTERIOR continuava na tela (auditoria SEC-07).
    function mensagemDeErro(status) {
        if (status === 403) {
            return "A proteção do formulário expirou e acabou de ser renovada. Envie de novo.";
        }
        if (status === 413) {
            return "O texto enviado passou do tamanho que o site aceita. Nada foi calculado — envie um trecho menor.";
        }
        if (status === 429) {
            return "Muitas requisições seguidas desta rede. Aguarde um minuto e envie de novo.";
        }
        if (status >= 500) {
            return "Erro no servidor. Nada foi calculado — tente de novo em instantes.";
        }
        if (status >= 401) {
            return "O servidor recusou o pedido. Nada foi calculado — recarregue a página e tente de novo.";
        }
        return "";
    }

    // Só texto fixo e código numérico entram aqui: nada vindo da resposta vira HTML.
    function avisoHtml(texto, status) {
        const codigo = status ? " (código " + Number(status) + ")" : "";
        return '<div class="alert alert-warning d-flex align-items-center gap-2 mb-0" role="alert">'
            + '<span class="material-symbols-outlined" aria-hidden="true" translate="no">warning</span>'
            + "<span>" + texto + codigo + "</span></div>";
    }

    // Reinicializa tooltips e popovers em fragmentos HTML injetados dinamicamente via HTMX.
    document.body.addEventListener("htmx:afterSwap", (event) => {
        if (w.FieldTooltips && w.FieldTooltips.init) {
            w.FieldTooltips.init(event.detail.target || document);
        }
        if (w.FormInputs && w.FormInputs.init) {
            w.FormInputs.init(event.detail.target || document);
        }
    });
})(window);
