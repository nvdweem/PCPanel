package com.getpcpanel.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import org.junit.jupiter.api.Test;

class TemplateServiceTest {
    private static TemplateService service() {
        var service = new TemplateService();
        service.engine = TemplateRendererTest.engine();
        service.core = new CoreTemplateVariables();
        service.namespaces = List.of();
        service.init();
        return service;
    }

    private final TemplateService service = service();

    @Test
    void theAutomaticNameIsOnlyResolvedWhenUsed() {
        var scope = TemplateScope.EMPTY.withValue(42L).withName(() -> fail("name must not be resolved"));
        assertEquals("42%", service.render("{{ value }}%", scope, () -> "fallback"));

        var named = TemplateScope.EMPTY.withValue(42L).withName(() -> "Spotify");
        assertEquals("Spotify 42%", service.render("{{ name }} {{ value }}%", named, () -> "fallback"));
    }

    @Test
    void plainTextIsReturnedAsIs() {
        assertEquals("Music", service.render("Music", TemplateScope.EMPTY, () -> fail("no render for plain text")));
    }
}
