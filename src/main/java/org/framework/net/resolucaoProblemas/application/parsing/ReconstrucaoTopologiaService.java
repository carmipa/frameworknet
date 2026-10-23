package org.framework.net.resolucaoProblemas.application.parsing;

import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.resolucaoProblemas.domain.kernel.MascaraIpv4;
import org.framework.net.resolucaoProblemas.domain.model.AchadoConfiguracao;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.EnlaceReconstruido;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.LanReconstruida;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.LinhaTabela;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.ScriptCorrigido;
import org.framework.net.resolucaoProblemas.domain.model.CenarioReconstruido.TabelaRoteador;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.BlocoRoteamento;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.InterfaceLida;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.RedeAnunciada;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.RotaEstatica;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.RoteadorLido;
import org.framework.net.resolucaoProblemas.domain.model.ConfiguracaoLida.VizinhoBgp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Monta o projeto visível a partir dos roteadores já auditados.
 *
 * <p><b>Propósito de negócio:</b> entregar, a partir de configuração existente, o
 * mesmo material que a aba "Projetar" entrega a partir de requisitos — LANs com
 * faixa e gateway, enlaces ponto a ponto com as duas pontas, tabela por roteador,
 * desenho da topologia e o script pronto para colar no Packet Tracer. É a metade
 * final da engenharia reversa.</p>
 *
 * <p><b>Invariantes do domínio:</b> nada é inventado. Enlace com uma ponta só
 * aparece marcado como incompleto em vez de ganhar um vizinho fictício; interface
 * sem endereço não vira LAN; o script corrigido reproduz apenas o que foi lido ou
 * corrigido com evidência, com cada linha alterada comentada em
 * {@code ! CORRIGIDO:}.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> lista de roteadores vazia produz
 * coleções vazias e diagrama vazio; não lança.</p>
 */
@ApplicationScoped
public class ReconstrucaoTopologiaService {

    /** Prefixo a partir do qual uma sub-rede é tratada como enlace ponto a ponto. */
    private static final int PREFIXO_PONTO_A_PONTO = 30;

    /**
     * LANs por trás dos roteadores.
     *
     * <p><b>Invariantes do domínio:</b> só entram interfaces com endereço e
     * prefixo menor que {@code /30} — {@code /30} e {@code /31} são enlace, não
     * rede de usuário. Loopback fica de fora: é endereço de gerência, não LAN.</p>
     */
    public List<LanReconstruida> montarLans(List<RoteadorLido> roteadores) {
        List<LanReconstruida> lans = new ArrayList<>();
        for (RoteadorLido r : roteadores) {
            for (InterfaceLida i : r.interfacesComIp()) {
                if (i.prefixo() >= PREFIXO_PONTO_A_PONTO || i.loopback()) {
                    continue;
                }
                String rede = MascaraIpv4.enderecoDeRede(i.ip(), i.prefixo());
                String broadcast = MascaraIpv4.broadcastDe(i.ip(), i.prefixo());
                long redeLong = MascaraIpv4.ipParaLong(rede);
                lans.add(new LanReconstruida(
                        r.hostname(),
                        i.nome(),
                        rede,
                        i.prefixo(),
                        i.mascara(),
                        MascaraIpv4.wildcardDe(i.prefixo()),
                        i.ip(),
                        MascaraIpv4.longParaIp(redeLong + 1),
                        MascaraIpv4.longParaIp(MascaraIpv4.ipParaLong(broadcast) - 1),
                        broadcast,
                        MascaraIpv4.hostsUtilizaveis(i.prefixo()),
                        anunciada(r, rede)));
            }
        }
        return lans;
    }

    private boolean anunciada(RoteadorLido r, String rede) {
        for (BlocoRoteamento bloco : r.blocos()) {
            for (RedeAnunciada anuncio : bloco.redes()) {
                if (rede.equals(anuncio.rede())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Enlaces ponto a ponto entre roteadores.
     *
     * <p><b>Invariantes do domínio:</b> um enlace com uma ponta só é devolvido com
     * {@code completo = false}. Completá-lo com a outra ponta calculada seria
     * inventar um roteador que ninguém colou.</p>
     */
    public List<EnlaceReconstruido> montarEnlaces(List<RoteadorLido> roteadores) {
        Map<String, List<Ponta>> porSubRede = new LinkedHashMap<>();
        for (RoteadorLido r : roteadores) {
            for (InterfaceLida i : r.interfacesComIp()) {
                if (i.prefixo() < PREFIXO_PONTO_A_PONTO) {
                    continue;
                }
                porSubRede.computeIfAbsent(MascaraIpv4.cidrDe(i.ip(), i.prefixo()),
                        k -> new ArrayList<>()).add(new Ponta(r.hostname(), i));
            }
        }

        List<EnlaceReconstruido> enlaces = new ArrayList<>();
        for (Map.Entry<String, List<Ponta>> entrada : porSubRede.entrySet()) {
            List<Ponta> pontas = entrada.getValue();
            Ponta a = pontas.get(0);
            String rede = entrada.getKey().split("/")[0];
            int prefixo = a.itf().prefixo();

            if (pontas.size() >= 2) {
                Ponta b = pontas.get(1);
                String dce = a.itf().temClockRate() ? a.roteador() + " " + a.itf().nome()
                        : b.itf().temClockRate() ? b.roteador() + " " + b.itf().nome() : "não definido";
                enlaces.add(new EnlaceReconstruido(rede, prefixo, a.itf().mascara(),
                        a.roteador(), a.itf().nome(), a.itf().ip(),
                        b.roteador(), b.itf().nome(), b.itf().ip(), dce, true));
            } else {
                String par = MascaraIpv4.parPontoAPonto(a.itf().ip(), prefixo);
                enlaces.add(new EnlaceReconstruido(rede, prefixo, a.itf().mascara(),
                        a.roteador(), a.itf().nome(), a.itf().ip(),
                        "", "", par.isEmpty() ? "-" : par + " (livre)",
                        a.itf().temClockRate() ? a.roteador() + " " + a.itf().nome() : "não definido",
                        false));
            }
        }
        return enlaces;
    }

    /** Tabela por roteador, no formato usado na montagem do laboratório. */
    public List<TabelaRoteador> montarTabelas(List<RoteadorLido> roteadores) {
        List<TabelaRoteador> tabelas = new ArrayList<>();
        for (RoteadorLido r : roteadores) {
            List<LinhaTabela> linhas = new ArrayList<>();
            for (InterfaceLida i : r.interfaces()) {
                String papel = i.loopback() ? "Loopback"
                        : i.prefixo() >= PREFIXO_PONTO_A_PONTO ? "WAN (ponto a ponto)"
                        : i.temIp() ? "LAN" : "sem endereço";
                List<String> notas = new ArrayList<>();
                if (i.temClockRate()) {
                    notas.add("DCE (clock rate)");
                }
                if (i.temIp() && i.shutdownExplicito()) {
                    notas.add("shutdown explícito");
                } else if (i.temIp() && !i.noShutdown()) {
                    notas.add("falta no shutdown");
                }
                if (i.vlan() > 0) {
                    notas.add("VLAN " + i.vlan());
                }
                if (!i.descricao().isBlank()) {
                    notas.add(i.descricao());
                }
                linhas.add(new LinhaTabela(i.nome(),
                        i.temIp() ? i.ip() : "-",
                        i.temIp() ? i.mascara() : "-",
                        i.temIp() ? MascaraIpv4.cidrDe(i.ip(), i.prefixo()) : "-",
                        papel,
                        String.join(" · ", notas)));
            }
            tabelas.add(new TabelaRoteador(r.hostname(), r.asBgp(), linhas));
        }
        return tabelas;
    }

    /**
     * Diagrama Mermaid do que foi reconstruído.
     *
     * <p><b>Invariantes do domínio:</b> o desenho mostra apenas roteadores lidos e
     * enlaces com as duas pontas encontradas; enlace incompleto vira nó explícito
     * de pendência, para que a falta apareça no desenho em vez de sumir.</p>
     */
    public String montarMermaid(List<RoteadorLido> roteadores, List<EnlaceReconstruido> enlaces,
                                List<LanReconstruida> lans) {
        if (roteadores.isEmpty()) {
            return "";
        }
        List<String> linhas = new ArrayList<>();
        linhas.add("graph LR");

        Map<String, String> idPorRoteador = new LinkedHashMap<>();
        int indice = 1;
        for (RoteadorLido r : roteadores) {
            String id = "R" + indice;
            idPorRoteador.put(r.hostname(), id);
            String as = r.asBgp() > 0 ? "\\nAS " + r.asBgp() : "";
            // TODAS as LANs do roteador entram no desenho — não apenas a primeira.
            // Omitir uma LAN faz o diagrama parecer completo enquanto esconde uma
            // sub-rede: o pior resultado numa ferramenta didática de reconstrução.
            List<LanReconstruida> lansDoRoteador = lans.stream()
                    .filter(l -> l.roteador().equals(r.hostname())).toList();

            linhas.add("    subgraph SG" + indice + "[\"" + escapar(r.hostname())
                    + (r.asBgp() > 0 ? " · AS " + r.asBgp() : "") + "\"]");
            linhas.add("        " + id + "[\"" + escapar(r.hostname()) + as + "\"]");
            int seqLan = 1;
            for (LanReconstruida lan : lansDoRoteador) {
                String lanId = "L" + indice + "_" + seqLan;
                linhas.add("        " + lanId + "[\"" + escapar(lan.nomeInterface()) + ": "
                        + escapar(lan.gateway()) + "\\nLAN " + escapar(lan.cidr()) + "\\n"
                        + lan.hostsUtilizaveis() + " hosts\"]");
                linhas.add("        " + id + " --- " + lanId);
                seqLan++;
            }
            linhas.add("    end");
            indice++;
        }

        int pendente = 1;
        for (EnlaceReconstruido enlace : enlaces) {
            String origem = idPorRoteador.get(enlace.roteadorA());
            if (origem == null) {
                continue;
            }
            if (enlace.completo()) {
                String destino = idPorRoteador.get(enlace.roteadorB());
                if (destino == null) {
                    continue;
                }
                linhas.add("    " + origem + " ---|\"" + enlace.cidr() + "\\n" + enlace.ipA()
                        + " ↔ " + enlace.ipB() + "\"| " + destino);
            } else {
                String id = "P" + pendente++;
                linhas.add("    " + id + "[\"ponta ausente\\n" + enlace.cidr() + "\"]");
                linhas.add("    " + origem + " -.->|\"" + enlace.cidr() + "\"| " + id);
            }
        }
        return String.join("\n", linhas);
    }

    /**
     * Script Cisco de cada roteador, já corrigido.
     *
     * <p><b>Invariantes do domínio:</b> cada linha alterada é precedida de um
     * comentário {@code ! CORRIGIDO:} com o valor original. O usuário precisa
     * conseguir ver, no próprio arquivo que vai colar no equipamento, o que a
     * ferramenta mexeu — script corrigido sem marca vira configuração aplicada às
     * cegas.</p>
     */
    public List<ScriptCorrigido> montarScripts(
            List<RoteadorLido> roteadores, List<AchadoConfiguracao> achados) {

        List<ScriptCorrigido> scripts = new ArrayList<>();
        for (RoteadorLido r : roteadores) {
            List<AchadoConfiguracao> correcoes = achados.stream()
                    .filter(a -> a.severidade() == AchadoConfiguracao.Severidade.ERRO_CORRIGIDO)
                    .filter(a -> r.hostname().equals(a.roteador()))
                    .toList();

            List<String> linhas = new ArrayList<>();
            linhas.add("!");
            linhas.add("! " + r.hostname() + " — script reconstruído pela engenharia reversa");
            linhas.add("! " + (correcoes.isEmpty() ? "nenhuma correção aplicada"
                    : correcoes.size() + " correção(ões) aplicada(s), marcadas abaixo"));
            linhas.add("!");
            linhas.add("enable");
            linhas.add("configure terminal");
            linhas.add("hostname " + r.hostname());

            for (InterfaceLida i : r.interfaces()) {
                linhas.add("!");
                linhas.add("interface " + i.nome());
                if (i.vlan() > 0) {
                    linhas.add(" encapsulation dot1Q " + i.vlan() + (i.dot1qNativa() ? " native" : ""));
                }
                if (!i.descricao().isBlank()) {
                    linhas.add(" description " + i.descricao());
                }
                if (i.temIp()) {
                    String comando = "ip address " + i.ip() + " " + i.mascara();
                    marcarCorrecao(linhas, correcoes, comando);
                    linhas.add(" " + comando);
                }
                for (String secundario : i.secundarios()) {
                    linhas.add(" ip address " + secundario + " secondary");
                }
                if (i.temClockRate()) {
                    linhas.add(" clock rate " + i.clockRateBps());
                }
                // Reconstrução FIEL dos TRÊS estados administrativos distintos:
                // "shutdown" explícito, "no shutdown" explícito e comando ausente
                // (nada emitido). Colapsar shutdown com ausência apagaria a intenção
                // do operador — a interface endereçada sem "no shutdown" já é
                // sinalizada como achado pela auditoria.
                if (i.shutdownExplicito()) {
                    linhas.add(" shutdown");
                } else if (i.noShutdown()) {
                    linhas.add(" no shutdown");
                }
                linhas.add(" exit");
            }

            for (BlocoRoteamento bloco : r.blocos()) {
                linhas.add("!");
                linhas.add("router " + bloco.protocolo().toLowerCase(java.util.Locale.ROOT)
                        + (bloco.identificador() > 0 ? " " + bloco.identificador() : ""));
                for (String outra : bloco.outrasLinhas()) {
                    linhas.add(" " + outra);
                }
                for (VizinhoBgp v : bloco.vizinhos()) {
                    linhas.add(" neighbor " + v.ip() + " remote-as " + v.remoteAs());
                }
                for (RedeAnunciada rede : bloco.redes()) {
                    String comando = comandoDeRede(bloco, rede);
                    marcarCorrecao(linhas, correcoes, comando);
                    linhas.add(" " + comando);
                }
                if (bloco.autoSummaryDesligado()) {
                    linhas.add(" no auto-summary");
                }
                linhas.add(" exit");
            }

            for (RotaEstatica rota : r.rotasEstaticas()) {
                linhas.add("ip route " + rota.rede() + " " + rota.mascara() + " " + rota.proximoSalto()
                        + (rota.complemento().isEmpty() ? "" : " " + rota.complemento()));
            }

            linhas.add("!");
            linhas.add("end");
            linhas.add("write memory");
            scripts.add(new ScriptCorrigido(r.hostname(), String.join("\n", linhas), correcoes.size()));
        }
        return scripts;
    }

    private void marcarCorrecao(List<String> linhas, List<AchadoConfiguracao> correcoes, String comando) {
        for (AchadoConfiguracao a : correcoes) {
            if (a.corrigido() != null && a.corrigido().strip().equals(comando)) {
                linhas.add(" ! CORRIGIDO: era \"" + a.original().strip() + "\" — " + a.evidencia());
                return;
            }
        }
    }

    private String comandoDeRede(BlocoRoteamento bloco, RedeAnunciada rede) {
        if ("BGP".equals(bloco.protocolo()) && !rede.mascara().isBlank()) {
            return "network " + rede.rede() + " mask " + rede.mascara();
        }
        if ("OSPF".equals(bloco.protocolo())) {
            return "network " + rede.rede() + " " + rede.wildcard()
                    + (rede.area() >= 0 ? " area " + rede.area() : "");
        }
        if (!rede.wildcard().isBlank()) {
            return "network " + rede.rede() + " " + rede.wildcard();
        }
        return "network " + rede.rede();
    }

    private String escapar(String texto) {
        return texto == null ? "" : texto.replace("\"", "'").replace("\n", " ");
    }

    private record Ponta(String roteador, InterfaceLida itf) {
    }
}
