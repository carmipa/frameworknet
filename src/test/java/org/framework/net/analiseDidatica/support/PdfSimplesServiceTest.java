package org.framework.net.analiseDidatica.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfSimplesServiceTest {

    @Test
    void deveGerarPdfBasico() throws Exception {
        byte[] pdf = PdfSimplesService.gerarPdfSimples("Linha 1\nLinha 2");
        assertTrue(pdf.length > 100);
        assertTrue(new String(pdf, 0, 8).startsWith("%PDF-1.4"));
    }

    @Test
    void pdfContemOTextoInformado() throws Exception {
        // A6 — relê o PDF gravado e confirma que o texto entrou no content stream. Só tamanho +
        // magic bytes nao prova conteudo: um PDF em branco ou truncado tambem passaria naquilo.
        byte[] pdf = PdfSimplesService.gerarPdfSimples("Linha 1\nLinha 2");
        String conteudo = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(conteudo.contains("(Linha 1) Tj"), "PDF deve conter o texto 'Linha 1'");
        assertTrue(conteudo.contains("(Linha 2) Tj"), "PDF deve conter o texto 'Linha 2'");
        assertTrue(conteudo.contains("%%EOF"), "PDF deve terminar com %%EOF");
    }
}
