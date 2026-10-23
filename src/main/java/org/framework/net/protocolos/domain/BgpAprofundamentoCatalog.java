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
 * Carregador do conteúdo da página de aprofundamento do BGP.
 *
 * <p><b>Propósito de negócio:</b> ler uma única vez, na subida da aplicação, o
 * arquivo que alimenta {@code /protocolos/bgp}. Página didática é leitura pura e
 * de alto tráfego relativo; reler o JSON a cada requisição seria I/O sem
 * qualquer ganho.</p>
 *
 * <p><b>Invariantes do domínio:</b> falha fechada — resumo em branco ou
 * qualquer uma das listas obrigatórias vazia impede a aplicação de subir. É
 * deliberado: página pela metade em produção é pior que erro no boot, porque
 * ninguém percebe uma seção que sumiu.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> lança {@link ProtocolosException}
 * nomeando o recurso e a seção ausente, tanto para arquivo faltando quanto para
 * JSON inválido ou incompleto.</p>
 */
// OPS-20: carrega e valida no boot, como o Javadoc promete (sem isto a validação só rodava na 1a visita).
@Startup
@ApplicationScoped
public class BgpAprofundamentoCatalog {

    private static final String RECURSO = "/protocolos/bgp/conteudo.json";

    @Inject
    ObjectMapper objectMapper;

    private BgpAprofundamento conteudo;

    @PostConstruct
    void carregar() {
        try (InputStream input = BgpAprofundamentoCatalog.class.getResourceAsStream(RECURSO)) {
            if (input == null) {
                throw new ProtocolosException("Conteúdo do aprofundamento BGP não encontrado: " + RECURSO);
            }
            BgpAprofundamento carregado = objectMapper.readValue(input, BgpAprofundamento.class);
            validar(carregado);
            this.conteudo = carregado;
        } catch (IOException e) {
            throw new ProtocolosException("Falha ao carregar o conteúdo do aprofundamento BGP", e);
        }
    }

    public BgpAprofundamento getConteudo() {
        return conteudo;
    }

    private static void validar(BgpAprofundamento carregado) {
        if (carregado == null || carregado.resumo() == null || carregado.resumo().isBlank()) {
            throw new ProtocolosException("Conteúdo do aprofundamento BGP sem resumo: " + RECURSO);
        }
        exigir(carregado.conceitos(), "conceitos");
        exigir(carregado.atributos(), "atributos");
        exigir(carregado.selecaoMelhorRota(), "selecao_melhor_rota");
        exigir(carregado.estados(), "estados");
        exigir(carregado.mensagens(), "mensagens");
        exigir(carregado.temporizadores(), "temporizadores");
        exigir(carregado.topologias(), "topologias");
        exigir(carregado.protecoes(), "protecoes");
        exigir(carregado.laboratorios(), "laboratorios");
        exigir(carregado.diagnosticos(), "diagnosticos");
    }

    private static void exigir(List<?> secao, String nome) {
        if (secao == null || secao.isEmpty()) {
            throw new ProtocolosException(
                    "Seção \"" + nome + "\" ausente ou vazia em " + RECURSO
                            + " — a página do BGP não pode subir incompleta.");
        }
    }
}
