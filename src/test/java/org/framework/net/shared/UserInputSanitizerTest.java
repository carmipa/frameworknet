package org.framework.net.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserInputSanitizerTest {

    @Test
    void removeTagsHtml() {
        String cleaned = UserInputSanitizer.sanitizeLabel("<script>Matriz</script>");
        assertFalse(cleaned.contains("<"));
        assertFalse(cleaned.contains(">"));
        assertTrue(cleaned.contains("Matriz"));
    }
}
