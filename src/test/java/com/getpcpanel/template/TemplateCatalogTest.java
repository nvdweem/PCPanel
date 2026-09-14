package com.getpcpanel.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.template.rest.dto.TemplateVariableDto;

import io.quarkus.qute.TemplateData;

class TemplateCatalogTest {
    @TemplateData
    @TemplateDoc("Test integration")
    public static final class Root {
        @TemplateDoc("Channels, by id")
        public LazyMap<Channel> getChannel() {
            var channels = new LinkedHashMap<String, Channel>();
            channels.put("music", new Channel("Music", 42));
            channels.put("odd-id.1", new Channel("Game", 10));
            return LazyMap.of(channels, channels::get);
        }

        public boolean isConnected() {
            return true;
        }
    }

    @TemplateData
    public record Channel(@TemplateDoc("Name") String name, @TemplateDoc("Level, 0–100") long level) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static TemplateCatalog catalog() {
        var service = new TemplateService();
        service.engine = TemplateRendererTest.engine();
        service.core = new CoreTemplateVariables();
        service.namespaces = List.of(new TemplateNamespace() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public Class<?> rootType() {
                return Root.class;
            }

            @Override
            public Object root(TemplateScope scope) {
                return new Root();
            }
        });
        service.init();
        var catalog = new TemplateCatalog();
        catalog.templates = service;
        return catalog;
    }

    private final TemplateCatalog catalog = catalog();

    private static TemplateVariableDto find(List<TemplateVariableDto> items, String name) {
        return items.stream().filter(i -> i.name().equals(name)).findFirst().orElseThrow(() -> new AssertionError(name + " not in " + items));
    }

    @Test
    void rootsListCoreVariablesNamespacesAndFunctions() {
        var roots = catalog.children("", TemplateScope.EMPTY.withValue(42L));
        assertEquals("42", find(roots, "value").value());
        assertEquals("object", find(roots, "test").kind());
        assertEquals("Test integration", find(roots, "test").description());
        assertEquals("function", find(roots, "math:max").kind());
    }

    @Test
    void drillsIntoViewsAndMaps() {
        var test = catalog.children("test", TemplateScope.EMPTY);
        assertEquals("map", find(test, "channel").kind());
        assertEquals("true", find(test, "connected").value());

        var channels = catalog.children("test.channel", TemplateScope.EMPTY);
        assertEquals("Music", find(channels, "music").label());
        assertEquals("get('odd-id.1')", find(channels, "odd-id.1").insert());

        var game = catalog.children("test.channel.get('odd-id.1')", TemplateScope.EMPTY);
        assertEquals("10", find(game, "level").value());
        assertEquals("Level, 0–100", find(game, "level").description());
    }

    @Test
    void offersFunctionsAfterAValue() {
        var functions = catalog.children("value", TemplateScope.EMPTY.withValue(42L));
        assertTrue(functions.stream().anyMatch(f -> f.insert().equals("round(0)")), functions.toString());
        assertEquals(List.of(), catalog.children("nope.x", TemplateScope.EMPTY));
    }
}
