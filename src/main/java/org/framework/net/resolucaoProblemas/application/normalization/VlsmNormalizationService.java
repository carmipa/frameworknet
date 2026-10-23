package org.framework.net.resolucaoProblemas.application.normalization;

import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.resolucaoProblemas.domain.model.LanBlock;
import org.framework.net.resolucaoProblemas.domain.model.LocationInput;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class VlsmNormalizationService {

    public static final List<LocationInput> DEFAULT_LOCATIONS = List.of(
            new LocationInput("Matriz", "400"),
            new LocationInput("Filial I", "390"),
            new LocationInput("Filial II", "350"),
            new LocationInput("Data Center", "300")
    );

    public static final DemoScenario FIAP_CHECKPOINT_DEMO = new DemoScenario(
            "172.42.0.0/16", "extended_star", "30", "203", "203", "telnet", "eigrp_only",
            DEFAULT_LOCATIONS
    );

    public static final DemoScenario MAZOLAS_GLOBAL_SOLUTION_DEMO = new DemoScenario(
            "172.63.0.0/16", "star", "30", "", "", "telnet", "eigrp_only",
            List.of(
                    new LocationInput("Matriz", "900"),
                    new LocationInput("Filial I", "700"),
                    new LocationInput("Filial II", "750")
            )
    );

    public static final DemoScenario EIGHT_ROUTERS_DEMO = new DemoScenario(
            "10.50.0.0/16", "extended_star", "30", "71", "1", "ssh", "auto",
            List.of(
                    new LocationInput("Matriz", "220"),
                    new LocationInput("Filial Norte", "180"),
                    new LocationInput("Filial Sul", "160"),
                    new LocationInput("Filial Leste", "150"),
                    new LocationInput("Filial Oeste", "140"),
                    new LocationInput("Filial Centro", "130"),
                    new LocationInput("Filial Interior", "120"),
                    new LocationInput("Data Center", "200")
            )
    );

    public String normalizeRemoteAccess(String value) {
        String mode = value == null ? "telnet" : value.strip().toLowerCase(Locale.ROOT);
        if (Set.of("ssh", "telnet", "both").contains(mode)) {
            return mode;
        }
        return "telnet";
    }

    public int parsePositiveInt(String value, String fieldLabel) {
        String txt = value == null ? "" : value.strip();
        if (txt.isEmpty()) {
            throw new EntradaInvalidaException("Campo obrigatório: " + fieldLabel + ".");
        }
        java.util.OptionalInt lido = org.framework.net.shared.NumeroAscii.inteiro(txt, org.framework.net.shared.NumeroAscii.MAX_DIGITOS_INT);
        if (lido.isEmpty()) {
            throw new EntradaInvalidaException(fieldLabel + " deve ser um número inteiro positivo de até "
                    + org.framework.net.shared.NumeroAscii.MAX_DIGITOS_INT + " dígitos.");
        }
        int number = lido.getAsInt();
        if (number <= 0) {
            throw new EntradaInvalidaException(fieldLabel + " deve ser maior que zero.");
        }
        return number;
    }

    public String normalizeCliIdentifier(String value, String fallback) {
        String txt = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        StringBuilder clean = new StringBuilder();
        for (char ch : txt.toCharArray()) {
            clean.append(Character.isLetterOrDigit(ch) ? ch : '_');
        }
        String[] parts = clean.toString().split("_+");
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (!joined.isEmpty()) {
                    joined.append('_');
                }
                joined.append(part);
            }
        }
        return joined.isEmpty() ? fallback : joined.toString();
    }

    public List<LanBlock> normalizeLocationsInput(List<LocationInput> locationsInput) {
        if (locationsInput == null || locationsInput.isEmpty()) {
            throw new EntradaInvalidaException("Informe ao menos uma localidade.");
        }
        List<LanBlock> normalized = new ArrayList<>();
        Set<String> namesSeen = new HashSet<>();
        int index = 1;
        for (LocationInput raw : locationsInput) {
            String name = raw.getName() == null ? "" : raw.getName().strip();
            if (name.isEmpty()) {
                throw new EntradaInvalidaException("Nome da localidade #" + index + " deve ser informado.");
            }
            String keyName = name.toLowerCase(Locale.ROOT);
            if (namesSeen.contains(keyName)) {
                throw new EntradaInvalidaException("Existem localidades com nomes duplicados: '" + name + "'.");
            }
            namesSeen.add(keyName);
            int hosts = parsePositiveInt(raw.getHosts(), "Hosts de " + name);
            LanBlock block = new LanBlock();
            block.setLocationKey("loc_" + index);
            block.setLocationName(name);
            block.setHostsRequired(hosts);
            block.setRouterName("R-" + normalizeCliIdentifier(name, "SITE" + index));
            block.setCliId(normalizeCliIdentifier(name, "SITE" + index));
            normalized.add(block);
            index++;
        }
        return normalized;
    }

    public record DemoScenario(
            String baseNetwork,
            String topologyType,
            String wanPrefix,
            String eigrpAs,
            String ospfProcess,
            String remoteAccess,
            String routingMode,
            List<LocationInput> locations
    ) { }
}
