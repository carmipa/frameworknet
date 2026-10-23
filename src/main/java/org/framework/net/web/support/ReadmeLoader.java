package org.framework.net.web.support;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class ReadmeLoader {

    public ReadmeContent carregar() {
        try (var in = Thread.currentThread().getContextClassLoader().getResourceAsStream("README.md")) {
            if (in != null) {
                return new ReadmeContent(new String(in.readAllBytes(), StandardCharsets.UTF_8), null);
            }
        } catch (IOException ignored) {
        }
        Path root = Path.of("README.md");
        if (Files.exists(root) && Files.isRegularFile(root)) {
            try {
                return new ReadmeContent(Files.readString(root, StandardCharsets.UTF_8), null);
            } catch (IOException ex) {
                return new ReadmeContent(null, "Não foi possível ler o README.md.");
            }
        }
        return new ReadmeContent(null, "Arquivo README.md não encontrado na raiz do projeto.");
    }

    public record ReadmeContent(String conteudo, String erro) {
    }
}
