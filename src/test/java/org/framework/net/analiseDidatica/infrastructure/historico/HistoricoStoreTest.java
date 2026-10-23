package org.framework.net.analiseDidatica.infrastructure.historico;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.config.AnaliseDidaticaConfig;
import org.framework.net.telemetria.TelemetriaLogger;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invariantes do histórico (auditoria F06): o boot nunca aborta por arquivo ilegível (falha ABERTA
 * para dado didático, com quarentena); a gravação concorrente não corrompe o arquivo; leitura nunca
 * muta o que está guardado. Cada teste usa um diretório temporário próprio — nunca o
 * ~/.framework-net real da máquina.
 */
@QuarkusTest
class HistoricoStoreTest {

    @Inject
    AnaliseDidaticaConfig config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TelemetriaLogger telemetriaLogger;

    private HistoricoStore novoStore(Path home) {
        HistoricoStore s = new HistoricoStore();
        s.config = config;
        s.objectMapper = objectMapper;
        s.telemetriaLogger = telemetriaLogger;
        s.userHome = home.toString();
        return s;
    }

    private static Path arquivo(Path home) {
        return home.resolve(".framework-net").resolve("consulta_history.v2.json");
    }

    @Test
    void arquivoCorrompidoNaoDerrubaOBootEVaiParaQuarentena() throws Exception {
        Path home = Files.createTempDirectory("hist-corrompido");
        Files.createDirectories(arquivo(home).getParent());
        Files.writeString(arquivo(home), "[{\"id\":\"abc\",\"modo\":\"ip\"}, {\"id\":");  // truncado no meio

        HistoricoStore s = novoStore(home);
        assertDoesNotThrow(s::carregar);
        assertEquals(0, s.listarTodos().size());
        assertFalse(Files.exists(arquivo(home)), "o arquivo ilegível deveria ter saído do caminho");
        try (var ls = Files.list(arquivo(home).getParent())) {
            assertTrue(ls.anyMatch(p -> p.getFileName().toString().startsWith("consulta_history.v2.json.corrompido-")),
                    "o conteúdo ilegível precisa ficar preservado em quarentena");
        }

        // Controle positivo (A1): arquivo legítimo continua sendo carregado.
        Path home2 = Files.createTempDirectory("hist-ok");
        Files.createDirectories(arquivo(home2).getParent());
        Files.writeString(arquivo(home2), "[{\"id\":\"abc\",\"modo\":\"ip\",\"sessao\":\"s1\"}]");
        HistoricoStore ok = novoStore(home2);
        ok.carregar();
        assertEquals(1, ok.listar("s1").size());
    }

    @Test
    void paginarNaoMutaOsRegistrosGuardados() throws Exception {
        HistoricoStore s = novoStore(Files.createTempDirectory("hist-paginar"));
        s.registrarConsulta("s1", Map.of("modo", "ip", "ip", "10.0.0.1"), Map.of("rede", "10.0.0.0"));
        s.paginar("s1", "10", "1");
        assertFalse(s.listar("s1").get(0).containsKey("timestamp_utc"),
                "paginar escreveu no mapa guardado (compartilhado entre requisições)");
    }

    @Test
    void gravacaoConcorrenteNaoCorrompeOArquivo() throws Exception {
        Path home = Files.createTempDirectory("hist-concorrente");
        HistoricoStore s = novoStore(home);
        int threads = 8;
        int porThread = 40;
        ExecutorService pool = Executors.newFixedThreadPool(threads + 2);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> tarefas = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int id = t;
            tarefas.add(pool.submit(() -> {
                largada.await();
                for (int i = 0; i < porThread; i++) {
                    s.registrarConsulta("t" + id, Map.of("modo", "ip", "ip", "10.0." + id + "." + i), Map.of("rede", "x"));
                }
                return null;
            }));
        }
        for (int t = 0; t < 2; t++) {
            tarefas.add(pool.submit(() -> {
                largada.await();
                for (int i = 0; i < 200; i++) {
                    s.paginar("t0", "60", "1");
                    objectMapper.writeValueAsString(s.listarTodos());
                }
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> f : tarefas) {
            f.get(60, TimeUnit.SECONDS);   // qualquer exceção (ConcurrentModification etc.) reprova aqui
        }
        pool.shutdown();
        s.gravarPendente();

        List<Map<String, Object>> noDisco = objectMapper.readValue(arquivo(home).toFile(), new TypeReference<>() {});
        // 8 sessões x 40 consultas: cada sessão cabe no teto por sessão, e o total no teto do arquivo.
        assertEquals(Math.min(HistoricoStore.MAX_TOTAL, threads * Math.min(porThread, config.maxHistory())),
                noDisco.size());
        assertEquals(noDisco.size(), s.listarTodos().size());
    }

    /**
     * BACK-14: 500+ consultas avulsas (um POST sem cookie = sessão nova) não apagam o histórico de quem tem
     * histórico de verdade; o teto global continua valendo e quem sai são as consultas avulsas mais antigas.
     */
    @Test
    void consultasAvulsasNaoExpulsamQuemTemHistorico() throws Exception {
        HistoricoStore s = novoStore(Files.createTempDirectory("hist-despejo"));
        s.registrarConsulta("dono", Map.of("modo", "ip", "ip", "10.9.9.1"), Map.of("rede", "x"));
        s.registrarConsulta("dono", Map.of("modo", "ip", "ip", "10.9.9.2"), Map.of("rede", "x"));
        for (int i = 0; i < HistoricoStore.MAX_TOTAL + 50; i++) {
            s.registrarConsulta("avulsa-" + i, Map.of("modo", "ip", "ip", "10.8." + (i / 250) + "." + (i % 250)),
                    Map.of("rede", "x"));
        }
        assertEquals(HistoricoStore.MAX_TOTAL, s.listarTodos().size());
        assertEquals(2, s.listar("dono").size(), "o histórico de quem consultou duas vezes não pode ser expulso");
        assertEquals(0, s.listar("avulsa-0").size(), "a consulta avulsa mais antiga é a que sai");
        assertEquals(1, s.listar("avulsa-" + (HistoricoStore.MAX_TOTAL + 49)).size());
    }

    /**
     * Fronteira achada na revisão adversarial da correção acima: com o arquivo cheio de sessões de 2+ consultas, o
     * visitante novo tem de GUARDAR as próprias consultas (a 1ª versão apagava a consulta nova na hora).
     */
    @Test
    void arquivoSaturadoNaoCongelaQuemChega() throws Exception {
        HistoricoStore s = novoStore(Files.createTempDirectory("hist-saturado"));
        for (int i = 0; i < HistoricoStore.MAX_TOTAL / 2; i++) {
            for (int j = 0; j < 2; j++) {
                s.registrarConsulta("antiga-" + i, Map.of("modo", "ip", "ip", "10.7." + (i % 250) + "." + j), Map.of("rede", "x"));
            }
        }
        assertEquals(HistoricoStore.MAX_TOTAL, s.listarTodos().size());
        for (int k = 0; k < 10; k++) {
            s.registrarConsulta("nova", Map.of("modo", "ip", "ip", "10.6.6." + k), Map.of("rede", "x"));
        }
        assertEquals(10, s.listar("nova").size(), "o visitante novo precisa guardar as próprias consultas");
        assertEquals(HistoricoStore.MAX_TOTAL, s.listarTodos().size());
        // e quem saiu foi a sessão parada há mais tempo, não a mais recente das antigas
        assertEquals(2, s.listar("antiga-" + (HistoricoStore.MAX_TOTAL / 2 - 1)).size());
    }

    /** OPS-03: consultas em série saem numa gravação só, e nada se perde ao gravar. */
    @Test
    void consultasEmSerieSaemNumaGravacaoSo() throws Exception {
        Path home = Files.createTempDirectory("hist-lote");
        HistoricoStore s = novoStore(home);
        s.atrasoGravacaoMs = 400;
        for (int i = 0; i < 20; i++) {
            s.registrarConsulta("L", Map.of("modo", "ip", "ip", "10.9.0." + i), Map.of("rede", "x"));
        }
        long limite = System.currentTimeMillis() + 5_000;
        while (!Files.exists(arquivo(home)) && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
        }
        Thread.sleep(200);
        assertTrue(s.gravacoes.get() <= 2, "20 consultas gravaram o arquivo " + s.gravacoes.get() + " vezes");
        List<Map<String, Object>> noDisco = objectMapper.readValue(arquivo(home).toFile(), new TypeReference<>() {});
        assertEquals(Math.min(20, config.maxHistory()), noDisco.size(), "a gravação em lote leva todas as consultas");
    }

    /** F07: cada sessão vê só o seu; teto por sessão não apaga o de outra; o legado global não volta. */
    @Test
    void sessoesSaoIsoladasComTetoProprio() throws Exception {
        HistoricoStore s = novoStore(Files.createTempDirectory("hist-sessao"));
        for (int i = 0; i < config.maxHistory() + 5; i++) {
            s.registrarConsulta("A", Map.of("modo", "ip", "ip", "10.0.0." + i), Map.of("rede", "x"));
        }
        s.registrarConsulta("B", Map.of("modo", "ip", "ip", "192.168.0.1"), Map.of("rede", "y"));
        assertEquals(config.maxHistory(), s.listar("A").size());
        assertEquals(1, s.listar("B").size());
        assertEquals("192.168.0.1", s.listar("B").get(0).get("ip_entrada"));
        assertTrue(s.listar("A").stream().noneMatch(r -> "192.168.0.1".equals(r.get("ip_entrada"))));
        assertFalse(s.listar("A").get(0).containsKey("sessao"), "a chave de sessão não sai para o navegador");
        assertEquals(0, s.listar().size(), "fora de requisição não há sessão: leitura vazia (falha fechada)");
    }

    @Test
    void registrosLegadosSemSessaoSaoDescartadosNaCarga() throws Exception {
        Path home = Files.createTempDirectory("hist-legado");
        Files.createDirectories(arquivo(home).getParent());
        Files.writeString(arquivo(home), "[{\"id\":\"old\",\"ip_entrada\":\"203.0.113.9\"},"
                + "{\"id\":\"new\",\"sessao\":\"S\",\"ip_entrada\":\"10.0.0.1\"}]");
        HistoricoStore s = novoStore(home);
        s.carregar();
        assertEquals(1, s.listarTodos().size());
        assertEquals("new", s.listarTodos().get(0).get("id"));
    }

    @Test
    void arquivoDaVersaoAnteriorNaoRecebeHistoricoPorSessao() throws Exception {
        // Revisão operacional: a versão anterior ao F07 lê consulta_history.json e o publica inteiro no
        // GET /history. Num rollback, ela não pode encontrar ali o histórico de cada sessão.
        Path home = Files.createTempDirectory("hist-rollback");
        Path antigo = home.resolve(".framework-net").resolve("consulta_history.json");
        Files.createDirectories(antigo.getParent());
        String conteudoAntigo = "[{\"id\":\"pre\",\"modo\":\"ip\",\"ip_entrada\":\"198.51.100.1\"}]";
        Files.writeString(antigo, conteudoAntigo);

        HistoricoStore s = novoStore(home);
        s.carregar();
        s.registrarConsulta("S", Map.of("modo", "ip", "ip", "203.0.113.50"), Map.of("rede", "x"));
        s.gravarPendente();

        assertEquals(conteudoAntigo, Files.readString(antigo), "o arquivo lido pela versão anterior foi alterado");
        assertTrue(Files.readString(arquivo(home)).contains("203.0.113.50"), "o registro novo tem de ir para o v2");
        assertEquals(1, s.listar("S").size());
    }

    /**
     * A pasta-base nunca é uma expressão por expandir: em 02/10/2026 o padrão {@code "${user.home}"} da
     * anotação não foi expandido e o quarkusDev gravou o histórico numa pasta com esse nome literal.
     */
    @Test
    void pastaBaseNuncaEhExpressaoPorExpandir() {
        HistoricoStore s = new HistoricoStore();
        assertEquals(System.getProperty("user.home"), s.baseDoHistorico(), "sem propriedade vale a pasta do usuário");
        s.homeConfigurado = java.util.Optional.of("/dados/historico");
        assertEquals("/dados/historico", s.baseDoHistorico());
        s.homeConfigurado = java.util.Optional.of("${user.home}");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, s::baseDoHistorico,
                "expressão por expandir não pode virar nome de pasta");
    }
}
