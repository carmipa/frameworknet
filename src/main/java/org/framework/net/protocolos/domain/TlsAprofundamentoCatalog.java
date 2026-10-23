package org.framework.net.protocolos.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.protocolos.exception.ProtocolosException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Carregador do conteúdo da página de aprofundamento do TLS.
 *
 * <p><b>Propósito de negócio:</b> ler uma única vez, na subida da aplicação, o
 * arquivo que alimenta {@code /protocolos/tls}. Conteúdo estático e leitura pura
 * não justificam I/O por requisição, e ler no boot faz o erro aparecer na subida
 * — não na cara de quem abriu a página.</p>
 *
 * <p><b>Invariantes do domínio:</b> falha fechada — resumo em branco ou seção
 * obrigatória vazia impede a subida. As seções de ciphers, ataques e mitigações
 * são as mais sensíveis: uma página de TLS que perde metade das defesas orienta
 * errado, o que é pior que não existir.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> lança {@link ProtocolosException}
 * nomeando o recurso e a seção ausente.</p>
 */
@Startup
@ApplicationScoped
public class TlsAprofundamentoCatalog {

    private static final String RECURSO = "/protocolos/tls/conteudo.json";

    @Inject
    ObjectMapper objectMapper;

    private TlsAprofundamento conteudo;

    @PostConstruct
    void carregar() {
        try (InputStream input = TlsAprofundamentoCatalog.class.getResourceAsStream(RECURSO)) {
            if (input == null) {
                throw new ProtocolosException("Conteúdo do aprofundamento TLS não encontrado: " + RECURSO);
            }
            TlsAprofundamento carregado = objectMapper.readValue(input, TlsAprofundamento.class);
            validar(carregado);
            this.conteudo = carregado;
        } catch (IOException e) {
            throw new ProtocolosException("Falha ao carregar o conteúdo do aprofundamento TLS", e);
        }
    }

    public TlsAprofundamento getConteudo() {
        return conteudo;
    }

    private static void validar(TlsAprofundamento carregado) {
        if (carregado == null || carregado.resumo() == null || carregado.resumo().isBlank()) {
            throw new ProtocolosException("Conteúdo do aprofundamento TLS sem resumo: " + RECURSO);
        }
        exigir(carregado.conceitos(), "conceitos");
        exigir(carregado.handshake(), "handshake");
        exigir(carregado.cadeia(), "cadeia");
        exigir(carregado.ciphers(), "ciphers");
        exigir(carregado.ataques(), "ataques");
        exigir(carregado.mitigacoes(), "mitigacoes");
        exigir(carregado.laboratorios(), "laboratorios");
        exigir(carregado.diagnosticos(), "diagnosticos");
    }

    private static void exigir(List<?> secao, String nome) {
        if (secao == null || secao.isEmpty()) {
            throw new ProtocolosException(
                    "Seção \"" + nome + "\" ausente ou vazia em " + RECURSO
                            + " — a página do TLS não pode subir incompleta.");
        }
    }
}
