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
 * Carregador do conteúdo da página de aprofundamento do SSH.
 *
 * <p><b>Propósito de negócio:</b> ler uma única vez, na subida da aplicação, o
 * arquivo que alimenta {@code /protocolos/ssh}, pelo mesmo motivo do BGP:
 * conteúdo estático e leitura pura não justificam I/O por requisição.</p>
 *
 * <p><b>Invariantes do domínio:</b> falha fechada — resumo em branco ou seção
 * obrigatória vazia impede a subida. A seção de endurecimento é a mais sensível:
 * uma página de hardening que perde metade das diretivas passa a orientar
 * errado, o que é pior que não existir.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> lança {@link ProtocolosException}
 * nomeando o recurso e a seção ausente.</p>
 */
// OPS-20: carrega e valida no boot, como o Javadoc promete (sem isto a validação só rodava na 1a visita).
@Startup
@ApplicationScoped
public class SshAprofundamentoCatalog {

    private static final String RECURSO = "/protocolos/ssh/conteudo.json";

    @Inject
    ObjectMapper objectMapper;

    private SshAprofundamento conteudo;

    @PostConstruct
    void carregar() {
        try (InputStream input = SshAprofundamentoCatalog.class.getResourceAsStream(RECURSO)) {
            if (input == null) {
                throw new ProtocolosException("Conteúdo do aprofundamento SSH não encontrado: " + RECURSO);
            }
            SshAprofundamento carregado = objectMapper.readValue(input, SshAprofundamento.class);
            validar(carregado);
            this.conteudo = carregado;
        } catch (IOException e) {
            throw new ProtocolosException("Falha ao carregar o conteúdo do aprofundamento SSH", e);
        }
    }

    public SshAprofundamento getConteudo() {
        return conteudo;
    }

    private static void validar(SshAprofundamento carregado) {
        if (carregado == null || carregado.resumo() == null || carregado.resumo().isBlank()) {
            throw new ProtocolosException("Conteúdo do aprofundamento SSH sem resumo: " + RECURSO);
        }
        exigir(carregado.conceitos(), "conceitos");
        exigir(carregado.camadas(), "camadas");
        exigir(carregado.autenticacoes(), "autenticacoes");
        exigir(carregado.chaves(), "chaves");
        exigir(carregado.encaminhamentos(), "encaminhamentos");
        exigir(carregado.hardening(), "hardening");
        exigir(carregado.laboratorios(), "laboratorios");
        exigir(carregado.diagnosticos(), "diagnosticos");
    }

    private static void exigir(List<?> secao, String nome) {
        if (secao == null || secao.isEmpty()) {
            throw new ProtocolosException(
                    "Seção \"" + nome + "\" ausente ou vazia em " + RECURSO
                            + " — a página do SSH não pode subir incompleta.");
        }
    }
}
