package org.framework.net.analiseDidatica.support;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Explicação didática ("Causa" e "Como corrigir") da mensagem de erro da Análise Didática.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> o aluno lê, abaixo do erro, por que a entrada foi recusada e o que fazer.
 * A versão anterior procurava substrings soltas: "cidr" casava com "use a aba CIDR" e com "Comparador CIDR"
 * e respondia "o prefixo precisa ser de 0 a 32" a quem só tinha deixado o IP em branco; "máscara decimal
 * inválida" e "wildcard inválida" nunca casavam com o "inválido: octeto 2..." que o parser gera; e todo erro
 * com "domínio" virava "não pôde ser resolvido no DNS", inclusive o domínio vazio (auditoria FRONT-06).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> a explicação nunca contradiz a mensagem — cada regra casa com a FORMA
 * da frase que o produtor escreve (início ou trecho distintivo), na ordem do mais específico para o mais
 * genérico; mensagem sem regra recebe o texto genérico, que não afirma causa nenhuma.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> {@code explicar} devolve {@code null} para erro nulo ou vazio
 * (não há o que explicar) e nunca lança; {@code motivoAnalise} devolve o texto genérico para modo
 * desconhecido.</p>
 */
@ApplicationScoped
public class ErroDidaticoService {

    private record Regra(Pattern forma, String causa, String comoCorrigir) {
        Regra(String regex, String causa, String comoCorrigir) {
            this(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), causa, comoCorrigir);
        }
    }

    /** Da mais específica para a mais genérica: a primeira que casar responde. */
    private static final List<Regra> REGRAS = List.of(
            new Regra("entre 0 e 32|^CIDR inválido",
                    "O prefixo (o número depois da barra) precisa ser um inteiro de 0 a 32.",
                    "Exemplos válidos: 8, 16, 20, 24, 30."),
            new Regra("inválid[oa]: octeto \\d+ (está vazio|não é numérico)",
                    "Um dos 4 octetos está vazio ou tem algo que não é número.",
                    "Use quatro números separados por ponto, como 192.168.0.1 ou 255.255.255.0."),
            new Regra("inválid[oa]: octeto \\d+ com zero à esquerda",
                    "Octeto com zero à esquerda é ambíguo: muitos sistemas leem 010 como octal (8), não 10.",
                    "Escreva o octeto sem o zero (010 → 10)."),
            new Regra("inválid[oa]: octeto \\d+ fora de 0-255",
                    "Um dos octetos passa do limite: cada octeto vai de 0 a 255.",
                    "Confira o octeto indicado na mensagem (ex.: 192.168.0.300 → 192.168.0.30)."),
            new Regra("^Máscara decimal inválida\\. Use máscara contígua",
                    "A máscara precisa ter os bits de rede contíguos: uns à esquerda, zeros à direita.",
                    "Use máscara contínua, como 255.255.255.0 ou 255.255.240.0."),
            new Regra("^Wildcard inválida\\. Use formato x\\.x\\.x\\.x com inverso",
                    "A wildcard deve ser o inverso de uma máscara contígua: zeros à esquerda, uns à direita.",
                    "Ex.: 0.0.15.255 é o inverso de 255.255.240.0 (/20)."),
            new Regra("inválid[oa]\\. Use formato x\\.x\\.x\\.x",
                    "O valor não tem a forma de um IPv4: 4 octetos separados por ponto.",
                    "Use formato x.x.x.x (ex.: 172.19.0.10)."),
            new Regra("^IPv6 (inválido|com zone index)",
                    "O endereço não segue o formato IPv6: até 8 grupos hexadecimais separados por dois-pontos, "
                            + "com o :: usado uma vez só.",
                    "Ex.: 2001:db8::1 ou fe80::1%eth0."),
            new Regra("^Domínio/hostname inválido",
                    "O nome não tem a forma de um hostname: letras, números, hífen e ponto.",
                    "Use algo como google.com ou servidor.local."),
            new Regra("^Não foi possível resolver",
                    "O DNS não devolveu endereço para esse nome.",
                    "Confira a grafia; teste com google.com para saber se a resolução está funcionando."),
            new Regra("^(Erro interno ao resolver|Resolução AAAA interrompida)",
                    "A consulta DNS falhou do lado do servidor (tempo esgotado ou erro interno).",
                    "Tente de novo em alguns segundos."),
            new Regra("não pertence a este navegador",
                    "O histórico fica só no navegador em que a consulta foi feita, e some ao fechá-lo.",
                    "Refaça a análise neste navegador."),
            new Regra("^Modo de análise não suportado",
                    "O modo pedido não existe nesta página.",
                    "Escolha um dos modos da lista e tente de novo."),
            new Regra("^(No modo |Selecione um modo)|\\bvazio\\.$",
                    "Faltou preencher um campo que este modo exige.",
                    "Preencha o campo indicado na mensagem e analise de novo."));

    /**
     * Causa e correção para a mensagem de erro dada.
     *
     * @return mapa com {@code causa} e {@code como_corrigir}; {@code null} se a mensagem for nula ou vazia
     */
    public Map<String, String> explicar(String erro) {
        String txt = erro == null ? "" : erro.strip();
        if (txt.isEmpty()) {
            return null;
        }
        for (Regra regra : REGRAS) {
            if (regra.forma().matcher(txt).find()) {
                return explicacao(regra.causa(), regra.comoCorrigir());
            }
        }
        return explicacao("A entrada não passou nas validações do modo selecionado.",
                "Revise os campos obrigatórios do modo e tente novamente.");
    }

    private static Map<String, String> explicacao(String causa, String comoCorrigir) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("causa", causa);
        out.put("como_corrigir", comoCorrigir);
        return out;
    }

    public String motivoAnalise(String modo) {
        return switch (modo == null ? "" : modo) {
            case "cidr" -> "Usuário pediu cálculo de sub-rede CIDR para validar rede/hosts.";
            case "mask" -> "Usuário pediu decomposição didática de máscara decimal e barra.";
            case "wildcard" -> "Usuário pediu análise wildcard (ACL, EIGRP, OSPF, redes Cisco).";
            case "autoip" -> "Usuário pediu descoberta automática de CIDR a partir do IP.";
            case "dominio" -> "Usuário pediu resolução DNS e decomposição técnica do destino.";
            case "ipv6" -> "Usuário pediu análise didática de endereço IPv6.";
            case "comparador" -> "Usuário pediu comparação lado a lado entre dois prefixos CIDR.";
            case "geo" -> "Usuário consultou região geográfica (GeoIP).";
            default -> "Usuário executou análise técnica no framework.";
        };
    }
}
