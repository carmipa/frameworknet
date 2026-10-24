package org.framework.net.web.support;

import io.quarkus.qute.TemplateExtension;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Mapeia o rótulo de uma coluna de tabela para um ícone Material Symbols.
 *
 * <p><b>Propósito de negócio:</b> dar ícone + significado a todo cabeçalho de coluna, inclusive os que vêm
 * dos dados (colunas dos JSON de aprofundamento e de catálogo), sem cravar o ícone em cada template. É o mapa
 * de ícones de coluna do projeto: mesmo conceito, mesmo glifo — "Rede" é sempre {@code hub}, "Quando" é sempre
 * {@code schedule}, em qualquer tabela. Chamado no Qute como {@code {coluna.iconeColuna}}; os cabeçalhos de
 * texto fixo nos templates usam o mesmo glifo que este mapa devolve (há teste que confere).</p>
 *
 * <p><b>Invariantes do domínio:</b> função pura e total — qualquer entrada (inclusive nula ou desconhecida)
 * devolve um glifo válido. A comparação ignora caixa, espaços nas bordas e espaços repetidos. O nome exato
 * vence a regra por família; entre as regras, vale a primeira que casar. Rótulo conhecido nunca cai no glifo
 * neutro — {@link #conhecido(String)} diz se o rótulo tem glifo próprio, e o teste reprova coluna nova sem ele.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> nunca lança; entrada nula, vazia ou sem glifo próprio devolve
 * {@value #NEUTRO}, para a tela nunca mostrar o nome do glifo como palavra.</p>
 */
@TemplateExtension
public final class IconesColuna {

    /** Glifo de coluna sem significado próprio; só aparece para rótulo que o mapa ainda não conhece. */
    public static final String NEUTRO = "view_column";

    private static final Map<String, String> EXATO = normalizarChaves(Map.ofEntries(
            Map.entry("#", "tag"),
            Map.entry("nº osi", "tag"),
            Map.entry("número", "tag"),
            Map.entry("cód.", "tag"),
            Map.entry("código", "tag"),
            Map.entry("opcode", "tag"),
            Map.entry("tag (hex)", "tag"),
            Map.entry("1º", "looks_one"),
            Map.entry("2º", "looks_two"),
            Map.entry("3º", "looks_3"),
            Map.entry("4º", "looks_4"),
            Map.entry("octeto 1", "looks_one"),
            Map.entry("octeto 2", "looks_two"),
            Map.entry("octeto 3", "looks_3"),
            Map.entry("octeto 4", "looks_4"),
            Map.entry("a", "counter_1"),
            Map.entry("b", "counter_2"),
            Map.entry("osi", "layers"),
            Map.entry("tcp/ip", "layers"),
            Map.entry("dns", "dns"),
            Map.entry("serviço", "door_open"),
            Map.entry("reverso (ip6.arpa)", "dns"),
            Map.entry("pdu", "deployed_code"),
            Map.entry("ips", "memory"),
            Map.entry("capacidade", "memory"),
            Map.entry("nome da pdu", "deployed_code"),
            Map.entry("rede", "lan"),
            Map.entry("rede (lan)", "lan"),
            Map.entry("hub", "hub"),
            Map.entry("switch", "device_hub"),
            Map.entry("roteador", "router"),
            Map.entry("gateway", "router"),
            Map.entry("gateway (svi ::1)", "router"),
            Map.entry("próximo salto", "alt_route"),
            Map.entry("broadcast", "campaign"),
            Map.entry("solicited-node", "groups"),
            Map.entry("hosts úteis", "memory"),
            Map.entry("máscara", "filter_alt"),
            Map.entry("máscara equivalente", "filter_alt"),
            Map.entry("wildcard", "filter_alt"),
            Map.entry("wildcard informada", "filter_alt"),
            Map.entry("cidr", "tag"),
            Map.entry("cidr equivalente", "tag"),
            Map.entry("cidr origem", "tag"),
            Map.entry("prefixo", "tag"),
            Map.entry("prefixo cidr", "tag"),
            Map.entry("barra", "tag"),
            Map.entry("subnet", "tag"),
            Map.entry("sub-redes /64", "grid_on"),
            Map.entry("quantas cabem", "grid_on"),
            Map.entry("1º host", "first_page"),
            Map.entry("1º endereço", "first_page"),
            Map.entry("primeiro", "first_page"),
            Map.entry("último", "last_page"),
            Map.entry("último host", "last_page"),
            Map.entry("último endereço", "last_page"),
            Map.entry("pulo", "redo"),
            Map.entry("variação", "swap_vert"),
            Map.entry("int", "pin"),
            Map.entry("decimal", "pin"),
            Map.entry("hex", "hexagon"),
            Map.entry("hexteto", "hexagon"),
            Map.entry("binário", "data_array"),
            Map.entry("binário (4 bits)", "data_array"),
            Map.entry("ip (bin)", "data_array"),
            Map.entry("comprimido (rfc 5952)", "compress"),
            Map.entry("expandido", "expand"),
            Map.entry("interface", "settings_input_component"),
            Map.entry("interface id", "fingerprint"),
            Map.entry("enlace", "cable"),
            Map.entry("lado a", "settings_input_component"),
            Map.entry("lado b", "settings_input_component"),
            Map.entry("lado típico", "settings_input_component"),
            Map.entry("localidade", "location_on"),
            Map.entry("região / cidade", "location_on"),
            Map.entry("país", "public"),
            Map.entry("isp", "apartment"),
            Map.entry("vlan id", "sell"),
            Map.entry("ip", "dns"),
            Map.entry("endereço", "dns"),
            Map.entry("endereço / prefixo", "dns"),
            Map.entry("endereço ipv6", "dns"),
            Map.entry("endereço usado", "dns"),
            Map.entry("endereçamento", "dns"),
            Map.entry("endereços", "tag"),
            Map.entry("ssid", "wifi"),
            Map.entry("sinal", "network_wifi"),
            Map.entry("proto", "conversion_path"),
            Map.entry("origem", "source"),
            Map.entry("destino", "near_me"),
            Map.entry("de \\ para", "sync_alt"),
            Map.entry("sentido", "east"),
            Map.entry("direção", "east"),
            Map.entry("tam", "memory"),
            Map.entry("info", "info"),
            Map.entry("detalhe", "info"),
            Map.entry("valor", "text_snippet"),
            Map.entry("dado", "text_snippet"),
            Map.entry("exemplo de conteúdo", "text_snippet"),
            Map.entry("ano", "calendar_month"),
            Map.entry("data", "calendar_month"),
            Map.entry("hora", "schedule"),
            Map.entry("horário", "schedule"),
            Map.entry("timestamp", "schedule"),
            Map.entry("duração", "timer"),
            Map.entry("timer", "timer"),
            Map.entry("intervalo", "timer"),
            Map.entry("método", "http"),
            Map.entry("endpoint", "link"),
            Map.entry("chamadas", "tag"),
            Map.entry("p95", "query_stats"),
            Map.entry("4xx", "warning"),
            Map.entry("5xx", "error"),
            Map.entry("erros", "error"),
            Map.entry("mensagem", "chat"),
            Map.entry("módulo", "category"),
            Map.entry("nível", "equalizer"),
            Map.entry("métrica", "straighten"),
            Map.entry("entrada", "input"),
            Map.entry("saída", "output"),
            Map.entry("modo", "tune"),
            Map.entry("tema", "topic"),
            Map.entry("aba", "tab"),
            Map.entry("página", "web"),
            Map.entry("arquivo", "description"),
            Map.entry("artefato", "inventory_2"),
            Map.entry("ação", "touch_app"),
            Map.entry("ação correta", "touch_app"),
            Map.entry("ação (action_type)", "touch_app"),
            Map.entry("etapa", "format_list_numbered"),
            Map.entry("fase", "format_list_numbered"),
            Map.entry("resultado", "output"),
            Map.entry("referência", "menu_book"),
            Map.entry("fronteira", "border_vertical"),
            Map.entry("uso típico", "task_alt"),
            Map.entry("atributo", "tune"),
            Map.entry("diretiva", "settings"),
            Map.entry("escopo", "public"),
            Map.entry("alcance", "public"),
            Map.entry("público", "public"),
            Map.entry("igp/egp", "public"),
            Map.entry("expor na internet?", "public"),
            Map.entry("preferência", "priority_high"),
            Map.entry("conteúdo", "article"),
            Map.entry("conteúdo típico", "article"),
            Map.entry("tem corpo", "article"),
            Map.entry("rfc", "article"),
            Map.entry("suite", "lock"),
            Map.entry("avaliação", "grade"),
            Map.entry("padrão", "label"),
            Map.entry("padrão (dev)", "label"),
            Map.entry("padrão (nome comercial)", "label"),
            Map.entry("emissão", "verified"),
            Map.entry("integridade", "fingerprint"),
            Map.entry("mac / hmac", "fingerprint"),
            Map.entry("estado", "flag"),
            Map.entry("status", "flag"),
            Map.entry("situação", "flag"),
            Map.entry("situação atual", "flag"),
            Map.entry("estado típico (linux)", "flag"),
            Map.entry("tipo/estado", "category"),
            Map.entry("seguro", "lock"),
            Map.entry("segurança", "lock"),
            Map.entry("seguro (usar)", "lock"),
            Map.entry("segurança (bits)", "lock"),
            Map.entry("inseguro (evitar)", "gpp_bad"),
            Map.entry("correção", "build"),
            Map.entry("ferramenta comum", "build"),
            Map.entry("wireshark", "build"),
            Map.entry("tshark", "build"),
            Map.entry("tcpdump", "build"),
            Map.entry("flag", "outlined_flag"),
            Map.entry("cabeçalho", "title"),
            Map.entry("campo", "label"),
            Map.entry("bit / campo", "label"),
            Map.entry("elemento", "label"),
            Map.entry("nome", "label"),
            Map.entry("aspecto", "tune"),
            Map.entry("característica", "tune"),
            Map.entry("propriedade", "tune"),
            Map.entry("recurso", "widgets"),
            Map.entry("extensão", "extension"),
            Map.entry("extensões", "extension"),
            Map.entry("opção", "checklist"),
            Map.entry("escolha", "checklist"),
            Map.entry("critério", "rule"),
            Map.entry("regra", "rule"),
            Map.entry("filtro", "filter_list"),
            Map.entry("operação", "play_circle"),
            Map.entry("cenário", "map"),
            Map.entry("idempotente", "repeat"),
            Map.entry("precisa de root?", "admin_panel_settings"),
            Map.entry("interno/gerência", "admin_panel_settings"),
            Map.entry("como exigir senha", "password"),
            Map.entry("força bruta possível?", "password"),
            Map.entry("hash de senha (argon2, bcrypt, pbkdf2)", "password"),
            Map.entry("hash geral (sha-256, sha-3)", "fingerprint"),
            Map.entry("nonce / aleatoriedade", "casino"),
            Map.entry("precisa de padding", "padding"),
            Map.entry("assinatura digital", "draw"),
            Map.entry("autoassinado?", "draw"),
            Map.entry("expira?", "event_busy"),
            Map.entry("encadeia até raiz confiável?", "account_tree"),
            Map.entry("trust store", "inventory_2"),
            Map.entry("crl", "policy"),
            Map.entry("ocsp (direto)", "policy"),
            Map.entry("ocsp stapling", "policy"),
            Map.entry("no certificado (ca decide)", "verified"),
            Map.entry("no csr (você pede)", "edit_document"),
            Map.entry("no browser moderno", "web"),
            Map.entry("eku (nome / oid)", "task_alt"),
            Map.entry("eku técnico", "task_alt"),
            Map.entry("tipo asn.1", "category"),
            Map.entry("codificação", "code"),
            Map.entry("iniciais", "abc"),
            Map.entry("equivale a simétrico", "compare_arrows"),
            Map.entry("ponto forte", "thumb_up"),
            Map.entry("técnica principal", "construction"),
            Map.entry("tecnologia", "memory"),
            Map.entry("banco", "database"),
            Map.entry("banco de dados", "database"),
            Map.entry("causa provável", "troubleshoot"),
            Map.entry("falha típica", "bug_report"),
            Map.entry("erro (chrome/chromium)", "error"),
            Map.entry("sinal visto", "sensors"),
            Map.entry("interferência", "signal_wifi_bad"),
            Map.entry("fator de amplificação típico", "trending_up"),
            Map.entry("próximo evento típico", "skip_next"),
            Map.entry("papel", "assignment_ind"),
            Map.entry("papel no aperto de mãos", "assignment_ind"),
            Map.entry("versão/porta em claro", "lock_open"),
            Map.entry("versão/porta segura", "lock"),
            Map.entry("http na porta 80", "http"),
            Map.entry("https na porta 443", "lock"),
            Map.entry("texto / starttls", "lock"),
            Map.entry("imap (rfc 3501)", "mail"),
            Map.entry("pop3 (rfc 1939)", "mail"),
            Map.entry("empresarial (802.1x / eap)", "corporate_fare"),
            Map.entry("pessoal (psk / sae)", "wifi_password"),
            Map.entry("troca de chave (pessoal)", "key"),
            Map.entry("exposição recomendada", "visibility"),
            Map.entry("exposto (bind 0.0.0.0, sem firewall)", "priority_high"),
            Map.entry("protegido (bind 127.0.0.1 ou rede privada)", "lock"),
            Map.entry("nunca na internet", "block"),
            Map.entry("dispositivo que a lê", "router"),
            Map.entry("faixa do 1º octeto", "linear_scale"),
            Map.entry("bits de rede", "memory"),
            Map.entry("velocidade máxima teórica", "speed"),
            Map.entry("frequência central", "cell_tower"),
            Map.entry("2.4 ghz", "cell_tower"),
            Map.entry("5 ghz", "cell_tower"),
            Map.entry("6 ghz", "cell_tower"),
            Map.entry("como aparece / comando típico", "terminal"),
            Map.entry("como inspecionar/gerenciar", "manage_search"),
            Map.entry("onde inspecionar", "manage_search"),
            Map.entry("o que examina e decide", "manage_search"),
            Map.entry("o que faz", "task_alt"),
            Map.entry("o que acontece", "bolt"),
            Map.entry("o que muda", "sync_alt"),
            Map.entry("o que mede", "straighten"),
            Map.entry("o que guarda", "inventory_2"),
            Map.entry("o que carrega", "inventory_2"),
            Map.entry("o que casa", "rule"),
            Map.entry("o que confere", "fact_check"),
            Map.entry("o que ainda verificar", "fact_check"),
            Map.entry("o que a ca valida", "fact_check"),
            Map.entry("o que impede", "block"),
            Map.entry("o que responde", "reply"),
            Map.entry("o que o cabeçalho adiciona", "add_box"),
            Map.entry("o que o cliente deve fazer", "touch_app"),
            Map.entry("o que a versão segura protege", "lock"),
            Map.entry("o que costuma indicar", "lightbulb"),
            Map.entry("dhcp", "conversion_path"),
            Map.entry("equipamento", "router"),
            Map.entry("vazão", "speed"),
            Map.entry("sub-redes", "grid_on"),
            Map.entry("ips por sub-rede", "memory"),
            Map.entry("primeiro host", "first_page"),
            Map.entry("hosts", "group"),
            Map.entry("necessário/suportado", "group"),
            Map.entry("bloco", "lan"),
            Map.entry("bloco cidr", "lan"),
            Map.entry("sub-rede", "lan"),
            Map.entry("vlan", "sell"),
            Map.entry("gateway (svi)", "router"),
            Map.entry("o que valida", "fact_check"),
            Map.entry("evidência", "fact_check"),
            Map.entry("o que está errado", "error"),
            Map.entry("erro", "error"),
            Map.entry("conceito ipv4", "block"),
            Map.entry("em ipv6", "rule"),
            Map.entry("sem estado", "rule"),
            Map.entry("equivalente correto", "swap_horiz"),
            Map.entry("alternativa segura", "verified_user"),
            Map.entry("ações", "touch_app"),
            Map.entry("ad", "low_priority"),
            Map.entry("convergência", "update"),
            Map.entry("escalabilidade", "open_in_full"),
            Map.entry("porta comum", "door_open"),
            Map.entry("ponta a", "settings_input_component"),
            Map.entry("ponta b", "settings_input_component"),
            Map.entry("grau", "priority_high"),
            Map.entry("antes → depois", "sync_alt"),
            Map.entry("estado (antes &rarr; depois)", "sync_alt"),
            Map.entry("anunciada", "publish"),
            Map.entry("dce (clock rate)", "schedule"),
            Map.entry("linha", "tag"),
            Map.entry("reqs", "tag"),
            Map.entry("acessos", "tag"),
            Map.entry("local", "location_on"),
            Map.entry("eficiência", "percent"),
            Map.entry("%", "percent"),
            Map.entry("star", "account_tree"),
            Map.entry("estrela est.", "account_tree"),
            Map.entry("mesh", "account_tree"),
            Map.entry("com estado", "account_tree"),
            Map.entry("atual", "today"),
            Map.entry("+25%", "trending_up"),
            Map.entry("+50%", "trending_up"),
            Map.entry("+100%", "trending_up"),
            Map.entry("link", "cable"),
            Map.entry("endereço ip", "dns"),
            Map.entry("pacote", "inventory_2"),
            Map.entry("ok", "check_circle"),
            Map.entry("rota", "route"),
            Map.entry("amostra", "text_snippet"),
            Map.entry("classificação", "grade"),
            Map.entry("indicador / por quê", "lightbulb"),
            Map.entry("motivo", "lightbulb"),
            Map.entry("valor / observação", "info"),
            Map.entry("autenticação", "login"),
            Map.entry("se ficar aberto", "gpp_bad"),
            Map.entry("seção", "segment"),
            Map.entry("peça", "widgets"),
            Map.entry("vai de", "source"),
            Map.entry("para", "near_me"),
            Map.entry("responsabilidade", "task_alt"),
            Map.entry("resources principais", "folder"),
            Map.entry("mapper", "transform"),
            Map.entry("exceção", "report"),
            Map.entry("status típico", "flag"),
            Map.entry("guarda", "lock"),
            Map.entry("tratamento", "build"),
            Map.entry("área", "dashboard"),
            Map.entry("tempo", "timer"),
            Map.entry("tempo (rtt)", "timer"),
            Map.entry("rtt (3 sondas)", "timer"),
            Map.entry("ttl", "hourglass_bottom"),
            Map.entry("ttl enviado", "hourglass_bottom"),
            Map.entry("salto", "alt_route"),
            Map.entry("nó que respondeu", "router"),
            Map.entry("observado", "visibility"),
            Map.entry("o que aconteceu", "bolt"),
            Map.entry("o que trafega", "swap_horiz")));

    private record Regra(Pattern padrao, String glifo) {
    }

    private static Regra regra(String padrao, String glifo) {
        return new Regra(Pattern.compile(padrao), glifo);
    }

    /**
     * Famílias de rótulo, escritas sem acento (o rótulo é comparado já normalizado): a primeira que casar
     * decide, então o mais específico vem antes.
     */
    private static final List<Regra> REGRAS = List.of(
            regra("^(o que nao garante|nao garante|nao cobre)", "cancel"),
            regra("^(garante|cobre)$", "check_circle"),
            regra("^quem\\b", "person"),
            regra("^onde\\b", "location_on"),
            regra("^quando\\b", "schedule"),
            regra("^por qu", "lightbulb"),
            regra("^como funciona", "menu_book"),
            regra("^(uso|para que serve|finalidade|objetivo|aplicacao|protocolo/uso)", "task_alt"),
            regra("^(o que e|o que diz|o que significa|significado|descricao|explicacao|interpretacao|observacao"
                    + "|nota|leitura|frase|risco/observacao|canal / observacao)\\b", "notes"),
            regra("(ataque|^superf)", "gpp_bad"),
            regra("(risco|perig|criticidade)", "priority_high"),
            regra("^(defesa|protecao)", "lock"),
            regra("^(efeito|impacto)", "bolt"),
            regra("^resposta", "reply"),
            regra("^recomenda", "tips_and_updates"),
            regra("^verifica", "fact_check"),
            regra("^(porta|portas|porta\\(s\\)|porta certa)$", "door_open"),
            regra("^(protocolo|protocolos|protocolos tipicos)$", "conversion_path"),
            regra("^(camada|camadas|equivale no osi)", "layers"),
            regra("^dispositivo", "router"),
            regra("chave", "key"),
            regra("(cifra|^aes-)", "lock"),
            regra("^(algoritmo|rsa|ecc|dh |ecdhe|efemero|estatico)", "data_object"),
            regra("^(funcao|camada / funcao)", "data_object"),
            regra("^exemplo", "lightbulb"),
            regra("^comando", "terminal"),
            regra("^ferramenta", "build"),
            regra("^(tipo|categoria|classe|familia)", "category"),
            regra("^(pdu|unidade)", "deployed_code"),
            regra("^(canal|canais|nao sobrepoe)", "view_week"),
            regra("^(banda|frequencia)", "cell_tower"),
            regra("^wi-fi", "wifi"),
            regra("^velocidade", "speed"),
            regra("^(tamanho|bits|bytes|nibbles|hextetos|mtu)", "memory"),
            regra("^largura", "straighten"),
            regra("^faixa", "linear_scale"),
            regra("^(tls|starttls)", "lock"),
            regra("^(ssh|telnet)", "terminal"),
            regra("^(tcp|udp)", "swap_calls"),
            regra("^(ipv4|ipv6|arp|ndp)", "conversion_path"),
            regra("^(pem|der|p7b|pfx|formato)", "description"),
            regra("^transporte", "swap_calls"));

    private IconesColuna() {
    }

    /** Sem acento, sem caixa, espaços colapsados: "Função " e "funcao" são a mesma coluna. */
    private static String normalizar(String coluna) {
        String semAcento = Normalizer.normalize(coluna.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> normalizarChaves(Map<String, String> bruto) {
        Map<String, String> m = new HashMap<>();
        bruto.forEach((k, v) -> {
            if (m.put(normalizar(k), v) != null) {
                throw new IllegalStateException("rótulo repetido no mapa de ícones: " + k);
            }
        });
        return Map.copyOf(m);
    }

    private static String glifoProprio(String coluna) {
        if (coluna == null || coluna.isBlank()) {
            return null;
        }
        String k = normalizar(coluna);
        String exato = EXATO.get(k);
        if (exato != null) {
            return exato;
        }
        for (Regra r : REGRAS) {
            if (r.padrao().matcher(k).find()) {
                return r.glifo();
            }
        }
        return null;
    }

    public static String iconeColuna(String coluna) {
        String g = glifoProprio(coluna);
        return g == null ? NEUTRO : g;
    }

    /** {@code true} quando o rótulo tem glifo próprio no mapa (não cai no {@value #NEUTRO}). */
    public static boolean conhecido(String coluna) {
        return glifoProprio(coluna) != null;
    }
}
