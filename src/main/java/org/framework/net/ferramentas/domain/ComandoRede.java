package org.framework.net.ferramentas.domain;

import java.util.List;

/**
 * Catálogo comparado de comandos de REDE entre Windows e Linux, organizado por
 * INTENÇÃO (o que a pessoa quer descobrir), não por nome de comando.
 *
 * <p><b>Propósito de negócio:</b> ser a fonte única de verdade da aba
 * "Rede: Windows × Linux" do módulo Ferramentas — cada tarefa traz o comando de
 * cada sistema, os parâmetros explicados, uma saída de EXEMPLO (simulada), a
 * leitura campo a campo, o limite da conclusão, o próximo passo, a necessidade de
 * privilégio e a disponibilidade da ferramenta.</p>
 *
 * <p><b>Invariantes do domínio:</b> nenhuma saída aqui foi executada em máquina
 * real — são exemplos didáticos rotulados como simulados; nunca se afirma
 * equivalência exata entre comandos que apenas se aproximam (o campo
 * {@code diferenca} registra a ressalva quando existe); a lista é imutável.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> classe só de dados; não executa nada
 * nem contata a rede. A camada de apresentação apenas renderiza este catálogo.</p>
 */
public record ComandoRede(
        String id,
        String categoria,
        String intencao,
        String pergunta,
        Plataforma windows,
        Plataforma linux,
        String limite,
        String proximoPasso,
        String diferenca,
        List<String> termos,
        String fonte) {

    /** Os dois lados da comparação, na ordem Windows depois Linux. */
    public List<Plataforma> plataformas() {
        return List.of(windows, linux);
    }

    /** Um lado da comparação (Windows ou Linux). {@code saida} é SIMULADA. */
    public record Plataforma(String rotulo, String shell, String comando, boolean admin,
                             String disponibilidade, List<Param> params, String saida, List<CampoSaida> campos) {

        /** Classe estável para CSS/JS (win|linux), derivada do rótulo. */
        public String classe() {
            return "Windows".equals(rotulo) ? "win" : "linux";
        }

        /** Ícone do design system para o lado (não são marcas, são símbolos genéricos). */
        public String icone() {
            return "Windows".equals(rotulo) ? "desktop_windows" : "terminal";
        }
    }

    /** Um parâmetro do comando e o que ele faz. */
    public record Param(String flag, String explica) {
    }

    /** Um campo da saída simulada, para dissecar clicando. */
    public record CampoSaida(String rotulo, String valor, String significado) {
    }

    /** Categorias para filtro/cor (chave estável usada no CSS/JS). */
    public static List<Categoria> categorias() {
        return List.of(
                new Categoria("interfaces", "Interfaces e endereços", "lan"),
                new Categoria("rotas", "Gateway e rotas", "route"),
                new Categoria("vizinhos", "ARP / vizinhos", "hub"),
                new Categoria("dns", "Nomes e DNS", "dns"),
                new Categoria("conectividade", "Conectividade e caminho", "travel_explore"),
                new Categoria("sockets", "Conexões e portas", "settings_ethernet"),
                new Categoria("porta", "Testar uma porta", "cable"),
                new Categoria("estatisticas", "Estatísticas de interface", "monitoring"),
                new Categoria("processos", "Processos × conexões", "memory"));
    }

    public record Categoria(String id, String rotulo, String icone) {
    }

    public static List<ComandoRede> catalogo() {
        return CATALOGO;
    }

    private static final List<ComandoRede> CATALOGO = List.of(
            new ComandoRede("ip-interfaces", "interfaces",
                    "Ver minhas interfaces e endereços IP",
                    "Qual é o meu IP? Que placas de rede eu tenho?",
                    new Plataforma("Windows", "CMD", "ipconfig /all", false, "Nativo (todas as versões)",
                            List.of(new Param("/all", "Mostra a configuração completa: MAC, DHCP, servidores DNS e sufixos — sem ele, vêm só IP, máscara e gateway.")),
                            "Adaptador Ethernet Ethernet:\n   Sufixo DNS específico. . . :\n   Endereço físico . . . . . . : 02-00-00-00-00-0A\n   DHCP Habilitado. . . . . . .: Sim\n   Endereço IPv4. . . . . . . . : 192.168.1.10(Preferencial)\n   Máscara de Sub-rede . . . . : 255.255.255.0\n   Gateway Padrão. . . . . . . : 192.168.1.1",
                            List.of(new CampoSaida("Endereço IPv4", "192.168.1.10", "O endereço desta máquina na rede local."),
                                    new CampoSaida("Endereço físico", "02-00-00-00-00-0A", "O MAC da placa — identifica a interface no enlace (L2)."),
                                    new CampoSaida("Gateway Padrão", "192.168.1.1", "Para onde vai o tráfego destinado a outras redes."))),
                    new Plataforma("Linux", "shell", "ip address show", false, "iproute2 (padrão nas distros atuais)",
                            List.of(new Param("address (addr, a)", "Objeto de camada 3 (endereços). Aceita abreviações: 'ip a' faz o mesmo."),
                                    new Param("show", "Ação de listar (implícita se omitida).")),
                            "2: eth0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 ...\n    link/ether 02:00:00:00:00:0a brd ff:ff:ff:ff:ff:ff\n    inet 192.168.1.10/24 brd 192.168.1.255 scope global eth0\n    inet6 fe80::10/64 scope link",
                            List.of(new CampoSaida("inet 192.168.1.10/24", "192.168.1.10/24", "Endereço IPv4 com prefixo /24 (a máscara embutida)."),
                                    new CampoSaida("link/ether", "02:00:00:00:00:0a", "O MAC da interface (L2)."),
                                    new CampoSaida("UP,LOWER_UP", "flags", "A interface está administrativamente e fisicamente ativa."))),
                    "Ver o IP não prova que há Internet — só mostra a configuração local. O gateway pode estar errado ou fora do ar.",
                    "Confira o gateway e teste a conectividade (ping no gateway e num destino externo).",
                    "O antigo 'ifconfig' (net-tools) some em muitas distros; 'ip' é o atual. No Windows, 'ipconfig' não tem equivalente em nome, mas 'Get-NetIPConfiguration' no PowerShell cobre o mesmo.",
                    List.of("ver meu ip", "meu endereco", "ipconfig", "ip a", "ifconfig", "placa de rede", "mac"),
                    "Microsoft Learn (ipconfig); man ip(8) — revisado 09/2026"),

            new ComandoRede("ip-rotas", "rotas",
                    "Ver o gateway e a tabela de rotas",
                    "Por onde meu tráfego sai para outras redes?",
                    new Plataforma("Windows", "CMD", "route print", false, "Nativo",
                            List.of(new Param("print", "Lista a tabela de rotas. 'route print -4' filtra só IPv4.")),
                            "Rotas Ativas:\nDestino de Rede    Máscara de Rede   Gateway        Interface  Métrica\n0.0.0.0            0.0.0.0           192.168.1.1    192.168.1.10   25\n192.168.1.0        255.255.255.0     Em vínculo     192.168.1.10   281",
                            List.of(new CampoSaida("0.0.0.0 / 0.0.0.0", "rota padrão", "O 'catch-all': tudo que não casa outra rota vai pelo gateway."),
                                    new CampoSaida("Gateway 192.168.1.1", "192.168.1.1", "O próximo salto da rota padrão."),
                                    new CampoSaida("Em vínculo (on-link)", "rede local", "A rede 192.168.1.0/24 é acessível direto, sem gateway."))),
                    new Plataforma("Linux", "shell", "ip route show", false, "iproute2",
                            List.of(new Param("route (r)", "Objeto de rotas."),
                                    new Param("show", "Lista (implícito). 'ip route get 8.8.8.8' mostra a rota escolhida para um destino.")),
                            "default via 192.168.1.1 dev eth0 proto dhcp metric 100\n192.168.1.0/24 dev eth0 proto kernel scope link src 192.168.1.10",
                            List.of(new CampoSaida("default via 192.168.1.1", "gateway", "A rota padrão e seu próximo salto."),
                                    new CampoSaida("dev eth0", "eth0", "A interface de saída."),
                                    new CampoSaida("scope link", "rede local", "A sub-rede alcançável diretamente."))),
                    "A rota existir não garante que o gateway responde; é a configuração, não o estado do enlace.",
                    "Faça ping no gateway; se responder, teste um destino externo por IP e depois por nome.",
                    "'route -n' (net-tools) ainda aparece no Linux, mas 'ip route' é o atual. No Windows, 'Get-NetRoute' é o equivalente em PowerShell.",
                    List.of("gateway", "rota padrao", "route print", "ip route", "tabela de rotas", "default gateway"),
                    "Microsoft Learn (route); man ip-route(8) — revisado 09/2026"),

            new ComandoRede("arp-vizinhos", "vizinhos",
                    "Ver vizinhos na rede local (ARP/NDP)",
                    "Quais MACs meu host já associou a IPs da LAN?",
                    new Plataforma("Windows", "CMD", "arp -a", false, "Nativo",
                            List.of(new Param("-a", "Mostra a tabela ARP (IP → MAC) de todas as interfaces.")),
                            "Interface: 192.168.1.10 --- 0x5\n  Endereço IP        Endereço físico       Tipo\n  192.168.1.1        02-00-00-00-00-01     dinâmico\n  192.168.1.20       02-00-00-00-00-14     dinâmico",
                            List.of(new CampoSaida("192.168.1.1 → …00-01", "gateway", "O MAC aprendido para o gateway."),
                                    new CampoSaida("dinâmico", "tipo", "Entrada aprendida por ARP (expira); 'estático' seria fixada manualmente."))),
                    new Plataforma("Linux", "shell", "ip neighbor show", false, "iproute2",
                            List.of(new Param("neighbor (neigh, n)", "Tabela de vizinhos: ARP no IPv4, NDP no IPv6.")),
                            "192.168.1.1 dev eth0 lladdr 02:00:00:00:00:01 REACHABLE\n192.168.1.20 dev eth0 lladdr 02:00:00:00:00:14 STALE",
                            List.of(new CampoSaida("lladdr", "02:00:00:00:00:01", "O endereço de enlace (MAC) do vizinho."),
                                    new CampoSaida("REACHABLE / STALE", "estado", "REACHABLE = confirmado há pouco; STALE = pode precisar revalidar."))),
                    "A tabela reflete só quem seu host contatou recentemente — não é um inventário da rede inteira.",
                    "Para descobrir um vizinho ausente, gere tráfego (ping) e consulte de novo.",
                    "No IPv6 não há ARP: o Linux mostra NDP na mesma tabela; o Windows usa 'netsh interface ipv6 show neighbors'.",
                    List.of("arp", "vizinhos", "mac de um ip", "tabela arp", "ndp", "ip neigh"),
                    "Microsoft Learn (arp); man ip-neighbour(8) — revisado 09/2026"),

            new ComandoRede("dns-resolucao", "dns",
                    "Resolver um nome e consultar o DNS",
                    "Esse nome resolve? Para qual IP?",
                    new Plataforma("Windows", "PowerShell", "Resolve-DnsName example.com", false, "PowerShell (Windows 8/2012+)",
                            List.of(new Param("example.com", "O nome a resolver."),
                                    new Param("-Type MX (opcional)", "Consulta um tipo específico (A, AAAA, MX, TXT…). 'nslookup example.com' funciona em qualquer Windows.")),
                            "Name                Type   TTL   Section    IPAddress\n----                ----   ---   -------    ---------\nexample.com         A      3600  Answer     93.184.216.34",
                            List.of(new CampoSaida("Type A", "A", "Registro de endereço IPv4 (AAAA seria IPv6)."),
                                    new CampoSaida("IPAddress", "93.184.216.34", "O IP que o nome resolveu."),
                                    new CampoSaida("TTL 3600", "3600 s", "Quanto tempo a resposta pode ficar em cache."))),
                    new Plataforma("Linux", "shell", "dig example.com", false, "bind-utils / dnsutils (instalar; senão use 'host' ou 'getent hosts')",
                            List.of(new Param("example.com", "O nome a resolver."),
                                    new Param("+short (opcional)", "Só a resposta, sem o cabeçalho. 'dig MX example.com' pede um tipo.")),
                            ";; ANSWER SECTION:\nexample.com.  3600  IN  A  93.184.216.34\n\n;; SERVER: 192.168.1.1#53(192.168.1.1)",
                            List.of(new CampoSaida("IN A 93.184.216.34", "93.184.216.34", "O IP resolvido para o nome."),
                                    new CampoSaida("SERVER …#53", "192.168.1.1", "Qual resolvedor respondeu (porta 53)."))),
                    "Resolver o nome não prova que o serviço no IP está no ar — só que o DNS respondeu. Pode ser cache antigo.",
                    "Com o IP em mãos, teste a conectividade e a porta do serviço (ex.: 443).",
                    "'nslookup' existe nos dois mundos, mas seu formato varia; no Linux moderno prefira 'dig'/'host'. 'getent hosts' respeita a ordem do sistema (arquivo hosts + DNS).",
                    List.of("dns", "resolver nome", "nslookup", "dig", "nao resolve", "qual o ip do site", "resolve-dnsname"),
                    "Microsoft Learn (Resolve-DnsName); man dig(1) — revisado 09/2026"),

            new ComandoRede("conectividade", "conectividade",
                    "Testar conectividade e investigar o caminho",
                    "Chego até o destino? Onde trava o caminho?",
                    new Plataforma("Windows", "CMD", "ping example.com", false, "Nativo",
                            List.of(new Param("example.com", "Destino (nome ou IP)."),
                                    new Param("-t", "Ping contínuo até Ctrl+C (no Windows o padrão são 4 pacotes). 'tracert' mostra o caminho salto a salto.")),
                            "Disparando example.com [93.184.216.34] com 32 bytes de dados:\nResposta de 93.184.216.34: bytes=32 tempo=12ms TTL=56\nEstatísticas do Ping para 93.184.216.34:\n    Pacotes: Enviados = 4, Recebidos = 4, Perdidos = 0 (0% de perda)",
                            List.of(new CampoSaida("tempo=12ms", "12 ms", "Latência de ida e volta (RTT)."),
                                    new CampoSaida("Perdidos = 0", "0%", "Nenhum pacote perdido nesta amostra."),
                                    new CampoSaida("TTL=56", "56", "TTL restante — pistas sobre quantos saltos o pacote atravessou."))),
                    new Plataforma("Linux", "shell", "ping -c 4 example.com", false, "Nativo (iputils); caminho: 'traceroute'/'mtr' (instalar)",
                            List.of(new Param("-c 4", "Envia 4 pacotes e para — no Linux, sem -c o ping é contínuo."),
                                    new Param("example.com", "Destino.")),
                            "PING example.com (93.184.216.34) 56(84) bytes of data.\n64 bytes from 93.184.216.34: icmp_seq=1 ttl=56 time=12.3 ms\n--- example.com ping statistics ---\n4 packets transmitted, 4 received, 0% packet loss",
                            List.of(new CampoSaida("time=12.3 ms", "12,3 ms", "RTT do pacote."),
                                    new CampoSaida("0% packet loss", "0%", "Sem perda nesta amostra."))),
                    "Ping usa ICMP: muitos hosts e firewalls bloqueiam ICMP por política — não responder ao ping não prova que o serviço está fora.",
                    "Se o ping falha mas o nome resolve, teste a porta do serviço direto (TCP) e o caminho com traceroute.",
                    "O padrão de contagem difere: Windows manda 4 e para; Linux fica contínuo sem '-c'. 'Test-Connection' é a versão PowerShell.",
                    List.of("ping", "sem internet", "conectividade", "tracert", "traceroute", "caminho", "latencia", "perda de pacote"),
                    "Microsoft Learn (ping/tracert); man ping(8) — revisado 09/2026"),

            new ComandoRede("sockets", "sockets",
                    "Ver conexões, sockets e portas locais",
                    "O que está aberto/escutando na minha máquina?",
                    new Plataforma("Windows", "CMD", "netstat -ano", false, "Nativo (-b, com nome do processo, exige admin)",
                            List.of(new Param("-a", "Todas as conexões e portas em escuta."),
                                    new Param("-n", "Números (não resolve nomes) — mais rápido e claro."),
                                    new Param("-o", "Mostra o PID dono de cada conexão.")),
                            "Proto  Endereço local      Endereço remoto     Estado        PID\nTCP    0.0.0.0:445         0.0.0.0:0           LISTENING     4\nTCP    192.168.1.10:51000  93.184.216.34:443   ESTABLISHED   6120",
                            List.of(new CampoSaida("0.0.0.0:445 LISTENING", "porta 445", "Um serviço escutando em todas as interfaces."),
                                    new CampoSaida("ESTABLISHED", "estado", "Conexão TCP ativa com o remoto."),
                                    new CampoSaida("PID 6120", "6120", "O processo dono — cruze com a lista de processos."))),
                    new Plataforma("Linux", "shell", "ss -tulpn", false, "iproute2; ver o PID de todos exige root",
                            List.of(new Param("-t / -u", "TCP / UDP."),
                                    new Param("-l", "Só sockets em escuta (listening)."),
                                    new Param("-p", "Mostra o processo (precisa de root para os alheios)."),
                                    new Param("-n", "Não resolve nomes/portas.")),
                            "Netid State  Local Address:Port  Peer Address:Port  Process\ntcp   LISTEN 0.0.0.0:22          0.0.0.0:*          users:((\"sshd\",pid=812,fd=3))\nudp   UNCONN 0.0.0.0:68          0.0.0.0:*",
                            List.of(new CampoSaida("LISTEN 0.0.0.0:22", "porta 22", "sshd escutando em todas as interfaces."),
                                    new CampoSaida("users:((\"sshd\",pid=812))", "sshd/812", "O processo e PID dono do socket."))),
                    "Escutar em 0.0.0.0 não prova exposição à Internet: um firewall ou NAT pode bloquear o acesso externo.",
                    "Anote o PID e identifique o processo; depois verifique se a porta está mesmo acessível de fora.",
                    "'netstat' no Linux (net-tools) foi superado por 'ss' (iproute2), mais rápido. No Windows, 'Get-NetTCPConnection' é a via PowerShell.",
                    List.of("netstat", "ss", "portas abertas", "quem usa esta porta", "escutando", "listening", "conexoes", "sockets"),
                    "Microsoft Learn (netstat); man ss(8) — revisado 09/2026"),

            new ComandoRede("porta-tcp", "porta",
                    "Testar uma porta TCP específica",
                    "Consigo chegar na porta X daquele host?",
                    new Plataforma("Windows", "PowerShell", "Test-NetConnection example.com -Port 443", false, "PowerShell (Windows 8/2012+)",
                            List.of(new Param("-Port 443", "A porta TCP a testar."),
                                    new Param("example.com", "O host de destino.")),
                            "ComputerName     : example.com\nRemoteAddress    : 93.184.216.34\nRemotePort       : 443\nTcpTestSucceeded : True",
                            List.of(new CampoSaida("TcpTestSucceeded : True", "True", "O handshake TCP na porta 443 completou — a porta está acessível daqui."),
                                    new CampoSaida("RemoteAddress", "93.184.216.34", "O IP que o nome resolveu e foi testado."))),
                    new Plataforma("Linux", "shell", "nc -vz example.com 443", false, "netcat (instalar); alternativa nativa em bash: 'echo > /dev/tcp/host/porta'",
                            List.of(new Param("-v", "Verboso (mostra sucesso/falha)."),
                                    new Param("-z", "Só testa (zero-I/O), não envia dados."),
                                    new Param("example.com 443", "Host e porta.")),
                            "Connection to example.com (93.184.216.34) 443 port [tcp/https] succeeded!",
                            List.of(new CampoSaida("succeeded!", "sucesso", "O TCP conectou na 443 — a porta responde a partir daqui."))),
                    "Sucesso prova que a porta responde DESTE ponto agora; não prova que o serviço está saudável nem acessível de outros lugares. Falha pode ser firewall, rota ou serviço parado.",
                    "Se conectou, fale o protocolo do serviço (ex.: inspecione o TLS na 443). Se falhou, isole: o nome resolve? o host pinga?",
                    "Windows não tem 'nc' nativo; usa 'Test-NetConnection'. No Linux, 'nc' costuma precisar de instalação; o truque '/dev/tcp' do bash não depende de pacote.",
                    List.of("testar porta", "porta aberta", "telnet porta", "nc", "test-netconnection", "conectar na porta"),
                    "Microsoft Learn (Test-NetConnection); man nc(1) — revisado 09/2026"),

            new ComandoRede("estatisticas", "estatisticas",
                    "Ver estatísticas de uma interface",
                    "Quantos pacotes/bytes e erros passaram pela placa?",
                    new Plataforma("Windows", "PowerShell", "Get-NetAdapterStatistics", false, "PowerShell (Windows 8/2012+); 'netstat -e' resumido em qualquer versão",
                            List.of(new Param("(sem argumento)", "Lista bytes/pacotes enviados e recebidos por adaptador.")),
                            "Name       ReceivedBytes  ReceivedUnicastPackets  SentBytes   SentUnicastPackets\n----       -------------  ----------------------  ---------   ------------------\nEthernet   1048576000     742315                  256000000   410992",
                            List.of(new CampoSaida("ReceivedBytes", "1048576000", "Total de bytes recebidos desde o boot/reset do contador."),
                                    new CampoSaida("SentUnicastPackets", "410992", "Pacotes unicast enviados."))),
                    new Plataforma("Linux", "shell", "ip -s link show eth0", false, "iproute2; detalhes por driver: 'ethtool -S eth0'",
                            List.of(new Param("-s", "Mostra estatísticas (RX/TX)."),
                                    new Param("link show eth0", "A interface alvo.")),
                            "2: eth0: <...> mtu 1500 ...\n    RX: bytes  packets  errors  dropped\n    1048576000 742315   0       0\n    TX: bytes  packets  errors  dropped\n    256000000  410992   0       0",
                            List.of(new CampoSaida("RX errors 0", "0", "Erros de recepção — crescimento aqui aponta problema físico/driver."),
                                    new CampoSaida("RX/TX bytes", "acumulado", "Contadores acumulados desde o boot."))),
                    "São contadores acumulados: um número alto de erros só importa em relação ao total e à variação no tempo.",
                    "Se há erros crescendo, investigue cabo/porta/driver; compare duas leituras com intervalo.",
                    "Não há equivalente idêntico: o Windows separa por contadores de adaptador; o Linux junta RX/TX na interface. 'ethtool -S' expõe contadores específicos do driver.",
                    List.of("estatisticas", "pacotes", "bytes", "erros de rede", "netstat -e", "ip -s", "ethtool"),
                    "Microsoft Learn (Get-NetAdapterStatistics); man ip-link(8) — revisado 09/2026"),

            new ComandoRede("processos-conexoes", "processos",
                    "Relacionar processos e conexões",
                    "Que programa está por trás desta conexão/porta?",
                    new Plataforma("Windows", "CMD", "netstat -anob", true, "Nativo; '-b' (nome do executável) EXIGE admin",
                            List.of(new Param("-b", "Mostra o executável dono de cada conexão (requer prompt como administrador)."),
                                    new Param("-a -n -o", "Todas as conexões, em números, com PID.")),
                            "Proto  Endereço local     Estado       PID\nTCP    0.0.0.0:445        LISTENING    4\n [System]\nTCP    192.168.1.10:51000 ESTABLISHED  6120\n [navegador.exe]",
                            List.of(new CampoSaida("[navegador.exe]", "processo", "O executável dono da conexão — mas o NOME pode ser forjado; não prova legitimidade."),
                                    new CampoSaida("PID 6120", "6120", "Cruze com 'tasklist /svc' ou Get-Process para detalhes."))),
                    new Plataforma("Linux", "shell", "sudo ss -tunap", true, "iproute2; ver o dono de sockets alheios exige root. Alternativa: 'sudo lsof -i'",
                            List.of(new Param("sudo", "Necessário para ver o processo de sockets de outros usuários."),
                                    new Param("-tunap", "TCP+UDP, em números, TODOS os estados (escuta e conexões), com processo. Com -l sairia só a escuta.")),
                            "tcp LISTEN 0.0.0.0:445 users:((\"smbd\",pid=1002,fd=34))\ntcp ESTAB 192.168.1.10:51000 93.184.216.34:443 users:((\"firefox\",pid=6120))",
                            List.of(new CampoSaida("users:((\"firefox\",pid=6120))", "firefox/6120", "Processo e PID donos do socket."),
                                    new CampoSaida("smbd", "serviço", "O nome do processo — confirme o binário e a origem antes de confiar."))),
                    "Casar processo↔conexão diz QUEM abriu o socket, não se é legítimo: o nome do processo pode ser forjado. Confirme o caminho e a origem do binário.",
                    "Com o PID, verifique o caminho do executável, sua assinatura/hash e desde quando roda.",
                    "Os dois pedem privilégio para ver donos alheios: '-b' no Windows exige admin; '-p' no ss exige root para sockets de outros usuários.",
                    List.of("processo da porta", "quem abriu a conexao", "pid da porta", "netstat -b", "ss -p", "lsof -i", "qual programa esta ouvindo"),
                    "Microsoft Learn (netstat -b); man ss(8), lsof(8) — revisado 09/2026"));
}
