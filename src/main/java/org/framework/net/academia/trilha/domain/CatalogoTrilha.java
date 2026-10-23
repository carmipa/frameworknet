package org.framework.net.academia.trilha.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * O catálogo da trilha: quais níveis e lições existem, e em que ordem.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> é o dono único do que a Academia oferece. A landing, as páginas
 * dos níveis, o sitemap e a validação dos eventos leem daqui — assim uma lição nova aparece em
 * todos esses lugares de uma vez, e uma lição inexistente é recusada em todos eles.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> ids de nível e de lição únicos; a ordem da lista é a ordem de
 * estudo; o primeiro nível está aberto; id de lição publicado nunca muda (é chave do progresso
 * guardado no navegador do aluno).</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> {@link #validar()} lança
 * {@link IllegalStateException} descrevendo a inconsistência; as consultas por id devolvem
 * {@link Optional#empty()} para id desconhecido, nunca nulo.</p>
 */
public final class CatalogoTrilha {

    private static final List<Nivel> NIVEIS = List.of(
            new Nivel("fundamentos", "Fundamentos",
                    "Como o computador guarda números e como uma mensagem vira quadro na rede: binário, "
                            + "hexadecimal e o encapsulamento em camadas.",
                    "school", true, List.of(
                    new Licao("fundamentos.binario", "Binário",
                            "Acenda e apague os oito bits de um byte e veja o número mudar na hora.",
                            "toggle_on", 15),
                    new Licao("fundamentos.hexadecimal", "Hexadecimal",
                            "Agrupe os bits de quatro em quatro e leia o mesmo byte em dois dígitos.",
                            "hexagon", 15),
                    new Licao("fundamentos.camadas", "Camadas e encapsulamento",
                            "Escreva uma mensagem e veja cada camada somar o seu cabeçalho até o quadro.",
                            "layers", 20))),
            new Nivel("ipv4", "IPv4",
                    "Máscara, prefixo CIDR e divisão em sub-redes, com a régua dos 32 bits.",
                    "lan", true, List.of(
                    new Licao("ipv4.mascara", "Máscara e prefixo",
                            "Arraste a divisória dos 32 bits e veja rede, broadcast e hosts mudarem na hora.",
                            "straighten", 20),
                    new Licao("ipv4.subredes", "Sub-redes",
                            "Divida um bloco em partes iguais e descubra o prefixo e o endereço de cada uma.",
                            "account_tree", 25))),
            new Nivel("transporte", "Transporte",
                    "TCP e UDP: o aperto de mão em três vias, a janela deslizante e a retransmissão.",
                    "swap_horiz", true, List.of(
                    new Licao("transporte.aperto", "Aperto de mão e sequência",
                            "Siga o SYN, o SYN-ACK e o ACK e descubra o número que cada lado confirma.",
                            "handshake", 20),
                    new Licao("transporte.janela", "Janela e retransmissão",
                            "Perca um segmento de propósito e compare o que o Go-Back-N e a repetição seletiva reenviam.",
                            "view_week", 25))),
            new Nivel("enlace", "Enlace",
                    "Switch, tabela MAC, ARP e a eleição da raiz no Spanning Tree.",
                    "device_hub", false, List.of()));

    private CatalogoTrilha() {
    }

    /** Todos os níveis, na ordem de estudo. */
    public static List<Nivel> niveis() {
        return NIVEIS;
    }

    /** Nível pelo id. */
    public static Optional<Nivel> nivel(String id) {
        return NIVEIS.stream().filter(n -> n.id().equals(id)).findFirst();
    }

    /** Lição pelo id ({@code fundamentos.binario}). */
    public static Optional<Licao> licao(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return NIVEIS.stream().flatMap(n -> n.licoes().stream()).filter(l -> l.id().equals(id)).findFirst();
    }

    /** A lição seguinte dentro do mesmo nível, para o botão "próxima lição". */
    public static Optional<Licao> proxima(String id) {
        for (Nivel nivel : NIVEIS) {
            List<Licao> licoes = nivel.licoes();
            for (int i = 0; i < licoes.size() - 1; i++) {
                if (licoes.get(i).id().equals(id)) {
                    return Optional.of(licoes.get(i + 1));
                }
            }
        }
        return Optional.empty();
    }

    /** Endereços públicos da Academia (landing, níveis abertos e lições), na ordem de estudo. */
    public static List<String> rotasPublicas() {
        List<String> rotas = new ArrayList<>();
        rotas.add("/academia");
        for (Nivel nivel : NIVEIS) {
            if (nivel.aberto()) {
                rotas.add(nivel.rota());
                nivel.licoes().forEach(l -> rotas.add(l.rota()));
            }
        }
        return List.copyOf(rotas);
    }

    /**
     * Confere o catálogo inteiro.
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> {@link IllegalStateException} com o primeiro
     * problema encontrado.</p>
     */
    public static void validar() {
        if (NIVEIS.isEmpty() || !NIVEIS.getFirst().aberto()) {
            throw new IllegalStateException("a trilha precisa começar por um nível aberto");
        }
        Set<String> niveis = new HashSet<>();
        Set<String> licoes = new HashSet<>();
        for (Nivel nivel : NIVEIS) {
            if (!niveis.add(nivel.id())) {
                throw new IllegalStateException("nível repetido: " + nivel.id());
            }
            for (Licao licao : nivel.licoes()) {
                if (!licoes.add(licao.id())) {
                    throw new IllegalStateException("lição repetida: " + licao.id());
                }
            }
        }
    }
}
