package org.framework.net.shared;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Fuso em que o site mostra hora a quem usa.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o site é brasileiro e os horários exibidos (painel da Telemetria, console,
 * "atualizado às" do Tráfego ao vivo, "Gerado em" dos pacotes exportados) eram calculados no fuso da JVM:
 * UTC em produção e BRT em desenvolvimento — "22:32:07" em produção era 19:32 em Brasília (auditoria
 * OPS-10). O instante continua sendo guardado em UTC; só a apresentação usa este fuso.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> um fuso IANA fixo ({@code America/Sao_Paulo}), nunca o padrão da
 * máquina; texto que sai para fora do site (arquivo exportado) diz o fuso junto da hora.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não há entrada externa; constantes não lançam.</p>
 */
public final class FusoDoSite {

    /** Fuso de apresentação. */
    public static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    /** Rótulo curto do fuso, para acompanhar hora em texto exportado. */
    public static final String ROTULO = "horário de Brasília";

    private FusoDoSite() {
    }

    /** Formatador no fuso do site. */
    public static DateTimeFormatter formato(String padrao) {
        return DateTimeFormatter.ofPattern(padrao).withZone(ZONA);
    }
}
