package org.framework.net.wifi.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Registro FLAT das páginas de aprofundamento do módulo Wifi. Fonte única do sub-menu, rotas, conteúdo, sitemap e testes. */
public record WifiAprofundamento(String slug, String titulo, String rotuloMenu, String icone, String chamada) {

    private static final List<WifiAprofundamento> DISPONIVEIS = List.of(
            new WifiAprofundamento("padroes", "Padrões 802.11 (Wi-Fi 4 a 7)", "Padrões", "wifi", "A evolução b/a/g/n/ac/ax/be."),
            new WifiAprofundamento("canais", "Frequências e canais", "Canais", "cell_tower", "2.4/5/6 GHz, canais e sobreposição (1/6/11)."),
            new WifiAprofundamento("seguranca", "Segurança: WEP, WPA, WPA2 e WPA3", "Segurança", "wifi_password", "WEP → WPA → WPA2 → WPA3 e o 4-way handshake."),
            new WifiAprofundamento("ataques", "Ataques a redes sem fio", "Ataques", "wifi_off", "Evil twin, deauth, captura de handshake, WPS."));

    public static List<WifiAprofundamento> disponiveis() { return DISPONIVEIS; }
    public static List<String> slugs() { return DISPONIVEIS.stream().map(WifiAprofundamento::slug).toList(); }
    public static Optional<WifiAprofundamento> porSlug(String slug) {
        if (slug == null || slug.isBlank()) { return Optional.empty(); }
        String alvo = slug.trim().toLowerCase(Locale.ROOT);
        return DISPONIVEIS.stream().filter(item -> item.slug().equals(alvo)).findFirst();
    }
    public String rota() { return "/wifi/" + slug; }
}
