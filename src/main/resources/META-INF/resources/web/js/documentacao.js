/* Documentação: Mermaid em tema DARK (alinhado ao design) + scroll-spy do sumário. */
(function () {
    "use strict";

    function initMermaid() {
        if (typeof mermaid === "undefined") return;
        mermaid.initialize({
            startOnLoad: false,
            // Tamanho natural do desenho (texto legível) dentro de uma caixa com rolagem própria: com useMaxWidth o
            // diagrama encolhia até caber e o texto chegava a 3 px no celular (auditoria de responsividade, 22/10/2026).
            flowchart: { useMaxWidth: false }, sequence: { useMaxWidth: false }, state: { useMaxWidth: false }, class: { useMaxWidth: false }, er: { useMaxWidth: false }, mindmap: { useMaxWidth: false },
            securityLevel: "strict",
            theme: "base",
            themeVariables: {
                darkMode: true,
                background: "#0a0e16",
                mainBkg: "#0f1622",
                primaryColor: "#0f1622",
                primaryTextColor: "#eef2f8",
                primaryBorderColor: "#2dd4bf",
                secondaryColor: "#111a27",
                tertiaryColor: "#0a0e16",
                lineColor: "#5b6679",
                textColor: "#c9d1d9",
                clusterBkg: "#0c1220",
                clusterBorder: "rgba(255,255,255,0.12)",
                nodeBorder: "rgba(255,255,255,0.18)",
                edgeLabelBackground: "#0a0e16",
                fontFamily: "Inter Tight, system-ui, sans-serif",
                fontSize: "0.875rem"
            }
        });
        // Diagrama largo rola na horizontal: o contêiner recebe foco para quem usa só o teclado (WCAG 2.1.1).
        document.querySelectorAll(".markdown-doc .mermaid").forEach((d) => {
            d.setAttribute("tabindex", "0");
            d.setAttribute("role", "region");
            d.setAttribute("aria-label", "Diagrama");
        });
        try {
            mermaid.run({ querySelector: ".markdown-doc .mermaid" });
        } catch (e) {
            /* diagramas inválidos não devem quebrar a página */
        }
    }

    function initScrollSpy() {
        const links = Array.from(document.querySelectorAll(".doc-sidebar a[href^='#']"));
        const sections = links
            .map((link) => document.querySelector(link.getAttribute("href")))
            .filter(Boolean);
        if (!links.length || !sections.length) return;

        const sync = () => {
            const threshold = window.scrollY + 150;
            let activeId = sections[0].id;
            sections.forEach((sec) => {
                if (sec.offsetTop <= threshold) activeId = sec.id;
            });
            links.forEach((link) => {
                link.classList.toggle("active", link.getAttribute("href") === "#" + activeId);
            });
        };

        links.forEach((link) => {
            link.addEventListener("click", () => {
                links.forEach((l) => l.classList.remove("active"));
                link.classList.add("active");
            });
        });
        window.addEventListener("scroll", sync, { passive: true });
        sync();
    }

    // Só os títulos cujo texto logo abaixo não diz o que a dica diz (os demais saíram em 24/10 e não voltam).
    const DICAS_TITULOS = [
        ["h4", "Módulo 3", "Catálogo de portas TCP e UDP e páginas de aprofundamento de cada protocolo"],
        ["h5", "Exportações", "Arquivos gerados pela Resolução de Problemas para o Packet Tracer e para entrega acadêmica"],
        ["h4", "Deploy Docker", "Estágios da imagem: compilação e testes num estágio, execução enxuta no outro"],
        ["h4", "Java / Quarkus local", "Comando Gradle para rodar em modo de desenvolvimento, com recarga automática"],
        ["h4", "Aplicação", "Propriedades gerais: porta, limites da calculadora e caches de DNS e GeoIP"],
        ["h4", "Telemetria", "Propriedades que ligam a coleta de eventos e limitam o armazenamento"],
        ["h4", "Segurança", "Propriedades de CSRF, limite de requisições e chave administrativa"],
        ["h4", "Variáveis de ambiente", "Variáveis definidas no contêiner, algumas obrigatórias em produção"],
        ["h3", "Estrutura de Pastas", "Onde mora cada parte do código-fonte, dos templates e dos arquivos estáticos"],
        ["h3", "Roadmap", "Itens já entregues, marcados, e os que ainda estão planejados"]
    ];

    const DICAS_COLUNAS = {
        "#|Seção|O que responde": [
            "Número da seção na ordem em que aparece no documento",
            "Parte do documento; o sumário lateral leva direto até ela",
            "Assunto que a seção esclarece, para decidir se vale ler"],
        "Módulo|Rota|Método|Descrição": [
            "Área funcional do site à qual a rota pertence",
            "Caminho da URL atendido pelo servidor, relativo ao domínio do site",
            "Verbo HTTP aceito: GET lê uma página ou dado, POST envia formulário ou cálculo",
            "O que a rota entrega: página, fragmento HTML, JSON ou arquivo exportado"],
        "Critério|Entrada|Exemplo": [
            "Forma de pedir a divisão do bloco em sub-redes",
            "Valor que você informa na calculadora para esse critério",
            "Caso numérico com o resultado que a calculadora devolve"],
        "Camada|Protocolos": [
            "Camada do modelo TCP/IP em que os protocolos atuam",
            "Protocolos dessa camada que têm página de aprofundamento própria"],
        "Página|Rota|Conteúdo": [
            "Protocolo tratado na página de aprofundamento",
            "Endereço da página no site",
            "Principais tópicos que a página explica"],
        "Peça|Onde": [
            "Parte da implementação das páginas de erro",
            "Arquivo ou pasta do código-fonte em que essa parte mora"],
        "Aba|Rota|Vai de|Para": [
            "Modo de trabalho da Resolução de Problemas",
            "Endereço que abre essa aba diretamente",
            "O que você fornece como ponto de partida",
            "O que a ferramenta entrega como resultado"],
        "Classe|O que acontece": [
            "Tipo de achado ao analisar a configuração Cisco colada",
            "Como a ferramenta trata esse tipo de achado no resultado"],
        "Ação (action_type)|Arquivo|Conteúdo": [
            "Valor do parâmetro action_type enviado ao pedir a exportação",
            "Nome do arquivo baixado",
            "O que vem dentro do arquivo exportado"],
        "Camada|Responsabilidade|Exemplos": [
            "Pacote interno de cada módulo, da borda HTTP até o domínio",
            "Tipo de código que pertence a essa camada",
            "Classes ou pastas reais do projeto que ficam nessa camada"],
        "Pacote|Resources principais": [
            "Pacote Java do módulo, dentro de org.framework.net",
            "Rotas HTTP publicadas pelas classes Resource desse pacote"],
        "Classe|Uso principal": [
            "Utilitário compartilhado entre os módulos",
            "Onde e para que o utilitário é usado"],
        "Mapper|Exceção|Status típico": [
            "Classe que converte a exceção em resposta HTTP",
            "Erro de aplicação capturado por esse mapper",
            "Código HTTP devolvido: 400 para entrada inválida, 500 para falha interna"],
        "Etapa|Detalhe": [
            "Fase ou aspecto da imagem Docker e do deploy",
            "Imagem base, comando, volume ou variável usada nessa fase"],
        "Guarda|O que impede": [
            "Teste automatizado que reprova o build quando a Academia sai do desenho previsto",
            "Situação que o teste detecta e bloqueia"],
        "Propriedade|Padrão|Descrição": [
            "Chave de configuração lida do application.properties ou de variável de ambiente",
            "Valor usado quando a propriedade não é definida",
            "Efeito da propriedade no comportamento da aplicação"],
        "Propriedade|Padrão (dev)|Descrição": [
            "Chave de configuração lida do application.properties ou de variável de ambiente",
            "Valor do perfil de desenvolvimento; em produção os segredos vêm de variável de ambiente",
            "Efeito da propriedade no comportamento da aplicação"],
        "Artefato|Formato|Descrição": [
            "Arquivo ou endpoint que entrega a telemetria",
            "Estrutura dos dados, segundo o modelo de logs do OpenTelemetry",
            "Para que o artefato serve e como é gravado"],
        "Dado|Tratamento": [
            "Informação presente nos eventos brutos de telemetria",
            "O que acontece com o dado antes de entrar no conjunto público"],
        "Área|Exemplos de testes": [
            "Módulo ou aspecto do sistema coberto pelos testes",
            "Classes de teste JUnit que verificam essa área"]
    };

    function textoLimpo(el) {
        return (el.textContent || "").replace(/\s+/g, " ").trim().replace(/^[^\p{L}\p{N}#]+/u, "");
    }

    /**
     * Dá uma dica explicativa aos cabeçalhos de coluna do README renderizado e aos títulos de seção cujo texto logo
     * abaixo não diz o que a dica diz.
     *
     * Propósito de negócio: o conteúdo vem de Markdown, que não carrega atributos; aqui o título ou a coluna ganha um
     * title que o adaptador de dicas converte no balão acessível, com o "?" à vista.
     *
     * Invariantes do domínio: nunca sobrescreve um title já existente; só age dentro de .markdown-doc; título ou
     * coluna fora do mapa (ou com texto vazio no mapa) fica como está — não inventa texto genérico.
     *
     * Comportamento em caso de falha: sem .markdown-doc (página de erro) não faz nada; não lança exceção.
     */
    function initDicas() {
        const doc = document.querySelector(".markdown-doc");
        if (!doc) return;
        doc.querySelectorAll("h2, h3, h4, h5").forEach((h) => {
            if (h.hasAttribute("title")) return;
            const tag = h.tagName.toLowerCase();
            const texto = textoLimpo(h);
            const achado = DICAS_TITULOS.find((d) => d[0] === tag && texto.startsWith(d[1]));
            if (achado) h.setAttribute("title", achado[2]);
        });
        doc.querySelectorAll("table").forEach((tabela) => {
            const ths = Array.from(tabela.querySelectorAll("thead th"));
            const dicas = DICAS_COLUNAS[ths.map(textoLimpo).join("|")];
            if (!dicas) return;
            ths.forEach((th, i) => {
                if (!th.hasAttribute("title") && dicas[i]) th.setAttribute("title", dicas[i]);
            });
        });
    }

    function boot() {
        initDicas();
        initMermaid();
        initScrollSpy();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", boot);
    } else {
        boot();
    }
})();
