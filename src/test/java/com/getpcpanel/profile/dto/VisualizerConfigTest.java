package com.getpcpanel.profile.dto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.AppLikeMapper;

class VisualizerConfigTest {
    private final ObjectMapper mapper = AppLikeMapper.build();

    private VisualizerConfig read(String json) throws Exception {
        return mapper.readValue(json, VisualizerConfig.class);
    }

    @Test
    void savedAppsBecomeOneSourceEach() throws Exception {
        var cfg = read("{\"when\":\"PLAYING\",\"apps\":[\"spotify.exe\",\"com.spotify.Client\"]}");
        assertArrayEquals(new VisualizerSource[] { VisualizerSource.app("spotify.exe"), VisualizerSource.app("com.spotify.Client") }, cfg.getSources());
    }

    @Test
    void noSavedAppsIsWhateverPlaysLoudest() throws Exception {
        assertArrayEquals(new VisualizerSource[] { VisualizerSource.anyApp() }, read("{\"when\":\"PLAYING\",\"apps\":[]}").getSources());
        assertArrayEquals(new VisualizerSource[] { VisualizerSource.anyApp() }, read("{\"when\":\"ALWAYS\"}").getSources());
    }

    @Test
    void sourcesWinOverAppsAndAppsIsNeverWritten() throws Exception {
        var cfg = read("{\"when\":\"PLAYING\",\"apps\":[\"a.exe\"],\"sources\":[{\"kind\":\"INPUT\",\"device\":\"mic\"},{\"kind\":\"OUTPUT\"}]}");
        assertArrayEquals(new VisualizerSource[] { VisualizerSource.input("mic"), VisualizerSource.output(null) }, cfg.getSources());
        var json = mapper.writeValueAsString(cfg);
        assertFalse(json.contains("\"apps\""), json);
        assertTrue(json.contains("\"sources\""), json);
    }

    @Test
    void aMigratedConfigSavesItsSources() throws Exception {
        var json = mapper.writeValueAsString(read("{\"when\":\"PLAYING\",\"apps\":[\"a.exe\"]}"));
        assertArrayEquals(new VisualizerSource[] { VisualizerSource.app("a.exe") }, read(json).getSources());
        assertFalse(json.contains("\"apps\""), json);
    }

    @Test
    void aNewVisualizerListensToTheDefaultOutput() {
        assertArrayEquals(new VisualizerSource[] { VisualizerSource.output(null) }, VisualizerConfig.create().getSources());
    }

    @Test
    void aCopyKeepsTheSources() throws Exception {
        var cfg = read("{\"when\":\"PLAYING\",\"apps\":[\"a.exe\"]}");
        assertArrayEquals(cfg.getSources(), cfg.copy().getSources());
    }
}
