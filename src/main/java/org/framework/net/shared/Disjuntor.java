package org.framework.net.shared;

import java.util.function.LongSupplier;

/**
 * Disjuntor de uma dependência opcional (Redis).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> com o Redis fora ou pendurado, cada consulta ao cache esperava o timeout
 * do cliente antes de seguir para a origem — toda requisição pagava a espera (auditoria OPS-17). Com o
 * disjuntor, a primeira falha desliga o uso por um intervalo e as seguintes vão direto à origem.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> relógio monotônico (ajuste de relógio de parede não adia a volta);
 * falha abre por {@link #ABERTO_NANOS}; depois disso uma chamada é permitida (meia-abertura) e o primeiro
 * sucesso fecha. Os métodos de registro dizem se houve TROCA de estado, para o log sair só na troca.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> não lança; é só estado.</p>
 */
public final class Disjuntor {

    /** Quanto tempo a dependência fica desligada depois de uma falha. */
    public static final long ABERTO_NANOS = 60_000_000_000L;

    private final LongSupplier relogio;
    private boolean aberto;
    private long abertoAte;

    public Disjuntor(LongSupplier relogioNanos) {
        this.relogio = relogioNanos;
    }

    /** A dependência pode ser chamada agora? */
    public synchronized boolean permite() {
        return !aberto || relogio.getAsLong() - abertoAte >= 0;
    }

    /** Registra falha; {@code true} se o disjuntor ABRIU agora (estava fechado). */
    public synchronized boolean falhou() {
        boolean abriu = !aberto;
        aberto = true;
        abertoAte = relogio.getAsLong() + ABERTO_NANOS;
        return abriu;
    }

    /** Registra sucesso; {@code true} se o disjuntor FECHOU agora (estava aberto). */
    public synchronized boolean funcionou() {
        boolean fechou = aberto;
        aberto = false;
        return fechou;
    }
}
