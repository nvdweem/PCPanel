package com.getpcpanel.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.getpcpanel.util.ValueInterpolator;

import io.quarkus.qute.Engine;

class TemplateRendererTest {
    static Engine engine() {
        var builder = Engine.builder().addDefaults().strictRendering(false);
        TemplateFunctions.resolvers().forEach(builder::addValueResolver);
        TemplateFunctions.namespaceResolvers().forEach(builder::addNamespaceResolver);
        // Mirrors quarkus.qute.property-not-found-strategy=noop on the application's engine.
        builder.addResultMapper(new io.quarkus.qute.ResultMapper() {
            @Override
            public boolean appliesTo(io.quarkus.qute.TemplateNode.Origin origin, Object result) {
                return io.quarkus.qute.Results.isNotFound(result);
            }

            @Override
            public String map(Object result, io.quarkus.qute.Expression expression) {
                return "";
            }
        });
        return builder.build();
    }

    private static final Set<String> ROOTS = Set.of("value", "percent", "raw", "name", "muted", "wl", "device", "level");

    private final TemplateRenderer renderer = new TemplateRenderer(engine(), ROOTS);

    private String render(String source, Map<String, Object> data) {
        return renderer.render(source, data::get).output();
    }

    private static Map<String, Object> data() {
        var data = new HashMap<String, Object>();
        data.put("value", 50L);
        data.put("percent", 42L);
        data.put("raw", 107L);
        data.put("name", "Spotify");
        data.put("muted", true);
        data.put("wl", Map.of("channel", Map.of("music", Map.of("name", "Music", "level", 37L))));
        return data;
    }

    /** Stored before this syntax existed: only {{ value }} rendered, every other tag was text. */
    private String migrateLegacyValueField(String source) {
        return renderer.escapeTags(source, inner -> inner.equals("value"));
    }

    /**
     * Templates written for the old {{ value }} substitution, carried over by the load-time migration, render exactly
     * as ValueInterpolator did — and the migration is idempotent.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "{{ value }}",
            "{{value}}",
            "{{   value\t}}",
            "plain text without tags",
            "{\"brightness\": {{value}}, \"on\": true}",
            "action: light.turn_on\ntarget: {entity_id: light.kitchen}\ndata: {brightness_pct: {{ value }}}",
            "{\"value_template\": \"{{ value_json.state }}\", \"x\": {{ value }}}",
            "{{ states('sensor.x') | float }} {% if is_state('a','on') %}y{% endif %}",
            "{{ value | float }}",
            "{{ value | int }} and {{ value }}",
            "odd {|pipe|} and |} and \\{esc} and {name} and {#if} and {{ }} and {{ unclosed",
            "C:\\path\\{x} {{ value }}}",
            "http://host/set?v={{ value }}&name={name}",
            "{{ config:quarkus.http.port }} {{ inject:saveService }} {{ cdi:x }} {{ str:concat('a','b') }}",
            "{{ foo }} {{ value.class }} {{ name.getClass() }}",
            "{{#if}}{{/if}} {{#include foo}} {{#insert}}",
    })
    void legacyTemplatesRenderUnchanged(String source) {
        var migrated = migrateLegacyValueField(source);
        assertEquals(ValueInterpolator.interpolate(source, 50), render(migrated, data()));
        assertEquals(migrated, migrateLegacyValueField(migrated));
    }

    /** A field that was never templated keeps its text, {{ value }} included. */
    @ParameterizedTest
    @ValueSource(strings = {"home/{{ value }}/set", "{{ name }} {{#if muted}}x{{/if}}", "a {{ b }} c"})
    void newlyTemplatedFieldsKeepTheirText(String source) {
        var migrated = renderer.escapeTags(source, inner -> false);
        assertEquals(source, render(migrated, data()));
    }

    @Test
    void legacyFractionalValue() {
        var data = data();
        data.put("value", TemplateFunctions.normalize(12.5));
        assertEquals(ValueInterpolator.interpolate("v={{ value }}", 12.5), render("v={{ value }}", data));
        data.put("value", TemplateFunctions.normalize(50.0));
        assertEquals("v=50", render("v={{ value }}", data));
    }

    @Test
    void variablesAndOperators() {
        assertEquals("Spotify 42%", render("{{ name }} {{ percent }}%", data()));
        assertEquals("Muted", render("{{ muted ? 'Muted' : name }}", data()));
        assertEquals("Muted", render("{{#if muted}}Muted{{#else}}{{ name }}{{/if}}", data()));
        assertEquals("loud", render("{{#if percent > 40}}loud{{#else if percent > 10}}mid{{/if}}", data()));
        assertEquals("47", render("{{ percent + 5 }}", data()));
        assertEquals("Music 37", render("{{ wl.channel.music.name }} {{ wl.channel.get('music').level }}", data()));
    }

    @Test
    void missingValuesRenderEmpty() {
        var data = data();
        data.remove("muted");
        assertEquals("[] [] []", render("[{{ muted }}] [{{ wl.channel.nope.level }}] [{{ name.nope }}]", data));
        assertEquals("fallback", render("{{ muted ?: 'fallback' }}", data));
    }

    @Test
    void unknownRootsStayLiteral() {
        assertEquals("{{ missing ?: name }}", render("{{ missing ?: name }}", data()));
        var result = renderer.render("{{ nmae }} {{ name }}", data()::get);
        assertEquals("{{ nmae }} Spotify", result.output());
        assertEquals(List.of("{{ nmae }}"), result.literalTags());
    }

    @Test
    void functions() {
        var data = data();
        data.put("level", 37.456);
        assertEquals("37.5", render("{{ level.round(1) }}", data));
        assertEquals("37", render("{{ level.round(0) }}", data));
        assertEquals("10700", render("{{ raw * 100 }}", data));
        assertEquals("42", render("{{ math:max(percent, 10) }}", data));
        assertEquals("10", render("{{ math:min(percent, 10) }}", data));
        assertEquals("200", render("{{ math:max(raw, 200) }}", data));
        assertEquals("SPOTIFY", render("{{ name.upper }}", data));
        assertEquals("Spot…", render("{{ name.truncate(5) }}", data));
        assertEquals("0042", render("{{ percent.padStart(4, '0') }}", data));
        assertEquals("\"Spotify\"", render("{{ name.json }}", data));
        assertEquals("{{ name.nope(1) }}", render("{{ name.nope(1) }}", data));
        data.put("name", "My App");
        assertEquals("My+App", render("{{ name.urlencode }}", data));
        assertEquals("50", render("{{ raw.scale(0, 214, 0, 100) }}", data));
        assertEquals("37.46", render("{{ level.format('0.##') }}", data));
    }

    @Test
    void loopsAndLets() {
        var data = data();
        data.put("wl", Map.of("channel", new LinkedHashMap<>(Map.of("a", Map.of("name", "A")))));
        assertEquals("A;", render("{{#for ch in wl.channel.values}}{{ ch.name }};{{/for}}", data));
        assertEquals("43", render("{{#let x = percent}}{{ x + 1 }}{{/let}}", data));
    }

    @Test
    void unclosedSectionKeepsSectionTagsLiteral() {
        var result = renderer.render("{{#if muted}}Muted {{ name }}", data()::get);
        assertEquals("{{#if muted}}Muted Spotify", result.output());
        assertNotNull(result.error());
    }

    @Test
    void rootsAreResolvedLazilyAndOncePerRender() {
        var calls = new AtomicInteger();
        var result = renderer.render("{{ name }} {{ name }} {{#if false}}{{ wl.channel }}{{/if}}", key -> {
            calls.incrementAndGet();
            return "name".equals(key) ? "N" : null;
        });
        assertEquals("N N ", result.output());
        assertNull(result.error());
        assertEquals(1, calls.get());
    }

    @Test
    void lazyMapLooksUpOnlyRequestedKeys() {
        var lookups = new AtomicInteger();
        var source = new LinkedHashMap<String, String>();
        for (var i = 0; i < 1000; i++) {
            source.put("ch" + i, "Channel " + i);
        }
        var data = data();
        data.put("wl", Map.of("channel", LazyMap.of(source, key -> {
            lookups.incrementAndGet();
            return Map.of("name", source.get(key));
        })));
        assertEquals("Channel 500", render("{{ wl.channel.ch500.name }}", data));
        assertEquals(1, lookups.get());
    }
}
