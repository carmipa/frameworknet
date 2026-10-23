package org.framework.net.shared;

/**
 * PROPÓSITO DE NEGÓCIO: recusa de {@link NetworkAddressGuard} — hostname interno/bloqueado ou endereço
 * não público numa resolução (mitigação de SSRF/rebinding).
 *
 * INVARIANTES DO DOMÍNIO: pertence ao kernel {@code shared}, que não depende de nenhum módulo de
 * negócio; cada módulo traduz para a própria exceção na sua fronteira (ex.: a Análise Didática a
 * converte em {@code DnsResolucaoException}, preservando a mensagem e a causa).
 *
 * COMPORTAMENTO EM CASO DE FALHA: é a própria falha — não captura nada.
 */
public class EnderecoBloqueadoException extends RuntimeException {

    public EnderecoBloqueadoException(String message) {
        super(message);
    }
}
