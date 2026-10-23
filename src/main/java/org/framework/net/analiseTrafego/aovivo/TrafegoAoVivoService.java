package org.framework.net.analiseTrafego.aovivo;

import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.analiseTrafego.aovivo.SnapshotAoVivo.DispositivoBluetooth;
import org.framework.net.analiseTrafego.aovivo.SnapshotAoVivo.PacoteResumo;
import org.framework.net.analiseTrafego.aovivo.SnapshotAoVivo.PontoTempo;
import org.framework.net.analiseTrafego.aovivo.SnapshotAoVivo.RedeWifi;
import org.framework.net.analiseTrafego.aovivo.SnapshotAoVivo.TopHost;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Estado do tráfego ao vivo em <b>modo demo</b>: uma simulação didática evoluída no servidor
 * (VPS-safe), que reproduz como um analisador de tráfego apresenta protocolos, throughput,
 * top hosts e redes Wi-Fi/Bluetooth — sem depender de captura real na máquina do usuário.
 */
@ApplicationScoped
public class TrafegoAoVivoService {

    private static final DateTimeFormatter HMS =
            org.framework.net.shared.FusoDoSite.formato("HH:mm:ss");
    private static final int MAX_SERIE = 30;
    private static final int MAX_PACOTES = 25;
    private static final String[] PROTOS = {"TCP", "UDP", "TLS", "DNS", "ICMP", "ARP"};
    private static final int[] PESOS = {55, 18, 12, 8, 4, 3};

    private final ReentrantLock lock = new ReentrantLock();

    // ---- estado do modo demo ----
    private final AtomicLong demoTotal = new AtomicLong();
    private final Deque<PontoTempo> demoSerie = new ArrayDeque<>();
    private final Deque<PacoteResumo> demoPacotes = new ArrayDeque<>();
    private final Map<String, Long> demoProto = new LinkedHashMap<>();
    private final Map<String, Long> demoHosts = new LinkedHashMap<>();
    private long demoBytesTick;

    /** Avança um "tick" da simulação e devolve o snapshot demo. */
    public SnapshotAoVivo snapshotDemo() {
        lock.lock();
        try {
            int novos = ThreadLocalRandom.current().nextInt(8, 45);
            demoBytesTick = 0;
            for (int i = 0; i < novos; i++) {
                gerarPacoteDemo();
            }
            demoSerie.addLast(new PontoTempo(HMS.format(Instant.now()), novos));
            while (demoSerie.size() > MAX_SERIE) {
                demoSerie.removeFirst();
            }
            double kbps = demoBytesTick * 8.0 / 1000.0;
            List<RedeWifi> wifi = wifiDemo();
            return new SnapshotAoVivo(
                    "demo", true, demoTotal.get(), novos, arredondar(kbps),
                    new LinkedHashMap<>(demoProto),
                    new ArrayList<>(demoSerie),
                    topHosts(demoHosts),
                    new ArrayList<>(demoPacotes),
                    wifi, bluetoothDemo(),
                    (int) wifi.stream().filter(RedeWifi::aberta).count(),
                    HMS.format(Instant.now()));
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------ demo helpers

    /**
     * Gera um pacote da simulação.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO:</b> o painel didático tem de parecer um analisador de verdade.</p>
     *
     * <p><b>INVARIANTES DO DOMÍNIO:</b> cada pacote tem número próprio e crescente (antes todos os do mesmo
     * tick saíam com o mesmo número); ARP só existe dentro da LAN, nunca com IP público; Client Hello sai do
     * cliente e Server Hello do servidor; consulta DNS sai e resposta volta (auditoria CALC-38, CONT-35).</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; chamado sob o lock do snapshot.</p>
     */
    private void gerarPacoteDemo() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String proto = protoAleatorio();
        boolean saindo = r.nextBoolean();
        String local = "192.168.0." + r.nextInt(2, 40);
        String remoto = "ARP".equals(proto) ? "192.168.0.1" : ipRemoto(proto);
        String origem = saindo ? local : remoto;
        String destino = saindo ? remoto : local;
        Integer sp = portaPara(proto, saindo, true);
        Integer dp = portaPara(proto, saindo, false);
        int tam = tamanhoPara(proto);
        demoBytesTick += tam;
        demoProto.merge(proto, 1L, Long::sum);
        if (!"ARP".equals(proto)) {
            demoHosts.merge(remoto, 1L, Long::sum);
        }
        String info = "ARP".equals(proto)
                ? "Who has " + destino + "? Tell " + origem
                : infoPara(proto, dp, saindo);
        demoPacotes.addFirst(new PacoteResumo(demoTotal.incrementAndGet(), HMS.format(Instant.now()),
                proto, origem, destino, sp, dp, tam, info));
        while (demoPacotes.size() > MAX_PACOTES) {
            demoPacotes.removeLast();
        }
    }

    private static String protoAleatorio() {
        int soma = 0;
        for (int p : PESOS) {
            soma += p;
        }
        int x = ThreadLocalRandom.current().nextInt(soma);
        int acc = 0;
        for (int i = 0; i < PROTOS.length; i++) {
            acc += PESOS[i];
            if (x < acc) {
                return PROTOS[i];
            }
        }
        return "TCP";
    }

    private static String ipRemoto(String proto) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String[] conhecidos = {"142.250.79.14", "20.190.159.0", "104.16.85.20", "8.8.8.8",
                "13.107.42.14", "151.101.1.140", "35.190.247.1"};
        return conhecidos[r.nextInt(conhecidos.length)];
    }

    private static Integer portaPara(String proto, boolean saindo, boolean origem) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int efemera = r.nextInt(49152, 65535);
        int servico = switch (proto) {
            case "TLS" -> 443;
            case "DNS" -> 53;
            case "UDP" -> r.nextBoolean() ? 443 : 123;
            case "TCP" -> r.nextBoolean() ? 80 : 443;
            default -> -1;
        };
        if (proto.equals("ICMP") || proto.equals("ARP")) {
            return null;
        }
        boolean ladoServico = saindo == !origem;
        return ladoServico ? servico : efemera;
    }

    private static int tamanhoPara(String proto) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return switch (proto) {
            case "ARP" -> 60;
            case "ICMP" -> 98;
            case "DNS" -> r.nextInt(70, 180);
            default -> r.nextInt(60, 1460);
        };
    }

    private static String infoPara(String proto, Integer dp, boolean saindo) {
        return switch (proto) {
            case "TLS" -> ThreadLocalRandom.current().nextBoolean() ? "Application Data"
                    : (saindo ? "Client Hello" : "Server Hello");
            case "DNS" -> saindo ? "Standard query A" : "Standard query response A";
            case "ARP" -> "Who has ...? Tell ...";
            case "ICMP" -> saindo ? "Echo (ping) request" : "Echo (ping) reply";
            case "TCP" -> dp != null && dp == 80 ? "GET / HTTP/1.1" : "ACK";
            default -> "";
        };
    }

    private static List<RedeWifi> wifiDemo() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return List.of(
                new RedeWifi("CAFE_FREE", "a4:2b:8c:11:22:33", "Aberta", -55 + r.nextInt(-5, 6), true),
                new RedeWifi("AEROPORTO-WIFI", "de:ad:be:ef:00:01", "Aberta", -70 + r.nextInt(-5, 6), true),
                new RedeWifi("MinhaCasa_5G", "88:99:aa:bb:cc:dd", "WPA2-PSK", -42 + r.nextInt(-4, 5), false),
                new RedeWifi("NET_2G4", "10:20:30:40:50:60", "WPA2-PSK", -60 + r.nextInt(-5, 6), false),
                new RedeWifi("Vizinho6", "aa:bb:cc:dd:ee:ff", "WPA3-SAE", -75 + r.nextInt(-5, 6), false),
                // WEP cifra (mal): não é rede aberta — o risco é a cifra quebrável, mostrado à parte.
                new RedeWifi("IoT_Camera", "12:34:56:78:9a:bc", "WEP", -66 + r.nextInt(-5, 6), false));
    }

    private static List<DispositivoBluetooth> bluetoothDemo() {
        return List.of(
                new DispositivoBluetooth("Fone JBL Tune", "00:1A:7D:DA:71:11", "Áudio", true),
                new DispositivoBluetooth("Mouse Logitech", "F0:9E:4A:12:34:56", "Periférico", true),
                new DispositivoBluetooth("Galaxy Buds", "C8:3D:D4:AB:CD:EF", "Áudio", false),
                new DispositivoBluetooth("Smart TV Samsung", "5C:49:7D:00:11:22", "Descoberto", false));
    }

    // ------------------------------------------------------------------ shared helpers

    private List<TopHost> topHosts(Map<String, Long> hosts) {
        return hosts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(6)
                .map(e -> new TopHost(e.getKey(), e.getValue()))
                .toList();
    }

    private static double arredondar(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
