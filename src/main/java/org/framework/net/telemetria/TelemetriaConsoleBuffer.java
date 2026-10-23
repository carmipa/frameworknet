package org.framework.net.telemetria;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@ApplicationScoped
public class TelemetriaConsoleBuffer {

    private static final int MAX_LINHAS = 800;
    private static final DateTimeFormatter FORMATO =
            org.framework.net.shared.FusoDoSite.formato("yyyy-MM-dd HH:mm:ss");

    private final ReentrantLock lock = new ReentrantLock();
    private final Deque<String> linhas = new ArrayDeque<>();

    public void append(String nivel, String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            return;
        }
        String linha = FORMATO.format(Instant.now()) + " [" + nivel.toUpperCase() + "] " + mensagem.trim();
        lock.lock();
        try {
            linhas.addLast(linha);
            while (linhas.size() > MAX_LINHAS) {
                linhas.removeFirst();
            }
        } finally {
            lock.unlock();
        }
    }

    public List<String> snapshot(int limite) {
        int max = Math.max(1, Math.min(limite, MAX_LINHAS));
        lock.lock();
        try {
            List<String> copia = new ArrayList<>(linhas);
            int inicio = Math.max(0, copia.size() - max);
            return List.copyOf(copia.subList(inicio, copia.size()));
        } finally {
            lock.unlock();
        }
    }

    public void limpar() {
        lock.lock();
        try {
            linhas.clear();
        } finally {
            lock.unlock();
        }
    }
}
