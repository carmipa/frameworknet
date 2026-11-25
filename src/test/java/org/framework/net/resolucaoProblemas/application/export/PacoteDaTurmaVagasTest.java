package org.framework.net.resolucaoProblemas.application.export;

import org.framework.net.resolucaoProblemas.domain.model.ClassRosterRow;
import org.framework.net.resolucaoProblemas.domain.model.ResolucaoFormData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vagas de montagem do "Pacote da turma" (Lote 1, revisão operacional): o teto por pedido não limitava a soma — 60
 * pedidos no teto chegando juntos seguravam centenas de MB. Sem vaga, a recusa é na hora; a vaga volta sempre, mesmo
 * quando a montagem falha no meio.
 */
class PacoteDaTurmaVagasTest {

    private static final List<ClassRosterRow> UM_ALUNO = List.of(new ClassRosterRow());

    /**
     * A2: com a vaga tomada, o pedido espera {@code ESPERA_VAGA_SEGUNDOS} e desiste sem montar nada (as dependências nem
     * existem neste teste), com o tipo que diz "ocupado" — não "colagem errada".
     */
    @Test
    void semVagaNaEsperaDesisteComOcupado() throws InterruptedException {
        ExportClassZipService servico = new ExportClassZipService();
        servico.vagas.acquire(ExportClassZipService.MAX_PACOTES_SIMULTANEOS);
        long inicio = System.nanoTime();
        org.framework.net.resolucaoProblemas.exception.PacoteTurmaOcupadoException ex = assertThrows(
                org.framework.net.resolucaoProblemas.exception.PacoteTurmaOcupadoException.class,
                () -> servico.generateClassRosterZipBuffer(UM_ALUNO, new ResolucaoFormData()));
        long esperou = (System.nanoTime() - inicio) / 1_000_000;
        assertTrue(ex.getMessage().contains("Tente de novo"), ex.getMessage());
        assertTrue(esperou >= ExportClassZipService.ESPERA_VAGA_SEGUNDOS * 1000 - 200,
                "desistiu sem esperar a vaga: " + esperou + " ms");
    }

    /** A1: a vaga que vaga dentro da espera é usada — o segundo professor não é recusado por chegar meio segundo depois. */
    @Test
    void vagaQueSoltaNaEsperaEUsada() throws InterruptedException {
        ExportClassZipService servico = new ExportClassZipService();
        servico.vagas.acquire(ExportClassZipService.MAX_PACOTES_SIMULTANEOS);
        Thread solta = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignorado) {
                Thread.currentThread().interrupt();
            }
            servico.vagas.release(ExportClassZipService.MAX_PACOTES_SIMULTANEOS);
        });
        solta.start();
        // Sem as dependências, a montagem falha DEPOIS de passar pela vaga: não é a recusa por ocupado.
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> servico.generateClassRosterZipBuffer(UM_ALUNO, new ResolucaoFormData()));
        solta.join();
        assertTrue(!(ex instanceof org.framework.net.resolucaoProblemas.exception.PacoteTurmaOcupadoException),
                "a vaga solta dentro da espera não foi usada");
    }

    /** A vaga volta mesmo quando a montagem falha no meio (aqui, sem as dependências injetadas). */
    @Test
    void vagaVoltaQuandoAMontagemFalha() {
        ExportClassZipService servico = new ExportClassZipService();
        assertThrows(RuntimeException.class,
                () -> servico.generateClassRosterZipBuffer(UM_ALUNO, new ResolucaoFormData()));
        assertEquals(ExportClassZipService.MAX_PACOTES_SIMULTANEOS, servico.vagas.availablePermits());
    }

    /**
     * Revisão adversarial (2ª passada): cada pedido na espera segura uma thread de trabalho por até
     * {@code ESPERA_VAGA_SEGUNDOS}; sem limite, uma rajada prendia o pool e o site inteiro esperava. Com a vaga tomada e
     * {@code MAX_NA_FILA} pedidos já esperando, o seguinte é recusado NA HORA. A2: sem o limite, ele esperaria os 5 s e a
     * asserção de tempo cai. Os que estavam na fila seguem esperando e, com a vaga solta, passam por ela.
     */
    @Test
    void filaCheiaRecusaNaHora() throws Exception {
        ExportClassZipService servico = new ExportClassZipService();
        servico.vagas.acquire(ExportClassZipService.MAX_PACOTES_SIMULTANEOS);
        List<Thread> naEspera = new java.util.ArrayList<>();
        java.util.concurrent.ConcurrentLinkedQueue<Throwable> desfechos = new java.util.concurrent.ConcurrentLinkedQueue<>();
        for (int i = 0; i < ExportClassZipService.MAX_NA_FILA; i++) {
            Thread t = new Thread(() -> {
                try {
                    servico.generateClassRosterZipBuffer(UM_ALUNO, new ResolucaoFormData());
                } catch (Throwable fim) {
                    desfechos.add(fim);
                }
            });
            t.start();
            naEspera.add(t);
        }
        long limite = System.nanoTime() + 3_000_000_000L;
        while (servico.naFila.get() < ExportClassZipService.MAX_NA_FILA && System.nanoTime() < limite) {
            Thread.sleep(10);
        }
        assertEquals(ExportClassZipService.MAX_NA_FILA, servico.naFila.get(), "a fila não encheu para o teste valer");

        long inicio = System.nanoTime();
        assertThrows(org.framework.net.resolucaoProblemas.exception.PacoteTurmaOcupadoException.class,
                () -> servico.generateClassRosterZipBuffer(UM_ALUNO, new ResolucaoFormData()));
        long esperou = (System.nanoTime() - inicio) / 1_000_000;
        assertTrue(esperou < 1_000, "com a fila cheia, o pedido esperou " + esperou + " ms em vez de ser recusado");

        servico.vagas.release(ExportClassZipService.MAX_PACOTES_SIMULTANEOS);
        for (Thread t : naEspera) {
            t.join(10_000);
        }
        assertEquals(ExportClassZipService.MAX_NA_FILA, desfechos.size());
        assertTrue(desfechos.stream().noneMatch(
                        d -> d instanceof org.framework.net.resolucaoProblemas.exception.PacoteTurmaOcupadoException),
                "quem estava na fila tem de passar pela vaga solta: " + desfechos);
        assertEquals(0, servico.naFila.get(), "o lugar na fila não foi devolvido");
        assertEquals(ExportClassZipService.MAX_PACOTES_SIMULTANEOS, servico.vagas.availablePermits());
    }
}
