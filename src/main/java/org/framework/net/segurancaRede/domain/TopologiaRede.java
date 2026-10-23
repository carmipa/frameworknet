package org.framework.net.segurancaRede.domain;

import java.util.List;

/**
 * Topologia montada pelo usuário para o diagnóstico de fluxo (P05 fase 2).
 *
 * <p><b>Propósito de negócio:</b> representar os equipamentos e enlaces que o
 * usuário descreveu, para que o motor de alcançabilidade responda se um fluxo
 * origem→destino chega e onde trava. É a estrutura por trás do montador — a
 * mesma resposta {@link DiagnosticoFluxo}, agora sobre uma topologia arbitrária
 * em vez de um cenário fixo.</p>
 *
 * <p><b>Invariantes do domínio:</b> ids de dispositivo são únicos e um enlace só
 * liga ids existentes (a validação vive no avaliador); host tem VLAN e gateway,
 * switch L3 declara as VLANs que roteia, firewall declara regras de deny e
 * servidor declara a porta aberta. Nenhuma lista é nula.</p>
 */
public record TopologiaRede(List<Dispositivo> dispositivos, List<Enlace> enlaces) {

    public TopologiaRede {
        dispositivos = dispositivos == null ? List.of() : List.copyOf(dispositivos);
        enlaces = enlaces == null ? List.of() : List.copyOf(enlaces);
    }

    public Dispositivo por(String id) {
        return dispositivos.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
    }

    /**
     * Um equipamento.
     *
     * <p><b>Invariantes:</b> {@code tipo} ∈ {host, switchl3, firewall, server}.
     * {@code vlan} vale para host/server; {@code gateway} (id de outro nó) para
     * host; {@code vlansRoteadas} para switch L3; {@code denyRegras} (ex.:
     * "tcp/23") para firewall; {@code portaAberta} para server.</p>
     */
    public record Dispositivo(
            String id,
            String tipo,
            int vlan,
            String gateway,
            List<Integer> vlansRoteadas,
            List<String> denyRegras,
            int portaAberta) {

        public Dispositivo {
            vlansRoteadas = vlansRoteadas == null ? List.of() : List.copyOf(vlansRoteadas);
            denyRegras = denyRegras == null ? List.of() : List.copyOf(denyRegras);
        }

        public boolean ehTipo(String t) {
            return t.equals(tipo);
        }
    }

    /** Enlace bidirecional entre dois dispositivos (por id). */
    public record Enlace(String a, String b) {

        public boolean liga(String x, String y) {
            return (a.equals(x) && b.equals(y)) || (a.equals(y) && b.equals(x));
        }
    }
}
