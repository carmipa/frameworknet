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
                fontSize: "14px"
            }
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

    const DICAS_TITULOS = [
        ["h2", "Framework de Redes", "Título do README do projeto, exibido aqui com sumário lateral e diagramas renderizados"],
        ["h3", "Sumário", "Índice numerado das seções do documento e da pergunta que cada uma responde"],
        ["h3", "Visão Geral", "Resumo do que o site cobre, do cálculo de sub-rede à geração de cenário de laboratório"],
        ["h3", "Módulos e Rotas", "Tabela de todas as rotas do site, com método HTTP e o que cada uma entrega"],
        ["h3", "Funcionalidades", "O que cada módulo faz, com seus critérios, abas e exportações"],
        ["h4", "Módulo 1", "Análise de IPv4 passo a passo: rede, broadcast, máscara, wildcard, DNS e comparador de CIDR"],
        ["h4", "Módulo 2", "Divisão de blocos em sub-redes, plano de VLANs, sumarização e conversão de faixa em CIDR"],
        ["h4", "Módulo — Trilha IPv6", "Análise, calculadora e resolução de problemas próprias para endereços e prefixos IPv6"],
        ["h4", "Módulo 3", "Catálogo de portas TCP e UDP e páginas de aprofundamento de cada protocolo"],
        ["h5", "Aprofundamentos de portas", "Páginas conceituais sobre a anatomia de uma porta e as famílias de serviços"],
        ["h5", "Aprofundamentos por protocolo", "Páginas de protocolo agrupadas nas camadas de Aplicação, Transporte e Rede"],
        ["h4", "Páginas de erro", "Como uma única página atende todos os códigos de erro HTTP e onde mora cada parte"],
        ["h4", "Módulo 4", "Projeto de endereçamento VLSM e enlaces WAN, ou análise reversa de uma configuração Cisco"],
        ["h5", "Aba", "Como a configuração Cisco colada é interpretada, corrigida com evidência e reconstruída em plano"],
        ["h5", "Exportações", "Arquivos gerados pela Resolução de Problemas para o Packet Tracer e para entrega acadêmica"],
        ["h5", "Importar turma", "Formato das colunas para colar a turma da planilha e gerar o pacote de cada aluno"],
        ["h3", "Arquitetura", "Diagramas e tabelas de como o código se organiza e por onde passa uma requisição"],
        ["h4", "Camadas por módulo", "Divisão interna de cada módulo em apresentação, aplicação, domínio e infraestrutura"],
        ["h4", "Fluxo do módulo VLSM", "Diagrama do caminho de um pedido de projeto VLSM até o plano e os scripts gerados"],
        ["h4", "Fluxo de telemetria", "Diagrama de como os eventos nascem no servidor, são gravados e exportados"],
        ["h4", "Utilitários transversais", "Classes de apoio usadas por vários módulos para higienizar e validar entradas"],
        ["h4", "Tratamento de exceções", "Como cada erro de aplicação vira uma resposta HTTP com o status adequado"],
        ["h4", "Deploy Docker", "Estágios da imagem: compilação e testes num estágio, execução enxuta no outro"],
        ["h4", "Calculadora de Sub-redes e VLANs", "Diagrama do caminho de um cálculo, do formulário até o fragmento HTML devolvido"],
        ["h4", "Camadas de proteção", "Ordem dos filtros que cada requisição atravessa: telemetria, chave admin, CSRF e limite de taxa"],
        ["h4", "Cache de APIs externas", "Como consultas a serviços externos usam memória local e Redis antes de chamar a origem"],
        ["h4", "Academia", "Como a Academia se divide em fatias independentes e quais testes vigiam essa separação"],
        ["h3", "Requisitos", "Versões de Java, Gradle e Docker necessárias para compilar e executar o projeto"],
        ["h3", "Execução", "Como subir o projeto na própria máquina ou em contêiner no servidor"],
        ["h4", "Java / Quarkus local", "Comando Gradle para rodar em modo de desenvolvimento, com recarga automática"],
        ["h4", "Docker (VPS)", "Comandos docker compose para subir e parar a aplicação no servidor"],
        ["h3", "Variáveis de Configuração", "Propriedades com valor padrão e efeito, que podem ser sobrescritas por variáveis de ambiente"],
        ["h4", "Aplicação", "Propriedades gerais: porta, limites da calculadora e caches de DNS e GeoIP"],
        ["h4", "Telemetria", "Propriedades que ligam a coleta de eventos e limitam o armazenamento"],
        ["h4", "Segurança", "Propriedades de CSRF, limite de requisições e chave administrativa"],
        ["h4", "Variáveis de ambiente", "Variáveis definidas no contêiner, algumas obrigatórias em produção"],
        ["h3", "Segurança", "Proteções do site: CSRF, chave administrativa, limite de requisições, CSP e SRI"],
        ["h3", "Telemetria e Observabilidade", "Como os eventos são registrados no servidor, correlacionados e compartilhados"],
        ["h4", "Correlação dos eventos", "Como o traceId liga entre si os eventos de uma mesma requisição"],
        ["h4", "Formato de compartilhamento", "Arquivos e endpoint que exportam os eventos no padrão de logs do OpenTelemetry"],
        ["h4", "Dataset público", "O que é removido ou anonimizado antes de publicar os dados de telemetria"],
        ["h3", "Estrutura de Pastas", "Onde mora cada parte do código-fonte, dos templates e dos arquivos estáticos"],
        ["h3", "Testes", "Suítes automatizadas por área do sistema e o que cada grupo verifica"],
        ["h4", "Testes que guardam", "Testes que reprovam o build quando arquitetura, menu ou CSP saem do padrão combinado"],
        ["h4", "Ao criar um módulo novo", "Arquivos e testes que precisam acompanhar a criação de um módulo para nada ficar órfão"],
        ["h3", "Roadmap", "Itens já entregues, marcados, e os que ainda estão planejados"],
        ["h3", "Autor", "Quem desenvolve o projeto e o link para o site pessoal"],
        ["h3", "Licença", "Termos de uso do código: licença MIT, que permite usar, modificar e redistribuir"]
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
     * Dá uma dica explicativa a cada título e cabeçalho de coluna do README renderizado.
     *
     * Propósito de negócio: o conteúdo vem de Markdown, que não carrega atributos; aqui cada seção e
     * coluna ganha um title que o adaptador de dicas converte no balão acessível.
     *
     * Invariantes do domínio: nunca sobrescreve um title já existente; só age dentro de .markdown-doc;
     * título ou tabela fora do mapa fica como está (não inventa texto genérico).
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
