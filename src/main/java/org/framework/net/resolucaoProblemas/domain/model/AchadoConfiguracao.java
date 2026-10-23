package org.framework.net.resolucaoProblemas.domain.model;

/**
 * Um achado da auditoria sobre a configuração colada.
 *
 * <p><b>Propósito de negócio:</b> o valor da aba de engenharia reversa não é
 * desenhar bonito — é dizer <em>o que está errado, onde, e por que a correção
 * proposta é aquela</em>. Cada achado carrega a evidência que o sustenta, porque
 * correção sem evidência é palpite, e palpite numa ferramenta de estudo ensina
 * errado.</p>
 *
 * <p><b>Invariantes do domínio:</b> achado de severidade
 * {@link Severidade#ERRO_CORRIGIDO} obrigatoriamente traz {@code corrigido} e
 * {@code evidencia} preenchidos — é o que separa uma correção derivada do texto
 * de um chute. Achado sem correção derivável nasce como
 * {@link Severidade#ERRO_SEM_CORRECAO} e é apontado sem ser consertado.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> é um record de dados, sem caminho de
 * erro. As fábricas estáticas existem para que nenhum ponto do código consiga
 * criar um "erro corrigido" sem dizer com base em quê.</p>
 */
public record AchadoConfiguracao(
        Severidade severidade,
        String categoria,
        String roteador,
        int linha,
        String original,
        String corrigido,
        String descricao,
        String evidencia) {

    /**
     * Grau de um achado.
     *
     * <p><b>Invariantes do domínio:</b> a ordem da enumeração é a ordem de
     * exibição — erro sem correção primeiro, porque é o que trava o cenário e
     * exige decisão humana.</p>
     */
    public enum Severidade {
        /** Impede o cenário e a ferramenta não consegue derivar a correção do texto. */
        ERRO_SEM_CORRECAO("Erro sem correção automática", "danger", "report"),
        /** Impede o cenário, mas a correção sai do próprio texto colado. */
        ERRO_CORRIGIDO("Erro corrigido", "warning", "build"),
        /** Não impede nada; é boa prática ou risco latente. */
        AVISO("Aviso", "info", "info");

        private final String rotulo;
        private final String cor;
        private final String icone;

        Severidade(String rotulo, String cor, String icone) {
            this.rotulo = rotulo;
            this.cor = cor;
            this.icone = icone;
        }

        public String rotulo() {
            return rotulo;
        }

        public String cor() {
            return cor;
        }

        public String icone() {
            return icone;
        }
    }

    /** Erro cuja correção foi derivada do próprio texto — exige a evidência. */
    public static AchadoConfiguracao corrigido(
            String categoria, String roteador, int linha,
            String original, String corrigido, String descricao, String evidencia) {
        return new AchadoConfiguracao(Severidade.ERRO_CORRIGIDO, categoria, roteador, linha,
                original, corrigido, descricao, evidencia);
    }

    /** Erro detectado sem correção derivável — aponta e para. */
    public static AchadoConfiguracao semCorrecao(
            String categoria, String roteador, int linha, String original,
            String descricao, String evidencia) {
        return new AchadoConfiguracao(Severidade.ERRO_SEM_CORRECAO, categoria, roteador, linha,
                original, "", descricao, evidencia);
    }

    /** Observação que não impede o cenário de funcionar. */
    public static AchadoConfiguracao aviso(
            String categoria, String roteador, int linha, String descricao, String evidencia) {
        return new AchadoConfiguracao(Severidade.AVISO, categoria, roteador, linha,
                "", "", descricao, evidencia);
    }

    public boolean temCorrecao() {
        return corrigido != null && !corrigido.isBlank();
    }

    public boolean temEvidencia() {
        return evidencia != null && !evidencia.isBlank();
    }

    /** Rótulo pronto para a tela — evita lógica de apresentação no template. */
    public String rotuloSeveridade() {
        return severidade.rotulo();
    }

    public String corSeveridade() {
        return severidade.cor();
    }

    public String iconeSeveridade() {
        return severidade.icone();
    }
}
