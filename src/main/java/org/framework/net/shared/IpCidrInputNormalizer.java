package org.framework.net.shared;

/**
 * Normaliza entradas IPv4 com notação CIDR opcional no mesmo campo (ex.: 172.19.0.0/16).
 * Espelha {@code _build_form_data} do projeto Python de referência.
 */
public final class IpCidrInputNormalizer {

    private IpCidrInputNormalizer() {
    }

    /**
     * Endereço e prefixo separados.
     *
     * @param aviso texto para a tela quando o "/NN" digitado junto do endereço divergiu do campo de prefixo
     *              (e prevaleceu); {@code null} quando não houve conflito
     */
    public record SplitResult(String ip, String cidrRaw, String aviso) {
    }

    /**
     * Separa "IP/NN" do campo de endereço.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o campo de prefixo volta preenchido com o valor da consulta anterior;
     * quem digitava "10.0.0.0/8" na consulta seguinte recebia o resultado em /24, sem aviso (auditoria
     * CALC-35). O "/NN" digitado junto do endereço é a intenção mais recente e explícita.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> "/NN" no campo de endereço prevalece sobre o campo de prefixo; quando
     * os dois divergem, o resultado carrega um aviso para a tela dizer qual valeu; sem "/" no endereço, o
     * campo de prefixo vale como antes.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não valida: devolve os textos separados (e aparados); a
     * validação do prefixo é de quem consome.</p>
     */
    public static SplitResult splitIpAndCidr(String ipRaw, String cidrRaw) {
        String ip = ipRaw == null ? "" : ipRaw.strip();
        String cidr = cidrRaw == null ? "" : cidrRaw.strip();
        String aviso = null;
        if (ip.contains("/")) {
            String[] parts = ip.split("/", 2);
            ip = parts[0].strip();
            String doEndereco = parts[1].strip();
            if (!cidr.isEmpty() && !doEndereco.isEmpty() && !cidr.equals(doEndereco)) {
                aviso = "O /" + doEndereco + " digitado junto do endereço prevaleceu sobre o /" + cidr
                        + " do campo de prefixo.";
            }
            if (!doEndereco.isEmpty()) {
                cidr = doEndereco;
            }
        }
        return new SplitResult(ip, cidr, aviso);
    }

    /** Evita resolver DNS quando o usuário digitou IPv4 ou IPv4/CIDR. */
    public static boolean looksLikeIpv4OrCidr(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (char c : value.strip().toCharArray()) {
            if (!(Character.isDigit(c) || c == '.' || c == '/')) {
                return false;
            }
        }
        return true;
    }
}
