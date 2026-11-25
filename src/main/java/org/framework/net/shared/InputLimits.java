package org.framework.net.shared;

public final class InputLimits {

    public static final int MAX_LOCATION_ROWS = 50;
    public static final int MAX_HOSTS_PER_LOCATION = 500_000;
    public static final int MAX_CLASS_ROSTER_ROWS = 200;
    public static final int MAX_LABEL_LENGTH = 120;

    /**
     * Maior nome de host em texto (RFC 1035 §2.3.4: 255 octetos no formato de rede = 253 caracteres com os
     * pontos). Vale para todo campo "domínio": nome maior não existe no DNS e só serve para gastar CPU.
     */
    public static final int MAX_NOME_HOST = 253;

    /**
     * Maior "Pacote da turma", somando os arquivos ANTES da compressão. Medido com o código de produção:
     * 200 alunos × 4 localidades = 11,5 MB (qualquer topologia); 200 × 10 em malha = 40 MB; 200 × 50 em
     * malha = ~790 MB (70 MB de ZIP e 30 s de CPU por pedido, que derrubavam a JVM com 10 simultâneos). O
     * ZIP é montado na memória: o orçamento soma também o nome de cada entrada (gravado duas vezes, sem compressão),
     * então o ZIP de um pedido não passa daqui; a soma entre pedidos é limitada pelas vagas de montagem do serviço.
     */
    public static final long MAX_BYTES_PACOTE_TURMA = 24L * 1024 * 1024;

    private InputLimits() {
    }
}
