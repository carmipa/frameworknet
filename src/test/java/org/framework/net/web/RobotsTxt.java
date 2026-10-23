package org.framework.net.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Leitor de {@code robots.txt} usado pelos testes.
 *
 * <p><b>Propósito de negócio:</b> mais de um teste precisa responder "esta rota
 * está fechada para este robô?". O critério mora aqui, numa implementação só:
 * duas leituras do mesmo arquivo divergem com o tempo, e a segunda sempre
 * esquece a regra que a primeira aprendeu.</p>
 *
 * <p><b>Invariantes do domínio:</b> reproduz as duas regras do formato que
 * enganam quem lê por busca de texto — (1) o arquivo <b>não soma grupos</b>: os
 * {@code User-agent:} consecutivos formam um grupo só, e um {@code User-agent:}
 * depois de uma regra abre grupo novo; (2) o casamento é por <b>prefixo</b>,
 * então {@code Disallow: /telemetria} fecha também
 * {@code /telemetria/api/exportar}. Comentário e campo desconhecido
 * ({@code Allow}, {@code Crawl-delay}, {@code Sitemap}) são ignorados sem
 * quebrar o agrupamento.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> não lança. Arquivo vazio devolve
 * mapa vazio — cabe a quem chama reprovar nesse caso, porque "nenhuma regra" e
 * "não consegui ler" não podem parecer a mesma coisa.</p>
 */
final class RobotsTxt {

    private RobotsTxt() {
    }

    /** Agente (em minúsculas) para a lista de caminhos que ele não pode rastrear. */
    static Map<String, List<String>> grupos(String corpo) {
        Map<String, List<String>> grupos = new LinkedHashMap<>();
        List<String> agentes = new ArrayList<>();
        boolean lendoRegras = false;

        for (String bruta : corpo.split("\\R")) {
            String linha = bruta;
            int comentario = linha.indexOf(35); // '#'
            if (comentario >= 0) {
                linha = linha.substring(0, comentario);
            }
            linha = linha.trim();
            int separador = linha.indexOf(58); // ':'
            if (linha.isEmpty() || separador < 0) {
                continue;
            }
            String campo = linha.substring(0, separador).trim().toLowerCase(Locale.ROOT);
            String valor = linha.substring(separador + 1).trim();

            if ("user-agent".equals(campo)) {
                if (lendoRegras) {
                    agentes = new ArrayList<>();
                    lendoRegras = false;
                }
                String agente = valor.toLowerCase(Locale.ROOT);
                agentes.add(agente);
                grupos.computeIfAbsent(agente, chave -> new ArrayList<>());
                continue;
            }

            lendoRegras = true;
            if ("disallow".equals(campo) && !valor.isEmpty()) {
                for (String agente : agentes) {
                    grupos.get(agente).add(valor);
                }
            }
        }
        return grupos;
    }

    /** Casamento por prefixo, que é como o robots.txt decide. */
    static boolean bloqueado(List<String> disallows, String caminho) {
        return disallows.stream().anyMatch(caminho::startsWith);
    }

    /**
     * Raízes da área autenticada e das telas de entrada. Ficam fora do robots.txt público (listar
     * caminho privado é entregar o mapa) e saem do índice pelo cabeçalho {@code X-Robots-Tag}; também
     * não entram no sitemap.
     */
    static final List<String> AREA_AUTENTICADA = List.of("/admin", "/login", "/telemetria");

    /** Diz se o caminho está na área autenticada: a raiz exata ou abaixo dela ({@code /loginx} não está). */
    static boolean areaAutenticada(String caminho) {
        return caminho != null && AREA_AUTENTICADA.stream()
                .anyMatch(raiz -> caminho.equals(raiz) || caminho.startsWith(raiz + "/"));
    }
}
