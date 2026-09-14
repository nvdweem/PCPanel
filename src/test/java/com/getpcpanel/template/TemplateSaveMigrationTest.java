package com.getpcpanel.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.getpcpanel.util.ValueInterpolator;

class TemplateSaveMigrationTest {
    private static TemplateNamespace namespace(String name) {
        return new TemplateNamespace() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Class<?> rootType() {
                return Object.class;
            }

            @Override
            public Object root(TemplateScope scope) {
                return null;
            }
        };
    }

    private static TemplateService service() {
        var service = new TemplateService();
        service.engine = TemplateRendererTest.engine();
        service.core = new CoreTemplateVariables();
        service.namespaces = List.of(namespace("wl"), namespace("audio"), namespace("obs"), namespace("vm"), namespace("discord"), namespace("mqtt"), namespace("ha"));
        service.init();
        return service;
    }

    private final TemplateService service = service();
    private final TemplateSaveMigration migration = migration(service);

    private static TemplateSaveMigration migration(TemplateService service) {
        var migration = new TemplateSaveMigration();
        migration.templates = service;
        return migration;
    }

    private static final String SAVE = """
            {
              "devices": {
                "serial": {
                  "profiles": [ {
                    "dialData": {
                      "0": { "commands": [ {
                        "_type": "output.http-request", "method": "POST",
                        "url": "http://host/{{ value }}?who={{ name }}",
                        "headers": "X-Level: {{value}}", "body": "{\\"v\\": {{ value }}, \\"t\\": \\"{{ value_json.x }}\\"}"
                      } ] },
                      "1": { "commands": [ {
                        "_type": "com.getpcpanel.commands.command.CommandMqttPublish",
                        "topic": "home/{{ value }}/set", "payload": "{{ value }} {{ device.name }}"
                      } ] },
                      "2": { "commands": [ {
                        "_type": "analog.bands",
                        "bands": [ { "commands": { "commands": [ { "_type": "osc.send", "address": "/{{ control }}" } ] } } ]
                      } ] }
                    },
                    "buttonData": {
                      "0": { "commands": [ { "_type": "homeassistant.action", "action": "data: {{ states('x') }} {{ profile }}" } ] }
                    },
                    "knobSettings": { "0": { "overlayName": "{{ name }} plain" }, "1": { "overlayName": "Music" } }
                  } ]
                }
              }
            }
            """;

    private ObjectNode document() throws Exception {
        return (ObjectNode) new ObjectMapper().readTree(SAVE);
    }

    private String render(String template, double value) {
        return service.render(template, TemplateScope.EMPTY.withValue(TemplateFunctions.normalize(value)), () -> "FAILED");
    }

    @Test
    void rewritesTagsThatWouldNowRenderAndKeepsValue() throws Exception {
        var doc = document();
        assertTrue(migration.migrate(doc));
        var profile = doc.at("/devices/serial/profiles/0");

        var http = profile.at("/dialData/0/commands/0");
        assertEquals("http://host/{{ value }}?who={{ '{' }}{ name }}", http.path("url").asText());
        assertEquals("X-Level: {{value}}", http.path("headers").asText());
        var mqtt = profile.at("/dialData/1/commands/0");
        assertEquals("home/{{ '{' }}{ value }}/set", mqtt.path("topic").asText());
        assertEquals("{{ value }} {{ '{' }}{ device.name }}", mqtt.path("payload").asText());
        assertEquals("/{{ '{' }}{ control }}", profile.at("/dialData/2/commands/0/bands/0/commands/commands/0/address").asText());
        assertEquals("data: {{ states('x') }} {{ '{' }}{ profile }}", profile.at("/buttonData/0/commands/0/action").asText());
        assertEquals("{{ '{' }}{ name }} plain", profile.at("/knobSettings/0/overlayName").asText());
        assertEquals("Music", profile.at("/knobSettings/1/overlayName").asText());
        assertEquals(TemplateSaveMigration.CURRENT_VERSION, doc.path(TemplateSaveMigration.VERSION_FIELD).asInt());
    }

    @Test
    void migratedFieldsRenderWhatTheyProducedBefore() throws Exception {
        var original = document();
        var doc = document();
        migration.migrate(doc);
        var before = original.at("/devices/serial/profiles/0");
        var after = doc.at("/devices/serial/profiles/0");
        for (var value : new double[] { 0, 12.5, 100 }) {
            for (var path : List.of("/dialData/0/commands/0/url", "/dialData/0/commands/0/body", "/dialData/1/commands/0/payload")) {
                assertEquals(ValueInterpolator.interpolate(before.at(path).asText(), value), render(after.at(path).asText(), value), path);
            }
            for (var path : List.of("/dialData/1/commands/0/topic", "/buttonData/0/commands/0/action", "/knobSettings/0/overlayName")) {
                assertEquals(before.at(path).asText(), render(after.at(path).asText(), value), path);
            }
        }
    }

    @Test
    void runsOnlyOnce() throws Exception {
        var doc = document();
        migration.migrate(doc);
        var once = doc.toString();
        doc.withObject("/devices/serial/profiles/0/knobSettings/1").put("overlayName", "{{ name }} written after the upgrade");
        assertFalse(migration.migrate(doc));
        assertEquals("{{ name }} written after the upgrade", doc.at("/devices/serial/profiles/0/knobSettings/1/overlayName").asText());
        assertTrue(once.contains("templateVersion"));
    }
}
