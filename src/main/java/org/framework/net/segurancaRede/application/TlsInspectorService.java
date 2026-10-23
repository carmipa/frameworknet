package org.framework.net.segurancaRede.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.segurancaRede.domain.ResultadoInspecaoTls;
import org.framework.net.segurancaRede.domain.ResultadoInspecaoTls.Checagem;
import org.framework.net.segurancaRede.exception.SegurancaException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Inspetor didático de certificado/conexão TLS.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> avaliar os atributos de uma conexão TLS
 * informados pelo aluno (nome acessado, nome no certificado, validade, emissor,
 * versão e cipher) exatamente como um validador faria — nome, validade, cadeia,
 * protocolo e cipher — e emitir um veredito por item e um geral. É simulação: não
 * abre conexão de rede (o app roda em VPS e uma conexão de saída seria efeito
 * externo com risco de SSRF), então ilustra a lógica de validação sem executá-la
 * contra um host real.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> o veredito geral é o PIOR estado entre as
 * checagens — uma única falha recusa a conexão, como no navegador. Entrada é
 * sanitizada contra caracteres de HTML/script antes de ser ecoada no resultado.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> campo obrigatório ausente, muito
 * longo, com caractere perigoso, ou "dias para expirar" não numérico dispara
 * {@link SegurancaException} (HTTP 400 pelo mapper) — nunca um veredito vazio.</p>
 */
@ApplicationScoped
public class TlsInspectorService {

    @Inject
    TelemetriaLogger telemetriaLogger;

    public ResultadoInspecaoTls inspecionar(
            String hostAcessado,
            String nomeCertificado,
            String diasParaExpirarRaw,
            String autoassinado,
            String versaoTls,
            String cipher) {
        return telemetriaLogger.medir("seguranca", "inspecao_tls", () -> {
            int dias = validar(hostAcessado, nomeCertificado, diasParaExpirarRaw, versaoTls, cipher);
            boolean selfSigned = "sim".equalsIgnoreCase(trim(autoassinado));

            List<Checagem> checagens = new ArrayList<>();
            checagens.add(checarNome(trim(hostAcessado), trim(nomeCertificado)));
            checagens.add(checarValidade(dias));
            checagens.add(checarCadeia(selfSigned));
            checagens.add(checarProtocolo(trim(versaoTls)));
            checagens.add(checarCipher(trim(cipher)));

            String pior = piorEstado(checagens);
            String veredito;
            String vereditoCor;
            String resumo;
            switch (pior) {
                case "FALHA" -> {
                    veredito = "RECUSADO PELO NAVEGADOR";
                    vereditoCor = "danger";
                    resumo = "Pelo menos uma checagem falhou. Um navegador exibiria o alerta vermelho e "
                            + "bloquearia a página antes de qualquer dado trafegar.";
                }
                case "ATENÇÃO" -> {
                    veredito = "ACEITO COM RESSALVAS";
                    vereditoCor = "warning";
                    resumo = "A conexão seria aceita, mas há pontos a endurecer — legado que ainda funciona "
                            + "hoje e vira dívida amanhã.";
                }
                default -> {
                    veredito = "CONFIÁVEL";
                    vereditoCor = "success";
                    resumo = "Todas as checagens passaram. Lembre-se: isto prova o CANAL e a identidade do "
                            + "domínio, não a intenção de quem o controla.";
                }
            }

            telemetriaLogger.logEvent("info", "seguranca", "tls_inspecionado", Map.of(
                    "host", trim(hostAcessado), "veredito", veredito, "checagens", checagens.size()));

            return new ResultadoInspecaoTls(veredito, vereditoCor, resumo, checagens);
        });
    }

    private Checagem checarNome(String host, String nomeCert) {
        if (nomeConfere(host, nomeCert)) {
            return ok("Nome (SAN)", "O nome acessado (" + host + ") corresponde ao do certificado ("
                    + nomeCert + ").");
        }
        return falha("Nome (SAN)", "O nome acessado (" + host + ") NÃO está no certificado (" + nomeCert
                + "). É o erro NET::ERR_CERT_COMMON_NAME_INVALID — e o que um MITM com certificado de "
                + "outro domínio provocaria.");
    }

    private Checagem checarValidade(int dias) {
        if (dias < 0) {
            return falha("Validade", "O certificado está EXPIRADO (" + (-dias) + " dia(s) atrás). "
                    + "Renovação automática costuma ter parado — o navegador recusa.");
        }
        if (dias == 0) {
            // Vence hoje: ainda vale até o instante do notAfter (auditoria CALC-37: saía "EXPIRADO, 0 dia atrás").
            return atencao("Validade", "Vence HOJE: ainda vale até o horário do notAfter. Renove agora "
                    + "(ACME/certbot) — depois disso o navegador recusa.");
        }
        if (dias <= 15) {
            return atencao("Validade", "Expira em " + dias + " dia(s). Ainda válido, mas perto do limite: "
                    + "confira se a renovação automática (ACME/certbot) está de pé.");
        }
        return ok("Validade", "Válido por mais " + dias + " dia(s).");
    }

    private Checagem checarCadeia(boolean selfSigned) {
        if (selfSigned) {
            return falha("Cadeia de confiança", "Certificado autoassinado ou de emissor não confiável: "
                    + "não sobe até uma CA raiz do navegador. Aceitar é o que um MITM com CA própria explora.");
        }
        return ok("Cadeia de confiança", "Emitido por CA confiável — a cadeia sobe até uma raiz do "
                + "truststore. (Envie sempre a intermediária junto com a folha.)");
    }

    private Checagem checarProtocolo(String versao) {
        String v = versao.toUpperCase(Locale.ROOT).replace(" ", "");
        if (v.contains("1.3")) {
            return ok("Protocolo", versao + ": versão viva, sem os recursos legados que abriram os "
                    + "ataques da era 1.2.");
        }
        if (v.contains("1.2")) {
            return atencao("Protocolo", versao + ": aceitável, mas exija 1.3 quando o público permitir — "
                    + "1.2 ainda carrega superfície de downgrade.");
        }
        return falha("Protocolo", versao + ": versão obsoleta (TLS 1.0/1.1 ou SSL). O SSL 3.0 caiu pelo POODLE, o TLS 1.0 "
                + "pelo BEAST, e os dois abrem margem a downgrade — desligue no servidor.");
    }

    private Checagem checarCipher(String cipher) {
        String c = cipher.toUpperCase(Locale.ROOT);
        if (c.contains("RC4") || c.contains("3DES") || c.contains("DES") || c.contains("NULL")
                || c.contains("EXPORT") || c.contains("ANON")) {
            return falha("Cipher", cipher + ": cifra quebrada ou nula (RC4/3DES/EXPORT/NULL). Presença em "
                    + "auditoria é achado, não configuração.");
        }
        if (c.contains("GCM") || c.contains("CHACHA20") || c.contains("POLY1305")) {
            return ok("Cipher", cipher + ": AEAD moderna, sem os problemas de padding do CBC.");
        }
        if (c.contains("CBC")) {
            return atencao("Cipher", cipher + ": modo CBC — Lucky13 (timing do padding) e, no TLS 1.0, BEAST (IV previsível). "
                    + "Prefira uma suite AEAD (GCM/CHACHA20).");
        }
        return atencao("Cipher", cipher + ": não reconhecida como AEAD conhecida. Confirme se é uma suite "
                + "moderna antes de confiar.");
    }

    /** Correspondência de nome com suporte a curinga de um rótulo à esquerda. */
    private static boolean nomeConfere(String host, String nomeCert) {
        String h = host.toLowerCase(Locale.ROOT);
        String n = nomeCert.toLowerCase(Locale.ROOT);
        if (h.equals(n)) {
            return true;
        }
        if (n.startsWith("*.")) {
            String sufixo = n.substring(1); // ".dominio.com"
            if (h.endsWith(sufixo)) {
                String rotulo = h.substring(0, h.length() - sufixo.length());
                return !rotulo.isEmpty() && !rotulo.contains("."); // curinga cobre UM rótulo
            }
        }
        return false;
    }

    private static String piorEstado(List<Checagem> checagens) {
        boolean temAtencao = false;
        for (Checagem c : checagens) {
            if ("FALHA".equals(c.estado())) {
                return "FALHA";
            }
            if ("ATENÇÃO".equals(c.estado())) {
                temAtencao = true;
            }
        }
        return temAtencao ? "ATENÇÃO" : "OK";
    }

    private static Checagem ok(String nome, String detalhe) {
        return new Checagem(nome, "OK", "success", detalhe, "check_circle");
    }

    private static Checagem atencao(String nome, String detalhe) {
        return new Checagem(nome, "ATENÇÃO", "warning", detalhe, "warning");
    }

    private static Checagem falha(String nome, String detalhe) {
        return new Checagem(nome, "FALHA", "danger", detalhe, "cancel");
    }

    private int validar(String host, String nomeCert, String diasRaw, String versao, String cipher) {
        exigir(host, "Nome acessado não informado.");
        exigir(nomeCert, "Nome do certificado (SAN) não informado.");
        exigir(versao, "Versão do TLS não informada.");
        exigir(cipher, "Cipher suite não informada.");
        if (host.length() > 255 || nomeCert.length() > 255 || versao.length() > 40 || cipher.length() > 80) {
            throw new SegurancaException("Entrada muito longa.");
        }
        if (perigoso(host) || perigoso(nomeCert) || perigoso(versao) || perigoso(cipher)) {
            throw new SegurancaException("Caracteres inválidos detectados nas entradas.");
        }
        int dias;
        try {
            dias = Integer.parseInt(diasRaw == null ? "" : diasRaw.trim());
        } catch (NumberFormatException ex) {
            throw new SegurancaException("Dias para expirar inválido (informe um número inteiro, negativo se expirado).");
        }
        // Faixa plausível (~100 anos): evita overflow em -dias (Integer.MIN_VALUE) e valor absurdo.
        if (dias < -36500 || dias > 36500) {
            throw new SegurancaException("Dias para expirar fora de uma faixa plausível (-36500 a 36500).");
        }
        return dias;
    }

    private static void exigir(String valor, String mensagem) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new SegurancaException(mensagem);
        }
    }

    private static boolean perigoso(String valor) {
        // HTML/script E caracteres de controle (\n \r \t) — estes últimos porque o host
        // é ecoado no corpo do evento de telemetria; sem barrá-los, um newline forja uma
        // linha de log (injeção de log). O host aqui só é comparado ao SAN; quem o ecoa como resposta de
        // rede é o DiagnosticoService, que por isso exige lista branca (validarAlvo).
        return valor.indexOf('<') >= 0 || valor.indexOf('>') >= 0
                || valor.indexOf('"') >= 0 || valor.indexOf('\'') >= 0 || valor.indexOf('`') >= 0
                || valor.indexOf('\n') >= 0 || valor.indexOf('\r') >= 0 || valor.indexOf('\t') >= 0;
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }
}
