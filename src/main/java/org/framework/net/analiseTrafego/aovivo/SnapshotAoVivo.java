package org.framework.net.analiseTrafego.aovivo;

import java.util.List;
import java.util.Map;

/**
 * Snapshot do tráfego ao vivo (consumido pelo dashboard via polling).
 *
 * <p>Alimentado pelo modo <b>demo</b>: uma simulação didática evoluída no servidor (VPS-safe).
 */
public record SnapshotAoVivo(
        String modo,                       // "demo"
        boolean agenteConectado,           // sempre true no modo demo
        long totalPacotes,
        long pacotesPorSegundo,
        double throughputKbps,
        Map<String, Long> porProtocolo,    // TCP, UDP, TLS, DNS, ICMP, ARP...
        List<PontoTempo> serie,            // últimos N segundos (I/O graph)
        List<TopHost> topHosts,
        List<PacoteResumo> ultimosPacotes,
        List<RedeWifi> wifi,
        List<DispositivoBluetooth> bluetooth,
        int redesAbertas,
        String atualizadoEm) {

    /** Um ponto da série temporal (pacotes por segundo). */
    public record PontoTempo(String t, long valor) {
    }

    /** Host que mais aparece no tráfego. */
    public record TopHost(String host, long pacotes) {
    }

    /** Linha resumida de um pacote para a tabela ao vivo. */
    public record PacoteResumo(
            long seq, String timestamp, String protocolo,
            String origem, String destino,
            Integer portaOrigem, Integer portaDestino,
            int tamanho, String info) {
    }

    /** Rede Wi-Fi detectada (aberta = sem criptografia = insegura). */
    public record RedeWifi(String ssid, String bssid, String seguranca, int sinal, boolean aberta) {
    }

    /** Dispositivo Bluetooth visível/pareado. */
    public record DispositivoBluetooth(String nome, String endereco, String tipo, boolean pareado) {
    }
}
