package com.getpcpanel.integration.volume.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.getpcpanel.util.os.FakeProcess;
import com.getpcpanel.util.os.ProcessHelper;

class LinuxAppOutputRouterTest {
    private static final String SINK_INPUTS = """
            Sink Input #42
            \tDriver: PipeWire
            \tSink: 1
            \tProperties:
            \t\tapplication.name = "Spotify"
            \t\tapplication.process.binary = "spotify"
            Sink Input #43
            \tDriver: PipeWire
            \tSink: 1
            \tProperties:
            \t\tapplication.name = "Firefox"
            \t\tapplication.process.binary = "firefox"
            Sink Input #44
            \tDriver: PipeWire
            \tSink: 1
            \tProperties:
            \t\tapplication.name = "Spotify"
            \t\tapplication.process.binary = "spotify"
            """;
    private static final String SINKS = """
            Sink #1
            \tName: alsa_output.pci
            \tDescription: Speakers
            Sink #2
            \tName: alsa_output.usb
            \tDescription: USB Headset
            """;
    private static final String INFO = """
            Server Name: PulseAudio (on PipeWire 1.0.5)
            Default Sink: alsa_output.pci
            Default Source: alsa_input.pci
            """;

    @TempDir Path dir;
    private List<String> issued;
    private LinuxAppOutputRouter sut;

    @BeforeEach
    void setUp() throws IOException {
        issued = Collections.synchronizedList(new ArrayList<>());
        var sinkInputs = Files.writeString(dir.resolve("sink-inputs"), SINK_INPUTS);
        var sinks = Files.writeString(dir.resolve("sinks"), SINKS);
        var info = Files.writeString(dir.resolve("info"), INFO);
        var empty = Files.writeString(dir.resolve("empty"), "");

        var wrapper = new PulseAudioWrapper();
        wrapper.processHelper = new ProcessHelper() {
            @Override
            public ProcessBuilder builder(String... command) {
                var line = String.join(" ", command);
                issued.add(line);
                var answer = switch (line) {
                    case "pactl list sink-inputs" -> sinkInputs;
                    case "pactl list sinks" -> sinks;
                    case "pactl info" -> info;
                    default -> empty;
                };
                return super.builder(FakeProcess.command("print-file", answer.toString()));
            }
        };
        sut = new LinuxAppOutputRouter();
        sut.pactl = wrapper;
    }

    private List<String> moves() {
        return issued.stream().filter(c -> c.startsWith("pactl move-sink-input")).toList();
    }

    @Test
    void movesEveryStreamOfTheAppToTheDevice() {
        var result = sut.route(Set.of("spotify.exe"), "alsa_output.usb");

        assertEquals(List.of("pactl move-sink-input 42 alsa_output.usb", "pactl move-sink-input 44 alsa_output.usb"), moves());
        assertEquals("USB Headset", result);
    }

    @Test
    void noDeviceMovesTheAppToTheDefaultOutput() {
        var result = sut.route(Set.of("spotify.exe"), null);

        assertEquals(List.of("pactl move-sink-input 42 alsa_output.pci", "pactl move-sink-input 44 alsa_output.pci"), moves());
        assertEquals("Speakers", result);
    }

    @Test
    void refusesAnInput() {
        assertNull(sut.route(Set.of("spotify.exe"), "in_alsa_input.pci"));
        assertEquals(List.of(), moves());
    }

    @Test
    void nothingWhenTheAppIsNotPlaying() {
        assertNull(sut.route(Set.of("vlc"), "alsa_output.usb"));
        assertEquals(List.of(), moves());
    }
}
