package org.framework.net.resolucaoProblemas.application.importing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.application.normalization.VlsmNormalizationService;
import org.framework.net.shared.InputLimits;
import org.framework.net.shared.UserInputSanitizer;
import org.framework.net.resolucaoProblemas.domain.kernel.Ipv4Kernel;
import org.framework.net.resolucaoProblemas.domain.model.ClassRosterRow;
import org.framework.net.resolucaoProblemas.domain.model.LocationInput;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@ApplicationScoped
public class BulkClassImportService {

    private static final Set<String> HEADER_MARKERS = Set.of(
            "nome", "name", "aluno", "estudante", "rede", "base", "ip", "hosts", "host", "matriz", "filial"
    );
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");
    private static final Pattern CSV_SPLIT = Pattern.compile("\\s*,\\s*");

    @Inject
    Ipv4Kernel ipv4Kernel;

    @Inject
    VlsmNormalizationService normalizationService;

    public List<ClassRosterRow> parseClassRosterPaste(String rawText) {
        return parseClassRosterPaste(rawText, List.of("Matriz", "Filial"), 16);
    }

    public List<ClassRosterRow> parseClassRosterPaste(
            String rawText,
            List<String> defaultLocationLabels,
            int defaultPrefix) {

        String text = rawText == null ? "" : rawText.strip();
        if (text.isEmpty()) {
            throw new EntradaInvalidaException(
                    "Cole os dados da planilha na área «Importar turma» (Ctrl+C no Excel → Ctrl+V aqui).");
        }

        List<ClassRosterRow> rows = new ArrayList<>();
        int lineNo = 0;
        List<String> locLabels = defaultLocationLabels == null || defaultLocationLabels.isEmpty()
                ? List.of("Matriz", "Filial")
                : defaultLocationLabels;

        // Dois alunos com o mesmo nome (ou nomes que normalizam igual) não podem dividir a pasta do ZIP.
        java.util.Set<String> pastasUsadas = new java.util.HashSet<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            lineNo++;
            List<String> parts = splitLine(line);
            if (parts.size() < 4) {
                throw new EntradaInvalidaException(
                        "Linha " + lineNo + ": esperado Nome, Rede base, Hosts1, Hosts2 "
                                + "(separados por TAB). Encontrado: " + parts.size() + " coluna(s).");
            }
            if (looksLikeHeader(parts)) {
                continue;
            }

            String studentName = UserInputSanitizer.sanitizeLabel(parts.get(0));
            if (studentName.isEmpty()) {
                throw new EntradaInvalidaException("Linha " + lineNo + ": nome do aluno inválido.");
            }
            String baseNetwork = normalizeBaseNetwork(parts.get(1), defaultPrefix);
            List<String> hostValues = parts.subList(2, parts.size());
            if (hostValues.size() < 2) {
                throw new EntradaInvalidaException(
                        "Linha " + lineNo + " (" + studentName + "): informe ao menos dois valores de hosts.");
            }
            // Cada coluna de hosts vira uma localidade: o teto do formulário vale aqui também, senão
            // uma linha com centenas de colunas em malha travava um núcleo por horas.
            if (hostValues.size() > InputLimits.MAX_LOCATION_ROWS) {
                throw new EntradaInvalidaException(
                        "Linha " + lineNo + " (" + studentName + "): máximo de " + InputLimits.MAX_LOCATION_ROWS
                                + " localidades (colunas de hosts); encontradas " + hostValues.size() + ".");
            }

            List<String> locationNames = locLabels;
            if (hostValues.size() != locLabels.size()) {
                locationNames = defaultLocationNames(hostValues.size());
            }

            List<LocationInput> locations = new ArrayList<>();
            for (int idx = 0; idx < hostValues.size(); idx++) {
                String label = idx < locationNames.size() ? locationNames.get(idx) : "Site " + (idx + 1);
                int hosts = parseHostsValue(hostValues.get(idx), lineNo, "hosts #" + (idx + 1));
                locations.add(new LocationInput(label, String.valueOf(hosts)));
            }

            ClassRosterRow row = new ClassRosterRow();
            row.setStudentName(studentName);
            row.setFolderSlug(org.framework.net.resolucaoProblemas.application.export.ExportTxtService.nomeUnico(
                    normalizationService.normalizeCliIdentifier(studentName, "ALUNO" + lineNo), pastasUsadas));
            row.setBaseNetwork(baseNetwork);
            row.setLocations(locations);
            rows.add(row);
            if (rows.size() > InputLimits.MAX_CLASS_ROSTER_ROWS) {
                throw new EntradaInvalidaException(
                        "Máximo de " + InputLimits.MAX_CLASS_ROSTER_ROWS + " alunos por importação.");
            }
        }

        if (rows.isEmpty()) {
            throw new EntradaInvalidaException(
                    "Nenhuma linha válida encontrada. Selecione as células A–D no Excel, "
                            + "copie (Ctrl+C) e cole aqui.");
        }
        return rows;
    }

    private List<String> splitLine(String line) {
        List<String> parts;
        if (line.contains("\t")) {
            parts = List.of(line.split("\t", -1));
        } else if (line.contains(";")) {
            parts = List.of(line.split(";", -1));
        } else if (line.contains(",")) {
            parts = List.of(CSV_SPLIT.split(line));
        } else {
            parts = List.of(MULTI_SPACE.split(line.strip()));
        }
        List<String> cleaned = new ArrayList<>();
        for (String part : parts) {
            String value = part == null ? "" : part.strip();
            if (!value.isEmpty()) {
                cleaned.add(value);
            }
        }
        return cleaned;
    }

    private boolean looksLikeHeader(List<String> parts) {
        if (parts.size() < 2) {
            return false;
        }
        String joined = String.join(" ", parts.subList(0, Math.min(4, parts.size()))).toLowerCase(Locale.ROOT);
        return HEADER_MARKERS.stream().anyMatch(joined::contains) && !looksLikeIp(parts.get(1));
    }

    private boolean looksLikeIp(String value) {
        String txt = value == null ? "" : value.strip();
        if (txt.contains("/")) {
            txt = txt.split("/", 2)[0].strip();
        }
        String[] chunks = txt.split("\\.", -1);
        if (chunks.length != 4) {
            return false;
        }
        try {
            for (String chunk : chunks) {
                int octet = org.framework.net.shared.NumeroAscii.parseIntEstrito(chunk.strip());
                if (octet < 0 || octet > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String normalizeBaseNetwork(String value, int defaultPrefix) {
        String txt = value == null ? "" : value.strip();
        if (txt.isEmpty()) {
            throw new EntradaInvalidaException("Rede base vazia na linha da planilha.");
        }
        if (!txt.contains("/")) {
            txt = txt + "/" + defaultPrefix;
        }
        String[] split = txt.split("/", 2);
        if (!looksLikeIp(split[0])) {
            throw new EntradaInvalidaException("IP de rede base inválido: " + value);
        }
        int prefix;
        try {
            prefix = org.framework.net.shared.NumeroAscii.parseIntEstrito(split[1].strip());
        } catch (NumberFormatException ex) {
            throw new EntradaInvalidaException("CIDR inválido em " + value);
        }
        if (prefix < 0 || prefix > 32) {
            throw new EntradaInvalidaException("CIDR fora do intervalo em " + value);
        }
        return split[0].strip() + "/" + prefix;
    }

    private int parseHostsValue(String value, int lineNo, String colLabel) {
        String txt = value == null ? "" : value.strip();
        if (txt.isEmpty()) {
            throw new EntradaInvalidaException("Linha " + lineNo + ": coluna " + colLabel + " de hosts está vazia.");
        }
        java.util.OptionalInt lido = org.framework.net.shared.NumeroAscii.inteiro(txt, org.framework.net.shared.NumeroAscii.MAX_DIGITOS_INT);
        if (lido.isEmpty()) {
            throw new EntradaInvalidaException(
                    "Linha " + lineNo + ": '" + txt + "' em " + colLabel + " deve ser um número inteiro de até "
                            + org.framework.net.shared.NumeroAscii.MAX_DIGITOS_INT + " dígitos.");
        }
        int number = lido.getAsInt();
        if (number <= 0) {
            throw new EntradaInvalidaException(
                    "Linha " + lineNo + ": hosts em " + colLabel + " deve ser maior que zero.");
        }
        if (number > InputLimits.MAX_HOSTS_PER_LOCATION) {
            throw new EntradaInvalidaException(
                    "Linha " + lineNo + ": hosts em " + colLabel + " excede " + InputLimits.MAX_HOSTS_PER_LOCATION + ".");
        }
        return number;
    }

    private List<String> defaultLocationNames(int count) {
        if (count <= 2) {
            List<String> names = new ArrayList<>();
            if (count >= 1) {
                names.add("Matriz");
            }
            if (count >= 2) {
                names.add("Filial");
            }
            return names;
        }
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            names.add("Site " + i);
        }
        return names;
    }
}
