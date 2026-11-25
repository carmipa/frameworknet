package org.framework.net.telemetria;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TelemetriaLoggerTest {

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    TelemetriaStore store;

    /**
     * Propósito de negócio: comprova o registro e a exportação canônica de um evento funcional.
     * Invariantes do domínio: a validação não depende do crescimento da contagem quando a janela já atingiu
     * o limite; o evento recém-criado deve estar presente entre os mais recentes.
     * Comportamento em caso de falha: o teste falha se o evento não entrar na janela ou se o OTLP não existir.
     */
    @Test
    void registraEventoEPersisteArquivoCompartilhado() throws Exception {
        telemetriaLogger.logEvent("info", "teste", "evento_unitario",
                Map.of("campo", "valor", "status", "ok"));
        store.flush();

        TelemetriaResumo resumo = store.gerarResumo(500);
        assertTrue(resumo.ultimosEventos().stream()
                .anyMatch(evento -> "teste".equals(evento.modulo())
                        && "evento_unitario".equals(evento.evento())));
        assertTrue(Files.exists(store.arquivoCompartilhado()));
        assertTrue(resumo.contagemPorModulo().containsKey("teste"));
    }

    /**
     * Lote 1 da auditoria (OPS-03 e SEC-06): sem teto por evento, um campo de 1 MB por pedido esgotava a memória em
     * ~156 pedidos e trocava a trilha durável por lixo; com quebra de linha crua, forjava linha no console. A2: o
     * campo de 1 MB sai no teto; a quebra de linha sai neutralizada. A1: número continua número e o valor curto
     * legítimo passa igual — o painel depende do tipo.
     */
    @Test
    void campoDoEventoTemTetoENaoForjaLinha() {
        // Marca única: a janela também traz eventos carregados do arquivo de uma execução anterior (com o número já
        // relido como Long); o teste olha só o evento que ele mesmo criou.
        String marca = java.util.UUID.randomUUID().toString();
        java.util.Map<String, Object> campos = new java.util.LinkedHashMap<>();
        campos.put("dominio", "q".repeat(1_000_000));
        campos.put("forjado", "a\nINFO login ok");
        campos.put("total", 42);
        campos.put("curto", "exemplo.com");
        campos.put("marca", marca);
        telemetriaLogger.logEvent("info", "teste-teto", "evento_teto", campos);

        TelemetriaEvent ev = store.snapshotEventos().stream()
                .filter(e -> "evento_teto".equals(e.evento()) && e.fields() != null && marca.equals(e.fields().get("marca")))
                .findFirst()
                .orElseThrow();
        String dominio = String.valueOf(ev.fields().get("dominio"));
        assertTrue(dominio.length() <= TelemetriaLogger.MAX_VALOR_CAMPO + TelemetriaContext.MARCA_TRUNCADO.length(),
                "campo do evento com " + dominio.length() + " caracteres");
        assertTrue(dominio.endsWith(TelemetriaContext.MARCA_TRUNCADO));
        org.junit.jupiter.api.Assertions.assertEquals("a%0AINFO login ok", ev.fields().get("forjado"));
        org.junit.jupiter.api.Assertions.assertEquals(42, ev.fields().get("total"));
        org.junit.jupiter.api.Assertions.assertEquals("exemplo.com", ev.fields().get("curto"));
        assertTrue(ev.message().length() <= TelemetriaLogger.MAX_MENSAGEM + TelemetriaContext.MARCA_TRUNCADO.length(),
                "mensagem do evento com " + ev.message().length() + " caracteres");
        assertTrue(!ev.message().contains("\n"), "a mensagem do evento forja linha");
        // Revisão de boa-fé (2ª passada): a mensagem não é saneada de novo — o escape aparece UMA vez (%0A), não %250A.
        assertTrue(ev.message().contains("forjado=a%0AINFO login ok"), ev.message());
        assertTrue(!ev.message().contains("%250A"), "a mensagem escapou duas vezes: " + ev.message());
    }

    /**
     * Revisão operacional (2ª passada): a cópia saneada não pode perder diagnóstico — as suprimidas (a falha do
     * {@code close()} de um try-with-resources) vêm junto, e numa cadeia funda a causa RAIZ fica, com a contagem das
     * intermediárias omitidas.
     */
    @Test
    void copiaSaneadaGuardaSuprimidasECausaRaiz() {
        RuntimeException raiz = new IllegalStateException("raiz\nfunda");
        Throwable cadeia = raiz;
        for (int i = 0; i < 9; i++) {
            cadeia = new RuntimeException("nivel " + i, cadeia);
        }
        RuntimeException topo = new RuntimeException("topo", cadeia);
        topo.addSuppressed(new java.io.IOException("close\nfalhou"));

        Throwable copia = TelemetriaLogger.comMensagensSaneadas(topo);
        assertTrue(copia.getSuppressed().length == 1
                && copia.getSuppressed()[0].getMessage().startsWith("java.io.IOException: close%0Afalhou"));
        Throwable ultimo = copia;
        while (ultimo.getCause() != null) {
            ultimo = ultimo.getCause();
        }
        assertTrue(ultimo.getMessage().contains("java.lang.IllegalStateException: raiz%0Afunda"), ultimo.getMessage());
        assertTrue(ultimo.getMessage().contains("omitida"), "a causa raiz não diz que houve intermediárias omitidas: "
                + ultimo.getMessage());
    }

    /**
     * Revisão adversarial: o console imprime a exceção inteira (formato com {@code %e}), e a mensagem dela costuma trazer
     * o que a pessoa digitou — crua, forjava linha no {@code docker logs} e, com 256 KB por pedido, girava os arquivos
     * de log em segundos. A cópia que vai ao log tem a mensagem saneada e a MESMA pilha (A1: o diagnóstico continua).
     */
    @Test
    void excecaoVaiAoLogComMensagemSaneadaEAMesmaPilha() {
        IllegalArgumentException original = new IllegalArgumentException("x\nINFO login ok " + "q".repeat(10_000),
                new RuntimeException("causa\r\nforjada"));
        Throwable copia = TelemetriaLogger.comMensagensSaneadas(original);

        org.junit.jupiter.api.Assertions.assertArrayEquals(original.getStackTrace(), copia.getStackTrace());
        assertTrue(copia.getMessage().startsWith("java.lang.IllegalArgumentException: x%0AINFO login ok"), copia.getMessage());
        assertTrue(copia.getMessage().length() <= "java.lang.IllegalArgumentException: ".length()
                + TelemetriaLogger.MAX_MENSAGEM_EXCECAO + TelemetriaContext.MARCA_TRUNCADO.length());
        assertTrue(copia.getCause().getMessage().startsWith("java.lang.RuntimeException: causa%0D%0Aforjada"));

        java.io.StringWriter impresso = new java.io.StringWriter();
        copia.printStackTrace(new java.io.PrintWriter(impresso));
        assertTrue(impresso.toString().lines().noneMatch(l -> l.startsWith("INFO login ok")),
                "a pilha impressa ainda forja uma linha começando com o texto digitado");
        org.junit.jupiter.api.Assertions.assertNull(TelemetriaLogger.comMensagensSaneadas(null));
    }

    /**
     * Escopo sem eventos info (lote do pacote da turma): dentro dele, {@code info} não é gravado e {@code warn} é; fora,
     * {@code info} volta a ser gravado; exceção dentro do escopo não o deixa ligado; e outra thread, no mesmo instante,
     * grava normalmente. A2: sem o escopo, o evento info de dentro aparece na janela.
     */
    @Test
    void escopoSemEventosInfoCalaSoInfoESoNaThreadQueChamou() throws Exception {
        String marca = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String deOutraThread = TelemetriaLogger.semEventosInfo(() -> {
            telemetriaLogger.logEvent("info", "teste", "dentro_info_" + marca, Map.of("n", 1));
            telemetriaLogger.logEvent("warn", "teste", "dentro_warn_" + marca, Map.of("n", 2));
            Thread outra = new Thread(() ->
                    telemetriaLogger.logEvent("info", "teste", "outra_thread_" + marca, Map.of("n", 3)));
            outra.start();
            try {
                outra.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "ok";
        });
        org.junit.jupiter.api.Assertions.assertEquals("ok", deOutraThread);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> TelemetriaLogger.semEventosInfo(() -> {
            throw new IllegalStateException("falha no meio do lote");
        }));
        telemetriaLogger.logEvent("info", "teste", "depois_info_" + marca, Map.of("n", 4));

        java.util.Set<String> gravados = new java.util.HashSet<>();
        store.snapshotEventos().forEach(e -> gravados.add(e.evento()));
        org.junit.jupiter.api.Assertions.assertFalse(gravados.contains("dentro_info_" + marca), "info dentro do escopo foi gravado");
        assertTrue(gravados.contains("dentro_warn_" + marca), "warn dentro do escopo sumiu");
        assertTrue(gravados.contains("outra_thread_" + marca), "o escopo calou outra thread");
        assertTrue(gravados.contains("depois_info_" + marca), "o escopo ficou ligado depois de sair (ou da exceção)");
    }

    /**
     * D27: o sobrecarregado de 4 argumentos gravava "ok" mesmo quando o mapa dizia outro status (38 chamadas), e o
     * dataset público mostrava timeout e erro de DNS, erro do IPv6 e recusa de CSRF como sucesso.
     * A3: o esperado é o status que a chamada escreveu. A6: conferido no evento guardado e na mensagem.
     * A1: sem "status" no mapa o evento continua "ok", como antes.
     */
    @Test
    void statusDoEventoVemDoCampoStatus() {
        String marca = "d27-" + java.util.UUID.randomUUID();
        telemetriaLogger.logEvent("warn", "teste", "evento_d27", Map.of("status", "erro", "marca", marca));
        telemetriaLogger.logEvent("info", "teste", "evento_d27", Map.of("marca", marca + "-sem"));

        TelemetriaEvent comStatus = doStore(marca);
        assertEquals("erro", comStatus.status(), "o evento saiu com status diferente do que a chamada escreveu");
        assertTrue(comStatus.message().startsWith("evento=evento_d27 status=erro"), comStatus.message());
        assertEquals("ok", doStore(marca + "-sem").status(), "sem campo status o evento deixou de ser ok");
    }

    /** Regra única do status tirado do campo: texto não vazio, saneado e com teto; o resto é "ok". */
    @Test
    void statusDosCamposRegra() {
        assertEquals("timeout", TelemetriaLogger.statusDosCampos(Map.of("status", " timeout ")));
        assertEquals("bloqueado", TelemetriaLogger.statusDosCampos(Map.of("status", "bloqueado", "x", 1)));
        assertEquals("ok", TelemetriaLogger.statusDosCampos(null));
        assertEquals("ok", TelemetriaLogger.statusDosCampos(Map.of()));
        assertEquals("ok", TelemetriaLogger.statusDosCampos(Map.of("status", "   ")));
        assertEquals("ok", TelemetriaLogger.statusDosCampos(Map.of("status", 5)));
        assertFalse(TelemetriaLogger.statusDosCampos(Map.of("status", "a\nb")).contains("\n"),
                "quebra de linha crua no status forjaria linha no console");
        String longo = TelemetriaLogger.statusDosCampos(Map.of("status", "x".repeat(5000)));
        assertTrue(longo.endsWith(TelemetriaContext.MARCA_TRUNCADO)
                && longo.length() <= TelemetriaLogger.MAX_VALOR_CAMPO + TelemetriaContext.MARCA_TRUNCADO.length(),
                "status sem teto (contrato do paraLog: no máximo o teto antes da marca de corte)");
    }

    private TelemetriaEvent doStore(String marca) {
        return store.snapshotEventos().stream()
                .filter(e -> e.fields() != null && marca.equals(e.fields().get("marca")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("evento " + marca + " não entrou no armazenamento"));
    }
}
