package org.framework.net.analiseDidatica.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public final class PdfSimplesService {

    private PdfSimplesService() {
    }

    public static byte[] gerarPdfSimples(String texto) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String ln : texto.split("\\R")) {
            lines.add(pdfSafeText(ln).substring(0, Math.min(110, pdfSafeText(ln).length())));
        }

        StringBuilder stream = new StringBuilder();
        stream.append("BT\n/F1 11 Tf\n14 TL\n72 800 Td\n");
        int max = Math.min(58, lines.size());
        for (int i = 0; i < max; i++) {
            stream.append("(").append(lines.get(i)).append(") Tj\nT*\n");
        }
        stream.append("ET");
        byte[] content = stream.toString().getBytes(StandardCharsets.ISO_8859_1);

        List<byte[]> objs = new ArrayList<>();
        objs.add("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n".getBytes(StandardCharsets.US_ASCII));
        objs.add("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n".getBytes(StandardCharsets.US_ASCII));
        objs.add(("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                + "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj\n").getBytes(StandardCharsets.US_ASCII));
        objs.add("4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n".getBytes(StandardCharsets.US_ASCII));
        byte[] obj5Header = ("5 0 obj << /Length " + content.length + " >> stream\n").getBytes(StandardCharsets.US_ASCII);
        byte[] obj5Footer = "\nendstream endobj\n".getBytes(StandardCharsets.US_ASCII);
        byte[] obj5 = concat(obj5Header, content, obj5Footer);
        objs.add(obj5);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
        List<Integer> xref = new ArrayList<>();
        xref.add(0);
        for (byte[] obj : objs) {
            xref.add(output.size());
            output.write(obj);
        }
        int xrefPos = output.size();
        output.write(("xref\n0 " + xref.size() + "\n").getBytes(StandardCharsets.US_ASCII));
        output.write("0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
        for (int pos : xref.subList(1, xref.size())) {
            output.write(String.format("%010d 00000 n \n", pos).getBytes(StandardCharsets.US_ASCII));
        }
        output.write(("trailer << /Size " + xref.size() + " /Root 1 0 R >>\nstartxref\n" + xrefPos + "\n%%EOF")
                .getBytes(StandardCharsets.US_ASCII));
        return output.toByteArray();
    }

    private static String pdfSafeText(String s) {
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFKD);
        String ascii = normalized.replaceAll("[^\\x00-\\x7F]", "");
        return ascii.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static byte[] concat(byte[]... parts) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.write(p);
        }
        return out.toByteArray();
    }
}
