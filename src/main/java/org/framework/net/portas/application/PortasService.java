package org.framework.net.portas.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.portas.domain.PortaItem;
import org.framework.net.portas.domain.PortasCatalog;
import org.framework.net.protocolos.domain.AprofundamentoProtocolo;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class PortasService {

    private static final String FALLBACK_ALTERNATIVA =
            "Aplicar segmentação, firewall e criptografia ponta a ponta";

    private static final Map<String, String> ALTERNATIVAS_POR_SERVICO = Map.ofEntries(
            Map.entry("ftp", "SFTP ou FTPS"),
            Map.entry("telnet", "SSH"),
            Map.entry("http", "HTTPS (TLS)"),
            Map.entry("http alternativo", "HTTPS (443/8443)"),
            Map.entry("pop3", "POP3S (995) ou IMAPS (993)"),
            Map.entry("imap", "IMAPS (993)"),
            Map.entry("ldap", "LDAPS (636)"),
            Map.entry("netbios", "SMBv3 restrito + VPN"),
            Map.entry("smb", "SMB assinado via VPN"),
            Map.entry("rdp", "Acesso via VPN + MFA"),
            Map.entry("vnc", "VNC via túnel SSH"),
            Map.entry("sql server", "Acesso privado via VPN/bastion"),
            Map.entry("oracle", "Acesso privado + criptografia"),
            Map.entry("mysql", "Acesso privado via VPN/bastion"),
            Map.entry("postgresql", "Acesso privado + SSL"),
            Map.entry("redis", "Redis local com auth e TLS"),
            Map.entry("elasticsearch", "Acesso privado com auth e TLS"),
            Map.entry("mongodb", "Acesso privado com auth e TLS"),
            Map.entry("dns", "DNS restrito + DNSSEC (quando aplicável)"),
            Map.entry("dhcp", "DHCP Snooping + segmentação VLAN"),
            Map.entry("ntp", "NTP autenticado e restrito"),
            Map.entry("https", "Manter TLS 1.2/1.3 e certificados válidos"),
            Map.entry("https alternativo", "Manter TLS 1.2/1.3 e certificados válidos"),
            Map.entry("smtps", "Submissão com TLS implícito (RFC 8314), TLS 1.2/1.3 e autenticação obrigatória"),
            Map.entry("submission", "Submission 587 com STARTTLS obrigatório"),
            Map.entry("imaps", "IMAPS com TLS forte"),
            Map.entry("pop3s", "POP3S com TLS forte"),
            Map.entry("ssh", "SSH com chave pública e MFA"),
            Map.entry("tftp", "SFTP/HTTPS para transferência segura"));

    @Inject
    PortasCatalog portasCatalog;

    @Inject
    TelemetriaLogger telemetriaLogger;

    /**
     * Serviço da porta → slug do aprofundamento de protocolo correspondente.
     *
     * <p><b>Propósito de negócio:</b> conecta os dois módulos — 16 das 30 portas
     * correspondem a um protocolo que já tem página dedicada, então a linha ganha
     * o botão "Aprofundar" apontando para {@code /protocolos/<slug>}. A rota final
     * NÃO é escrita à mão: vem de {@link AprofundamentoProtocolo#porSlug(String)},
     * a fonte única, de modo que uma renomeação de rota lá reflete aqui.</p>
     */
    private static final Map<String, String> SERVICO_PARA_PROTOCOLO = Map.ofEntries(
            Map.entry("ftp", "ftp"),
            Map.entry("tftp", "ftp"),
            Map.entry("ssh", "ssh"),
            Map.entry("telnet", "telnet"),
            Map.entry("smtp", "smtp"),
            Map.entry("pop3", "smtp"),
            Map.entry("imap", "smtp"),
            Map.entry("smtps", "smtp"),
            Map.entry("submission", "smtp"),
            Map.entry("imaps", "smtp"),
            Map.entry("pop3s", "smtp"),
            Map.entry("dns", "dns"),
            Map.entry("http", "http"),
            Map.entry("http alternativo", "http"),
            Map.entry("https", "tls"),
            Map.entry("https alternativo", "tls"));

    public List<PortaItemExibicao> montarPortasCatalogoExibicao() {
        telemetriaLogger.logEvent("info", "portas", "catalog_load",
                Map.of("total", portasCatalog.getCatalogo().size()));
        return portasCatalog.getCatalogo().stream()
                .map(item -> PortaItemExibicao.from(item, alternativaSeguraPorta(item), aprofundamentoRota(item)))
                .collect(Collectors.toList());
    }

    /**
     * Resolve a rota do aprofundamento de protocolo para a linha da porta.
     *
     * <p><b>Comportamento em caso de falha:</b> serviço sem protocolo mapeado, ou
     * slug que não existe mais no registro, devolve {@code null} — a linha
     * simplesmente não ganha o botão "Aprofundar", nunca um link quebrado.</p>
     */
    private String aprofundamentoRota(PortaItem item) {
        String servico = normalizar(item.servico()).toLowerCase(Locale.ROOT);
        String slug = SERVICO_PARA_PROTOCOLO.get(servico);
        if (slug == null) {
            return null;
        }
        return AprofundamentoProtocolo.porSlug(slug).map(AprofundamentoProtocolo::rota).orElse(null);
    }

    private String alternativaSeguraPorta(PortaItem item) {
        String servico = normalizar(item.servico()).toLowerCase(Locale.ROOT);
        String mapeada = ALTERNATIVAS_POR_SERVICO.get(servico);
        if (mapeada != null) {
            return mapeada;
        }
        String recomendacao = normalizar(item.recomendacao());
        if (!recomendacao.isEmpty()) {
            return recomendacao;
        }
        return FALLBACK_ALTERNATIVA;
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
