package com.getpcpanel.integration.volume;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.integration.volume.command.CommandVolumeDevice;
import com.getpcpanel.integration.volume.command.CommandVolumeProcess;
import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.template.TemplateScope;
import com.getpcpanel.template.TestTemplates;

class AudioTemplateNamespaceTest {
    private static final AudioDevice SPEAKERS = new AudioDevice(null, "Speakers", "spk") {
        {
            volume(0.5f);
            muted(false);
        }
    };
    private static final AudioSession SPOTIFY = new AudioSession(null, 42, new File("C:\\Apps\\Spotify.exe"), "Spotify", null, 0.3f, true);

    private static ISndCtrl snd() {
        return (ISndCtrl) Proxy.newProxyInstance(ISndCtrl.class.getClassLoader(), new Class<?>[] { ISndCtrl.class }, (proxy, method, args) -> switch (method.getName()) {
            case "getDevicesMap" -> Map.of("spk", SPEAKERS);
            case "getDevice" -> "spk".equals(args[0]) ? SPEAKERS : null;
            case "getAllSessions" -> List.of(SPOTIFY);
            case "defaultPlayer" -> "spk";
            case "defaultDeviceOnEmpty" -> args[0] == null || ((String) args[0]).isBlank() ? "spk" : args[0];
            default -> null;
        });
    }

    private static String render(String source, Command... commands) {
        var scope = new TemplateScope(null, 0, false, new Commands(List.of(commands), CommandsType.allAtOnce), null, null, null);
        return TestTemplates.render(source, Map.of("audio", new AudioTemplateNamespace.Root(snd(), scope)));
    }

    @Test
    void devicesAndApps() {
        assertEquals("Speakers 50 false", render("{{ audio.device.spk }} {{ audio.device.spk.volume }} {{ audio.device.spk.muted }}"));
        assertEquals("Speakers", render("{{ audio.defaultOutput.name }}"));
        assertEquals("Spotify 30 true", render("{{ audio.app.spotify }} {{ audio.app.spotify.volume }} {{ audio.app.spotify.muted }}"));
    }

    @Test
    void targetFollowsTheControlsCommand() {
        assertEquals("Spotify 30", render("{{ audio.target }} {{ audio.target.volume }}", new CommandVolumeProcess(List.of("spotify.exe"), "", false, DialCommandParams.DEFAULT)));
        assertEquals("Speakers 50", render("{{ audio.target }} {{ audio.target.volume }}", new CommandVolumeDevice("", false, DialCommandParams.DEFAULT)));
    }
}
