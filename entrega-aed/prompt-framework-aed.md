# Prompt de Criação — Framework de Redes A&D (Análise Didática Avançada)

> Cole TODO o bloco abaixo no seu agente de IA (Gemini, Claude, Cursor, etc.).
> Anexe as imagens da pasta `imagens/` como referência visual.

---

Você é um engenheiro frontend sênior especialista em ferramentas técnicas de redes e cibersegurança. Reconstrua o **frontend web completo** do "FRAMEWORK DE REDES — ANÁLISE DIDÁTICA | AVANÇADA" (A&D, projeto FIAP 2026, backend **Java 25 / Quarkus**). São **6 telas principais** que compartilham a MESMA casca (top-nav) e o MESMO sistema de design "command-center" dark. Siga TODAS as regras. O resultado deve parecer um produto técnico premium e coeso — não um terminal genérico verde-sobre-preto.

## 1. Stack
- HTML + CSS + JavaScript puro (ou React, se o projeto já usar). Sem framework de CSS.
- **Chart.js v4** (CDN) para os gráficos da Telemetria.
- **Material Symbols Outlined** para TODOS os ícones — nunca emojis nem SVG desenhado à mão.
- Fontes: `Space Grotesk` (títulos e números grandes), `Inter Tight` (texto/UI), `JetBrains Mono` (IPs, máscaras, portas, códigos, timestamps).

## 2. Casca compartilhada (todas as telas)
- **Top-nav sticky** com fundo `rgba(6,8,15,0.82)` + blur e borda inferior sutil:
  - Esquerda: logo quadrado gradiente teal→ciano com "A&D", label "MENU PRINCIPAL" (uppercase, minúsculo) e "Framework de Redes".
  - Direita: 6 abas (ícone + label): **Análise Didática** (`insights`), **Portas** (`settings_ethernet`), **Protocolos** (`lan`), **Resolução (VLSM+WAN)** (`extension`), **Telemetria** (`monitoring`), **Documentação** (`menu_book`). Aba ativa = pílula com gradiente teal→ciano e texto escuro; inativas = fundo translúcido com borda sutil.
- Navegação troca a tela SEM recarregar (SPA); cada troca rola para o topo.
- **Rodapé** em todas as telas: "Desenvolvido por: Paulo André Carminati · RM570877 · 1-TDCPV · CyberSegurança · FIAP/2026" + linha "github.com/carmipa · Java + Quarkus".

## 3. Sistema visual (dark, coeso — NENHUM card branco)
- Página `#06080f` com gradientes radiais sutis (teal no canto superior direito, indigo no superior esquerdo).
- **Cards:** `linear-gradient(180deg,#0f1622,#0a0e16)`, borda `1px solid rgba(255,255,255,0.07)`, `border-radius:16px`, padding ~22–24px. Todos iguais. Cada card de conteúdo abre com um "eyebrow" (rótulo uppercase colorido pequeno) acima do título.
- Texto: primário `#eef2f8`, secundário `#8b97ad`, faint `#5b6679`.
- Accent teal `#2dd4bf`; indigo de ação `#6366f1`. Semântica de risco: Crítico `#f43f5e`, Atenção `#f59e0b`, Seguro `#34d399`/`#10b981`, Didático/Médio `#60a5fa`/`#3b82f6`.
- Inputs/selects/textarea: fundo `#0a0e16`, borda `rgba(255,255,255,0.10)`, radius 10px, texto em `JetBrains Mono` quando for valor técnico.

## 4. As 6 telas

**4.1 Análise Didática** (tela inicial):
- **Hero** (grid `420px | 1fr`): à esquerda um slot de imagem para a arte "A&D" (placeholder arrastar-e-soltar, radius 16); à direita título "Framework de Redes — Análise Didática", subtítulo, e chips (Bitwise AND, RFC1918, Wildcard, Régua Sub-redes, Cisco CLI) coloridos.
- **Seletor de modo:** grid de 8 botões-modo (ícone + label), o ativo destacado em teal: CIDR, Máscara, Wildcard, Auto CIDR, Domínio, IPv6, Comparador, Região Geo. Trocar o modo muda o rótulo do campo de entrada.
- **Linha de entrada:** campo principal (Endereço IPv4/IPv6/Domínio/IP conforme modo), campo CIDR/Máscara, campo Sub-redes, botão "Executar" (gradiente teal). Abaixo, 4 botões de ação full-width: Exportar JSON (teal), Exportar PDF (azul), Copiar resultado (neutro), Limpar Tela (vermelho).
- **Banner de resultado:** card com glow, mostrando a classe em número gigante (ex.: "C /24") em teal + descrição ("1º octeto do IP: 192 · máscara padrão /24").
- **Decomposição em 32 bits:** 4 cards de octeto, cada um com 8 quadradinhos de bit (1 = teal preenchido, 0 = apagado) + rótulo "Octeto N · valor".
- **Resultado + Capacidade** (grid `1.15fr | 1fr`): lista de detalhes (Máscara, Wildcard, Rede, IP host, Broadcast, Gateway, Tipo de IP RFC1918, Classe) cada linha com ícone; e 4 mini-cards de capacidade (Hosts úteis, Bits de host, Total IPs, Prefixo).
- **A área de resultado muda conforme o modo selecionado** (as demais seções — banner de classe, decomposição de bits — só aparecem nos modos CIDR/Máscara/Wildcard/Auto/Domínio):
  - **IPv6:** card "Resultado IPv6" com tabela de 2 colunas (rótulo com ícone | valor mono) listando: IPv6 informado, Compactação, Expansão (endereço completo em 8 grupos), Tipo (Global unicast / Link-local), Faixa (2000::/3 ou fe80::/10), Uso, Roteável na internet, Prefixo sugerido (/64), Rede estimada, Zone index, Primeiros/Últimos 64 bits, Total de bits (128), Reverse DNS (PTR ...ip6.arpa), Sinais especiais, Resumo GRC. Linhas zebradas.
  - **Comparador:** card "Comparador — IP X" com duas colunas lado a lado (ex.: /20 e /24), cada uma mostrando Máscara, Rede, Broadcast, Hosts úteis, Pulo.
  - **Região Geográfica (GeoIP):** card com cabeçalho (bandeira do país + IP + badge IPv4 + badge de risco), grade de células (País, Estado/Região, Cidade, Coordenadas, ISP/Provedor, Tipo de Conexão) e uma seção "Histórico GeoIP" em tabela (Horário, IP, País, Cidade, ISP, Risco).
- **Histórico de análises:** tabela (Data, Modo, Entrada, Rede, Tema, botão Replay).

**4.2 Portas** — Catálogo de Portas TCP/UDP:
- Título centralizado + subtítulo. Barra de estatísticas (Total, Críticas, Atenção, Seguras) com números coloridos.
- Busca em tempo real + botões JSON/PDF.
- **DataGrid** com colunas: Porta (mono teal), Serviço, Transporte, Categoria, Risco (badge colorido com ponto), Recomendação, Alternativa segura (verde). Popular com ~15 portas reais (FTP 20/21, SSH 22, Telnet 23, SMTP 25, DNS 53, DHCP 67/68, TFTP 69, HTTP 80, POP3 110, NTP 123, IMAP 143, HTTPS 443, MySQL 3306, RDP 3389, HTTP-Alt 8080), cada uma com risco correto. Busca filtra o dataset completo; **paginação** (~8/página) com contador e setas.

**4.3 Protocolos** — Catálogo de Protocolos de Rede:
- Título + subtítulo. Busca em tempo real.
- **DataGrid** com colunas: Protocolo (mono indigo), Camada, Cripto (nota tipo "9/9"), Transporte, Portas, Segurança, Risco (badge). Popular com ~12 protocolos (ARP, FTP, SSH, DNS, HTTP, HTTPS, TLS, OSPFv2, EIGRP, BGP, SNMP, IPsec).

**4.4 Resolução (VLSM + WAN):**
- Título "Resolução de Problemas de Redes" + parágrafo explicativo (VLSM, WAN /30, EIGRP, OSPF, Packet Tracer). Aviso âmbar "Packet Tracer — equipamento padrão: Cisco 2911 / 2960".
- Card "Cenário de entrada": campos IP da rede base, Prefixo WAN (30), AS EIGRP, Topologia WAN (select). Bloco "Localidades dinâmicas" com linhas (Nome | Qtd. hosts | Remover) e botão "Adicionar localidade". Botões "Calcular cenário" (indigo) e "Limpar tela" (vermelho).
- Card "Importar turma (Excel → ZIP por aluno)": textarea para colar planilha (Nome | Rede base | Hosts1 | Hosts2) + botão âmbar "Exportar ZIP da turma".

**4.5 Telemetria:**
- Título "Telemetria do Framework" + botão "Exportar JSON".
- 4 KPIs (Total de eventos 263, Módulos ativos 6, HTTP OK/erro 170/0, Latência média 19 ms), cada um com ícone em quadrado colorido.
- 3 gráficos (grid `1.1fr | 1fr | 1.2fr`): barras "Eventos por Análise" (por módulo), doughnut "Métodos HTTP" (GET/POST/export/erro com "263" no centro), linha "Requisições/minuto" em tempo real.
- Card "Console ao vivo": log estruturado (mono) com timestamp + nível colorido (INFO/POST/WARN/AUTH) + mensagem; `max-height` com scroll.

**4.6 Documentação:**
- Layout duas colunas: sumário sticky à esquerda (Visão Geral, Funcionalidades, Arquitetura, Execução Docker, Observabilidade); conteúdo à direita em cards com texto e blocos de código em mono (`docker compose up -d`, `curl -H "X-Admin-Api-Key: ..."`).
- (Opcional) tela de **Acesso administrativo**: card centralizado com campo "API key administrativa" (password) + botão "Entrar" (âmbar) + nota sobre o header `X-Admin-Api-Key`.

## 5. Chart.js (tema dark)
- `Chart.defaults.color = '#8b97ad'`, fonte `Inter Tight`; grid `rgba(255,255,255,0.05)`; tooltips fundo `#0c1422`, `usePointStyle:true`; doughnut `borderColor` = fundo do card. Destrua/recrie os gráficos ao entrar/sair da Telemetria.

## 6. Regras finais
- **Nenhum card branco. Nenhum tooltip nativo vazando.** As DataGrids sempre com busca funcional sobre o dataset completo; Portas com paginação.
- Responsivo (top-nav quebra em linhas, grids viram 1 coluna); acessível (contraste, alvos ≥ 40px).
- Entregue código organizado e comentado por tela.

Construa as 6 telas completas seguindo exatamente este documento.
