package org.framework.net.segurancaRede.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.segurancaRede.domain.DiagnosticoFluxo;
import org.framework.net.segurancaRede.domain.DiagnosticoFluxo.Salto;
import org.framework.net.segurancaRede.domain.TopologiaRede;
import org.framework.net.segurancaRede.domain.TopologiaRede.Dispositivo;
import org.framework.net.segurancaRede.domain.TopologiaRede.Enlace;
import org.framework.net.segurancaRede.exception.SegurancaException;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Motor de alcançabilidade sobre uma topologia MONTADA pelo usuário (P05 fase 2).
 *
 * <p><b>Propósito de negócio:</b> receber a topologia descrita no montador
 * (equipamentos, enlaces, VLANs, ACLs) e um fluxo origem→destino:porta, e
 * responder se chega e ONDE trava — a mesma resposta {@link DiagnosticoFluxo} do
 * cenário fixo, agora sobre um grafo arbitrário.</p>
 *
 * <p><b>Invariantes do domínio:</b> determinístico; acha o caminho por busca em
 * largura (menor salto) e o percorre aplicando a regra de cada nó, parando no
 * PRIMEIRO ponto que recusa (nenhum salto depois do bloqueio). Entrada inválida
 * (id inexistente, origem que não é host, número inválido) é recusada — nunca se
 * inventa um veredito.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> texto/entrada inválidos lançam
 * {@link SegurancaException} (HTTP 400 pelo mapper).</p>
 */
@ApplicationScoped
public class AvaliadorTopologiaService {

    private static final int MAX_LINHAS = 60;

    /** Atributos aceitos por tipo de equipamento — qualquer outro é erro de entrada. */
    private static final Map<String, Set<String>> ATRIBUTOS_POR_TIPO = Map.of(
            "host", Set.of("vlan", "gw"),
            "switchl3", Set.of("vlans"),
            "firewall", Set.of("deny"),
            "server", Set.of("vlan", "porta"));

    @Inject
    TelemetriaLogger telemetriaLogger;

    /** Ponto de entrada da apresentação: texto do montador + fluxo → diagnóstico. */
    public DiagnosticoFluxo diagnosticar(String texto, String origem, String destino, String portaRaw) {
        return telemetriaLogger.medir("seguranca", "topologia_fluxo", () -> {
            TopologiaRede topo = parse(texto);
            String o = exigir(origem, "Informe a origem do fluxo.");
            String d = exigir(destino, "Informe o destino do fluxo.");
            int porta = porta(portaRaw);
            DiagnosticoFluxo r = avaliar(topo, o, d, porta);
            telemetriaLogger.logEvent("info", "seguranca", "topologia_fluxo", Map.of(
                    "dispositivos", topo.dispositivos().size(),
                    "enlaces", topo.enlaces().size(),
                    "alcanca", r.alcanca()));
            return r;
        });
    }

    // ------------------------------------------------------------------ parser

    public TopologiaRede parse(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new SegurancaException("Descreva a topologia (equipamentos e enlaces).");
        }
        String[] linhas = texto.split("\\r?\\n");
        if (linhas.length > MAX_LINHAS) {
            throw new SegurancaException("Topologia muito grande (máximo " + MAX_LINHAS + " linhas).");
        }
        List<Dispositivo> dispositivos = new ArrayList<>();
        List<Enlace> enlaces = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int numero = 0;
        for (String bruta : linhas) {
            numero++;
            String linha = bruta.strip();
            if (linha.isEmpty() || linha.startsWith("#")) {
                continue;
            }
            String[] t = linha.split("\\s+");
            String tipo = t[0].toLowerCase(Locale.ROOT);
            if (tipo.equals("link")) {
                if (t.length < 3) {
                    throw new SegurancaException("Linha " + numero + ": link precisa de dois equipamentos.");
                }
                if (t.length > 3) {
                    throw new SegurancaException("Linha " + numero
                            + ": link aceita só dois equipamentos (token extra '" + t[3] + "').");
                }
                enlaces.add(new Enlace(t[1], t[2]));
                continue;
            }
            if (!List.of("host", "switchl3", "firewall", "server").contains(tipo)) {
                throw new SegurancaException("Linha " + numero + ": tipo não suportado '" + t[0]
                        + "' (use host, switchl3, firewall, server ou link).");
            }
            if (t.length < 2) {
                throw new SegurancaException("Linha " + numero + ": falta o nome do equipamento.");
            }
            String id = t[1];
            if (!ids.add(id)) {
                throw new SegurancaException("Linha " + numero + ": id repetido '" + id + "'.");
            }
            Map<String, String> attrs = new HashMap<>();
            Set<String> permitidos = ATRIBUTOS_POR_TIPO.getOrDefault(tipo, Set.of());
            for (int i = 2; i < t.length; i++) {
                int eq = t[i].indexOf('=');
                if (eq <= 0) {
                    throw new SegurancaException("Linha " + numero + ": token inesperado '" + t[i]
                            + "' (atributos são no formato chave=valor).");
                }
                String chave = t[i].substring(0, eq).toLowerCase(Locale.ROOT);
                if (!permitidos.contains(chave)) {
                    throw new SegurancaException("Linha " + numero + ": atributo '" + chave
                            + "' não é válido para " + tipo + " (aceita: " + String.join(", ", permitidos) + ").");
                }
                if (attrs.containsKey(chave)) {
                    throw new SegurancaException("Linha " + numero + ": atributo '" + chave + "' repetido.");
                }
                attrs.put(chave, t[i].substring(eq + 1));
            }
            dispositivos.add(new Dispositivo(id, tipo,
                    vlanAtributo(attrs.get("vlan"), numero),
                    attrs.getOrDefault("gw", ""),
                    vlansAtributo(attrs.get("vlans"), numero),
                    denyAtributo(attrs.get("deny"), numero),
                    portaAtributo(attrs.get("porta"), numero)));
        }
        // Enlaces só entre ids existentes.
        for (Enlace e : enlaces) {
            if (!ids.contains(e.a()) || !ids.contains(e.b())) {
                throw new SegurancaException("Enlace liga equipamento inexistente: " + e.a() + " — " + e.b() + ".");
            }
        }
        if (dispositivos.isEmpty()) {
            throw new SegurancaException("Nenhum equipamento reconhecido na topologia.");
        }
        return new TopologiaRede(dispositivos, enlaces);
    }

    // ------------------------------------------------------------------ motor

    /**
     * Percorre o fluxo de um host até o destino e diz onde ele para.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o aluno monta a topologia e vê, salto a salto, se o tráfego chega.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> destino na mesma VLAN é entregue por camada 2, sem gateway nem SVI;
     * destino em outra VLAN vai pelo gateway e só cruza switch L3 que roteie as duas VLANs; firewall aplica
     * a ACL; o servidor aceita só a porta que abre.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> origem ou destino inexistente, ou origem que não é host,
     * lançam {@link SegurancaException}; bloqueio no caminho vira diagnóstico com o ponto de parada.</p>
     */
    public DiagnosticoFluxo avaliar(TopologiaRede topo, String origemId, String destinoId, int porta) {
        Dispositivo origem = topo.por(origemId);
        Dispositivo destino = topo.por(destinoId);
        if (origem == null) {
            throw new SegurancaException("Origem '" + origemId + "' não existe na topologia.");
        }
        if (destino == null) {
            throw new SegurancaException("Destino '" + destinoId + "' não existe na topologia.");
        }
        if (!origem.ehTipo("host")) {
            throw new SegurancaException("A origem do fluxo precisa ser um host.");
        }

        String titulo = "Topologia montada";
        String rotuloOrigem = origemId + " (VLAN " + origem.vlan() + ")";
        String rotuloDestino = destinoId + ":" + porta;

        List<Salto> saltos = new ArrayList<>();
        int ordem = 1;

        // Mesma VLAN = mesma sub-rede: o host entrega direto, por camada 2, sem gateway nem SVI
        // (RFC 1122 §3.3.1.1). Antes o fluxo ia ao gateway e era bloqueado por "falta de SVI" (CALC-21).
        if (destino.vlan() > 0 && destino.vlan() == origem.vlan()) {
            List<String> l2 = caminhoNaVlan(topo, origemId, destinoId);
            if (l2 == null) {
                saltos.add(new Salto(ordem, origemId + " (host)", "L2", false,
                        "Destino na mesma VLAN " + origem.vlan() + ", mas sem caminho de camada 2 até ele."));
                return bloqueado(titulo, rotuloOrigem, rotuloDestino, origemId + " (host)", saltos);
            }
            saltos.add(new Salto(ordem++, origemId + " (host)", "L2", true,
                    "Destino na mesma VLAN " + origem.vlan() + " (mesma sub-rede): entrega direta, sem gateway."));
            for (int i = 1; i < l2.size() - 1; i++) {
                Dispositivo d = topo.por(l2.get(i));
                saltos.add(new Salto(ordem++, d.id() + " (" + rotuloTipo(d.tipo()) + ")", "L2", true,
                        "Comuta na VLAN " + origem.vlan() + " (camada 2; não precisa de SVI)."));
            }
            return chegada(titulo, rotuloOrigem, rotuloDestino, destinoId, destino, porta, ordem, saltos);
        }

        // Host de origem: precisa de gateway válido e ligado.
        String gw = origem.gateway();
        if (gw == null || gw.isBlank()) {
            saltos.add(new Salto(ordem, origemId + " (host)", "L3", false,
                    "Host sem gateway configurado: não sabe como sair da própria LAN."));
            return bloqueado(titulo, rotuloOrigem, rotuloDestino, origemId + " (host)", saltos);
        }
        boolean gwLigado = topo.enlaces().stream().anyMatch(e -> e.liga(origemId, gw));
        if (!gwLigado || topo.por(gw) == null) {
            saltos.add(new Salto(ordem, origemId + " (host)", "L3", false,
                    "Gateway '" + gw + "' não é vizinho do host (sem ARP do next-hop): o pacote nem sai da LAN."));
            return bloqueado(titulo, rotuloOrigem, rotuloDestino, origemId + " (host)", saltos);
        }

        // INV-TOPO-1: o caminho avaliado É o do encaminhamento configurado —
        // host -> gateway -> (menor caminho a partir do gateway), e só transita por
        // equipamentos que ENCAMINHAM (switch L3 / firewall). Sem isso, um enlace
        // paralelo faria o BFS furar o gateway, ou a busca voltaria pelo próprio host
        // de origem usando-o como roteador — papel que host/servidor não têm no modelo.
        List<String> aPartirDoGw = caminhoDoGateway(topo, gw, destinoId);
        if (aPartirDoGw == null) {
            String noGw = gw + " (" + rotuloTipo(topo.por(gw).tipo()) + ")";
            saltos.add(new Salto(ordem, noGw, "L3", false,
                    "Sem rota do gateway " + gw + " até o destino: o encaminhamento não chega."));
            return bloqueado(titulo, rotuloOrigem, rotuloDestino, noGw, saltos);
        }
        List<String> caminho = new ArrayList<>();
        caminho.add(origemId);
        caminho.addAll(aPartirDoGw);

        saltos.add(new Salto(ordem++, origemId + " (host)", "L3", true,
                "Encaminha ao gateway " + gw + " porque o destino está em outra sub-rede."));

        // Nós intermediários (entre o host e o destino).
        for (int i = 1; i < caminho.size() - 1; i++) {
            Dispositivo d = topo.por(caminho.get(i));
            String no = d.id() + " (" + rotuloTipo(d.tipo()) + ")";
            if (d.ehTipo("switchl3")) {
                boolean roteia = d.vlansRoteadas().contains(origem.vlan())
                        && d.vlansRoteadas().contains(destino.vlan());
                if (!roteia) {
                    saltos.add(new Salto(ordem, no, "L3", false,
                            "Não roteia entre VLAN " + origem.vlan() + " e VLAN " + destino.vlan()
                                    + " (SVI/rota ausente): descarta."));
                    return bloqueado(titulo, rotuloOrigem, rotuloDestino, no, saltos);
                }
                saltos.add(new Salto(ordem++, no, "L3", true,
                        "Roteia entre VLAN " + origem.vlan() + " e VLAN " + destino.vlan() + "."));
            } else if (d.ehTipo("firewall")) {
                if (nega(d, porta)) {
                    saltos.add(new Salto(ordem, no, "L4", false,
                            "ACL nega tcp/" + porta + ": deny explícito antes do destino."));
                    return bloqueado(titulo, rotuloOrigem, rotuloDestino, no, saltos);
                }
                saltos.add(new Salto(ordem++, no, "L4", true,
                        "ACL permite tcp/" + porta + "; encaminha."));
            } else {
                saltos.add(new Salto(ordem++, no, "L2", true, "Encaminha (pass-through)."));
            }
        }

        return chegada(titulo, rotuloOrigem, rotuloDestino, destinoId, destino, porta, ordem, saltos);
    }

    /** Último salto: o destino aceita (ou recusa) a porta. Comum ao caminho roteado e ao da mesma VLAN. */
    private static DiagnosticoFluxo chegada(String titulo, String rotuloOrigem, String rotuloDestino,
                                            String destinoId, Dispositivo destino, int porta, int ordem,
                                            List<Salto> saltos) {
        String noDestino = destinoId + " (" + rotuloTipo(destino.tipo()) + ")";
        if (destino.ehTipo("server") && destino.portaAberta() != porta) {
            saltos.add(new Salto(ordem, noDestino, "L4", false,
                    "Porta " + porta + " fechada no servidor (ele abre " + destino.portaAberta() + ")."));
            return bloqueado(titulo, rotuloOrigem, rotuloDestino, noDestino, saltos);
        }
        saltos.add(new Salto(ordem, noDestino, "L4", true,
                "Destino aceita a conexão na porta " + porta + "."));
        return new DiagnosticoFluxo("montado", titulo, rotuloOrigem, rotuloDestino, true, "",
                "O fluxo percorre todo o caminho e chega ao destino.", saltos);
    }

    /**
     * Caminho de camada 2 entre dois equipamentos da mesma VLAN.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o tráfego dentro da VLAN é comutado, não roteado.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> só switch L3 comuta no meio do caminho (firewall do modelo roteia;
     * host e servidor são pontas); menor caminho por busca em largura.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> sem caminho devolve {@code null}; não lança.</p>
     */
    private static List<String> caminhoNaVlan(TopologiaRede topo, String origemId, String destinoId) {
        java.util.Map<String, String> anterior = new java.util.HashMap<>();
        java.util.ArrayDeque<String> fila = new java.util.ArrayDeque<>();
        fila.add(origemId);
        anterior.put(origemId, "");
        while (!fila.isEmpty()) {
            String atual = fila.poll();
            if (atual.equals(destinoId)) {
                List<String> caminho = new ArrayList<>();
                for (String n = destinoId; !n.isEmpty(); n = anterior.get(n)) {
                    caminho.add(0, n);
                }
                return caminho;
            }
            if (!atual.equals(origemId) && !topo.por(atual).ehTipo("switchl3")) {
                continue;
            }
            for (Enlace e : topo.enlaces()) {
                String vizinho = e.a().equals(atual) ? e.b() : e.b().equals(atual) ? e.a() : null;
                if (vizinho != null && !anterior.containsKey(vizinho) && topo.por(vizinho) != null) {
                    anterior.put(vizinho, atual);
                    fila.add(vizinho);
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ diagrama

    /**
     * Diagrama Mermaid da topologia (nós + enlaces), pintando de vermelho o nó
     * onde o fluxo travou. Reparse do texto (barato) para não acoplar o resultado
     * à topologia. Ids são sanitizados para caber na sintaxe do Mermaid.
     */
    public String mermaidDe(String texto, DiagnosticoFluxo resultado) {
        TopologiaRede topo = parse(texto);
        String bloqueioId = resultado.pontoBloqueio().isBlank()
                ? "" : resultado.pontoBloqueio().split("\\s+")[0];
        List<String> linhas = new ArrayList<>();
        linhas.add("graph LR");
        for (Dispositivo d : topo.dispositivos()) {
            linhas.add("    " + seguro(d.id()) + "[\"" + rotuloNo(d) + "\"]");
        }
        for (Enlace e : topo.enlaces()) {
            linhas.add("    " + seguro(e.a()) + " --- " + seguro(e.b()));
        }
        linhas.add("    classDef bloq fill:#7f1d1d,stroke:#f87171,color:#fff;");
        linhas.add("    classDef ok fill:#064e3b,stroke:#34d399,color:#fff;");
        if (!bloqueioId.isBlank() && topo.por(bloqueioId) != null) {
            linhas.add("    class " + seguro(bloqueioId) + " bloq;");
        } else if (resultado.alcanca() && !topo.dispositivos().isEmpty()) {
            // fluxo ok: destaca o destino em verde
            String destinoId = resultado.destino().split(":")[0].split("\\s+")[0];
            if (topo.por(destinoId) != null) {
                linhas.add("    class " + seguro(destinoId) + " ok;");
            }
        }
        return String.join("\n", linhas);
    }

    private static String rotuloNo(Dispositivo d) {
        String base = d.id() + " · " + rotuloTipo(d.tipo());
        return switch (d.tipo()) {
            case "host", "server" -> base + " v" + d.vlan() + (d.ehTipo("server") && d.portaAberta() > 0
                    ? " :" + d.portaAberta() : "");
            default -> base;
        };
    }

    /** Id seguro para o Mermaid: só letras, dígitos e _. */
    private static String seguro(String id) {
        return id.replaceAll("[^A-Za-z0-9_]", "_");
    }

    // ------------------------------------------------------------------ helpers

    private static DiagnosticoFluxo bloqueado(String titulo, String origem, String destino,
                                              String ponto, List<Salto> saltos) {
        return new DiagnosticoFluxo("montado", titulo, origem, destino, false, ponto,
                "O fluxo para no primeiro ponto que o recusa — os saltos seguintes nem são tentados.", saltos);
    }

    private static boolean nega(Dispositivo firewall, int porta) {
        String alvo = "tcp/" + porta;
        for (String regra : firewall.denyRegras()) {
            if (regra.trim().toLowerCase(Locale.ROOT).equals(alvo)) {
                return true;
            }
        }
        return false;
    }

    private static String rotuloTipo(String tipo) {
        return switch (tipo) {
            case "host" -> "host";
            case "switchl3" -> "switch L3";
            case "firewall" -> "firewall";
            case "server" -> "servidor";
            default -> tipo;
        };
    }

    /**
     * Busca em largura do gateway até o destino, respeitando o encaminhamento.
     *
     * <p><b>Propósito de negócio:</b> achar o menor caminho (lista de ids) que o
     * pacote percorreria a partir do gateway, para o motor aplicar a regra de cada
     * nó ao longo dele.</p>
     *
     * <p><b>Invariantes do domínio:</b> um nó intermediário só entra no caminho se
     * souber ENCAMINHAR (switch L3 ou firewall); host e servidor são pontas — só
     * podem ser o destino, nunca trânsito. Isso impede que a busca volte pelo host
     * de origem (ou por qualquer endpoint) tratando-o como roteador.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> devolve {@code null} quando não há
     * caminho de encaminhamento até o destino — o chamador bloqueia o fluxo.</p>
     */
    private static List<String> caminhoDoGateway(TopologiaRede topo, String gateway, String destino) {
        Map<String, List<String>> adj = new HashMap<>();
        for (Dispositivo d : topo.dispositivos()) {
            adj.put(d.id(), new ArrayList<>());
        }
        for (Enlace e : topo.enlaces()) {
            adj.computeIfAbsent(e.a(), k -> new ArrayList<>()).add(e.b());
            adj.computeIfAbsent(e.b(), k -> new ArrayList<>()).add(e.a());
        }
        Map<String, String> veioDe = new HashMap<>();
        Set<String> visto = new HashSet<>();
        ArrayDeque<String> fila = new ArrayDeque<>();
        fila.add(gateway);
        visto.add(gateway);
        while (!fila.isEmpty()) {
            String atual = fila.poll();
            if (atual.equals(destino)) {
                List<String> caminho = new ArrayList<>();
                for (String n = destino; n != null; n = veioDe.get(n)) {
                    caminho.add(0, n);
                }
                return caminho;
            }
            for (String viz : adj.getOrDefault(atual, List.of())) {
                if (visto.contains(viz)) {
                    continue;
                }
                // Só transita por quem encaminha; o destino pode ser de qualquer tipo.
                if (viz.equals(destino) || encaminha(topo.por(viz))) {
                    visto.add(viz);
                    veioDe.put(viz, atual);
                    fila.add(viz);
                }
            }
        }
        return null;
    }

    /** No modelo, só switch L3 e firewall encaminham pacotes de terceiros. */
    private static boolean encaminha(Dispositivo d) {
        return d != null && (d.ehTipo("switchl3") || d.ehTipo("firewall"));
    }

    private static String exigir(String v, String msg) {
        if (v == null || v.isBlank()) {
            throw new SegurancaException(msg);
        }
        return v.strip();
    }

    private static int porta(String raw) {
        try {
            int p = org.framework.net.shared.NumeroAscii.parseIntEstrito(raw == null ? "" : raw.trim());
            if (p < 0 || p > 65535) {
                throw new SegurancaException("Porta fora do intervalo (0–65535).");
            }
            return p;
        } catch (NumberFormatException e) {
            throw new SegurancaException("Porta inválida (informe um número).");
        }
    }

    /**
     * VLAN de um equipamento a partir do atributo textual do montador.
     *
     * <p><b>Propósito de negócio:</b> traduzir {@code vlan=<n>} para o domínio,
     * distinguindo ausência (equipamento sem VLAN) de erro de digitação.</p>
     *
     * <p><b>Invariantes do domínio:</b> atributo ausente ({@code null}) vira
     * {@code 0} (sem VLAN); presente precisa ser inteiro em 1–4094.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> valor não numérico ou fora da
     * faixa lança {@link SegurancaException} (HTTP 400) citando linha e campo —
     * nunca coage em silêncio para {@code 0}.</p>
     */
    private static int vlanAtributo(String v, int linha) {
        if (v == null) {
            return 0;
        }
        int n = inteiroExigido(v, linha, "vlan");
        if (n < 1 || n > 4094) {
            throw new SegurancaException("Linha " + linha + ": vlan fora do intervalo 1–4094 ('" + v.trim() + "').");
        }
        return n;
    }

    /**
     * Porta aberta de um servidor a partir do atributo textual.
     *
     * <p><b>Propósito de negócio:</b> traduzir {@code porta=<n>} para o domínio,
     * distinguindo ausência de erro de digitação.</p>
     *
     * <p><b>Invariantes do domínio:</b> ausente vira {@code 0} (sem porta
     * declarada); presente precisa ser inteiro em 1–65535.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> valor não numérico ou fora da
     * faixa lança {@link SegurancaException} (HTTP 400) citando linha e campo.</p>
     */
    private static int portaAtributo(String v, int linha) {
        if (v == null) {
            return 0;
        }
        int n = inteiroExigido(v, linha, "porta");
        if (n < 1 || n > 65535) {
            throw new SegurancaException("Linha " + linha + ": porta fora do intervalo 1–65535 ('" + v.trim() + "').");
        }
        return n;
    }

    /**
     * Lista de VLANs roteadas por um switch L3 (os SVIs) a partir do atributo.
     *
     * <p><b>Propósito de negócio:</b> traduzir {@code vlans=<a,b,...>} para o
     * domínio de roteamento inter-VLAN.</p>
     *
     * <p><b>Invariantes do domínio:</b> ausente/vazio vira lista vazia (nenhum
     * SVI); cada token não vazio precisa ser VLAN válida em 1–4094.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> qualquer token não numérico ou
     * fora da faixa lança {@link SegurancaException} — não descarta o token em
     * silêncio, para uma lista parcialmente inválida não virar diagnóstico falso.</p>
     */
    private static List<Integer> vlansAtributo(String v, int linha) {
        List<Integer> out = new ArrayList<>();
        if (v == null || v.isBlank()) {
            return out;
        }
        for (String p : v.split(",")) {
            if (p.isBlank()) {
                continue;
            }
            int n = inteiroExigido(p, linha, "vlans");
            if (n < 1 || n > 4094) {
                throw new SegurancaException("Linha " + linha + ": vlan fora do intervalo 1–4094 na lista ('" + p.trim() + "').");
            }
            out.add(n);
        }
        return out;
    }

    private static int inteiroExigido(String v, int linha, String campo) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new SegurancaException("Linha " + linha + ": " + campo + " precisa ser um número ('" + v.trim() + "').");
        }
    }

    /**
     * Regras deny de um firewall a partir do atributo textual do montador.
     *
     * <p><b>Propósito de negócio:</b> traduzir {@code deny=<proto>/<porta>[,...]}
     * para o domínio de bloqueio do firewall.</p>
     *
     * <p><b>Invariantes do domínio:</b> ausente/vazio vira lista vazia (firewall
     * não bloqueia nada); cada regra precisa ser {@code tcp/<porta>} ou
     * {@code udp/<porta>} com porta em 1–65535.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> regra malformada lança
     * {@link SegurancaException} — um deny mal digitado nunca vira "sem bloqueio".</p>
     */
    private static List<String> denyAtributo(String v, int linha) {
        List<String> out = new ArrayList<>();
        if (v == null || v.isBlank()) {
            return out;
        }
        for (String bruto : v.split(",")) {
            String r = bruto.trim().toLowerCase(Locale.ROOT);
            if (r.isEmpty()) {
                continue;
            }
            if (!r.matches("(tcp|udp)/\\d{1,5}")) {
                throw new SegurancaException("Linha " + linha + ": regra deny inválida '" + bruto.trim()
                        + "' (use tcp/<porta> ou udp/<porta>).");
            }
            int porta = Integer.parseInt(r.substring(r.indexOf('/') + 1));
            if (porta < 1 || porta > 65535) {
                throw new SegurancaException("Linha " + linha
                        + ": porta da regra deny fora de 1–65535 ('" + bruto.trim() + "').");
            }
            out.add(r);
        }
        return out;
    }
}
