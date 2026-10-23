package org.framework.net.academia.eventos.domain;

import java.util.function.LongSupplier;

/**
 * Teto global de eventos aceitos por minuto, com contagem do que foi descartado.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> a rota de eventos é aberta (a lição funciona sem login). Sem
 * teto, uma enxurrada de relatórios — de um robô ou de um erro em laço num navegador — encheria a
 * janela de telemetria e expulsaria a evidência real que já estava lá. O teto protege o que já foi
 * registrado; o contador de descartes diz que houve enxurrada.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> no máximo {@code capacidade} aceites por janela de 60 s; o
 * descarte é contado, nunca silencioso; o relógio é injetado (nanossegundos monotônicos), então a
 * regra é testável sem esperar.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> capacidade menor que 1 lança
 * {@link IllegalArgumentException}; relógio que volta para trás é tratado como janela nova.</p>
 */
public final class OrcamentoEventos {

    private static final long JANELA_NANOS = 60_000_000_000L;

    private final int capacidade;
    private final LongSupplier relogio;
    private long inicioJanela;
    private int usados;
    private long descartados;

    public OrcamentoEventos(int capacidade, LongSupplier relogioNanos) {
        if (capacidade < 1) {
            throw new IllegalArgumentException("capacidade < 1");
        }
        this.capacidade = capacidade;
        this.relogio = relogioNanos;
        this.inicioJanela = relogioNanos.getAsLong();
    }

    /** {@code true} se o evento cabe na janela atual; senão conta um descarte. */
    public synchronized boolean consumir() {
        long agora = relogio.getAsLong();
        if (agora - inicioJanela >= JANELA_NANOS || agora < inicioJanela) {
            inicioJanela = agora;
            usados = 0;
        }
        if (usados < capacidade) {
            usados++;
            return true;
        }
        descartados++;
        return false;
    }

    /** Total de eventos descartados por falta de orçamento desde o arranque. */
    public synchronized long descartados() {
        return descartados;
    }
}
