package org.framework.net.web.support;

import io.quarkus.qute.TemplateExtension;

import java.util.Locale;

/**
 * Mapeia o rótulo de uma coluna de tabela para um ícone Material Symbols.
 *
 * <p><b>Propósito de negócio:</b> dar ícone + significado às colunas das tabelas
 * de catálogo que são renderizadas de dados (cabeçalhos vindos do JSON), sem
 * precisar cravar o ícone em cada template. Chamado no Qute como
 * {@code {coluna.iconeColuna}}.</p>
 *
 * <p><b>Invariantes do domínio:</b> função pura e total — qualquer entrada
 * (inclusive nula ou desconhecida) devolve um ícone válido; o desconhecido cai
 * num ícone neutro de coluna. A comparação ignora caixa e espaços nas bordas.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> nunca lança; entrada nula/vazia →
 * {@code "view_column"}.</p>
 */
@TemplateExtension
public final class IconesColuna {

    private IconesColuna() {
    }

    public static String iconeColuna(String coluna) {
        if (coluna == null || coluna.isBlank()) {
            return "view_column";
        }
        return switch (coluna.trim().toLowerCase(Locale.ROOT)) {
            case "#" -> "tag";
            case "osi", "camada", "camadas" -> "layers";
            case "tcp/ip", "serviço", "servico", "dns" -> "dns";
            case "pdu" -> "deployed_code";
            case "protocolo", "protocolos" -> "lan";
            case "dispositivo", "dispositivos", "equipamento" -> "router";
            case "algoritmo" -> "function";
            case "tipo", "categoria", "classe" -> "category";
            case "uso", "para que serve", "aplicação", "aplicacao" -> "task_alt";
            case "chave" -> "key";
            case "status", "seguro", "segurança", "seguranca" -> "shield";
            case "padrão", "padrao" -> "verified";
            case "nome" -> "label";
            case "ano" -> "calendar_month";
            case "banda" -> "cell_tower";
            case "velocidade", "vazão", "vazao" -> "speed";
            case "largura de canal", "largura" -> "straighten";
            case "ferramenta" -> "build";
            case "exemplo", "comando" -> "terminal";
            case "porta", "portas" -> "settings_ethernet";
            case "transporte" -> "swap_horiz";
            case "formato" -> "description";
            case "tamanho" -> "straighten";
            case "descrição", "descricao", "observação", "observacao" -> "notes";
            case "faixa" -> "linear_scale";
            default -> "view_column";
        };
    }
}
