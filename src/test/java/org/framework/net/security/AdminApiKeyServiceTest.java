package org.framework.net.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminApiKeyServiceTest {

    @Test
    void validaChaveCorreta() {
        AdminApiKeyService service = new AdminApiKeyService();
        inject(service, "segredo-123", true);
        assertTrue(service.isValid("segredo-123"));
        assertFalse(service.isValid("errado"));
    }

    @Test
    void enforcementDesligadoAceitaQualquerCoisa() {
        AdminApiKeyService service = new AdminApiKeyService();
        inject(service, "segredo-123", false);
        assertTrue(service.isValid(""));
    }

    @Test
    void detectaRotasProtegidas() {
        AdminApiKeyService service = new AdminApiKeyService();
        inject(service, "k", true);
        // Telemetria passou a ser protegida em 2026-08-03: verificado em produção
        // que /api/resumo, /api/dashboard, /api/console e /api/exportar devolviam o
        // IP real do visitante, e /api/pasta os caminhos do servidor.
        assertTrue(service.isProtectedPath("/telemetria"));
        assertTrue(service.isProtectedPath("/telemetria/api/resumo"));
        assertTrue(service.isProtectedPath("/telemetria/api/dashboard"));
        assertTrue(service.isProtectedPath("/telemetria/api/console"));
        assertTrue(service.isProtectedPath("/telemetria/api/exportar"));
        assertTrue(service.isProtectedPath("/telemetria/api/pasta"));
        assertTrue(service.isProtectedPath("/export/json"));

        assertFalse(service.isProtectedPath("/history"));
        assertFalse(service.isProtectedPath("/history/catalog"));
        assertFalse(service.isProtectedPath("/documentacao"));
        assertFalse(service.isProtectedPath("/calculadora"));
    }

    private static void inject(AdminApiKeyService service, String key, boolean required) {
        try {
            var keyField = AdminApiKeyService.class.getDeclaredField("configuredKey");
            keyField.setAccessible(true);
            keyField.set(service, key);
            var reqField = AdminApiKeyService.class.getDeclaredField("adminApiKeyRequired");
            reqField.setAccessible(true);
            reqField.set(service, required);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
