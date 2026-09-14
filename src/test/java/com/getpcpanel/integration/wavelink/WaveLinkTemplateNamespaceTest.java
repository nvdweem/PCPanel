package com.getpcpanel.integration.wavelink;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.integration.wavelink.command.CommandWaveLinkChangeLevel;
import com.getpcpanel.integration.wavelink.command.CommandWaveLinkChangeMute;
import com.getpcpanel.integration.wavelink.command.WaveLinkCommandTarget;
import com.getpcpanel.template.TemplateScope;
import com.getpcpanel.template.TestTemplates;

import dev.niels.wavelink.impl.model.WaveLinkChannel;
import dev.niels.wavelink.impl.model.WaveLinkMix;

class WaveLinkTemplateNamespaceTest {
    private static WaveLinkService service() {
        var service = new WaveLinkService();
        service.getChannels().put("music", new WaveLinkChannel("music", "Music", null,
                List.of(new WaveLinkMix("personal", null, 0.25, true, null)), 0.42, false, null, null, null));
        service.getChannels().put("odd-id.1", new WaveLinkChannel("odd-id.1", "Game", null, null, 0.1, true, null, null, null));
        service.getMixes().put("personal", new WaveLinkMix("personal", "Personal Mix", 0.8, false, null));
        return service;
    }

    private static String render(String source, Commands commands) {
        var namespace = new WaveLinkTemplateNamespace();
        namespace.waveLink = service();
        var scope = new TemplateScope(null, 0, false, commands, null, null, null);
        return TestTemplates.render(source, Map.of("wl", namespace.root(scope)));
    }

    private static Commands of(com.getpcpanel.commands.command.Command command) {
        return new Commands(List.of(command), CommandsType.allAtOnce);
    }

    @Test
    void channelsMixesAndLevelsAsPercent() {
        var none = new Commands(List.of(), CommandsType.allAtOnce);
        assertEquals("Music 42 false", render("{{ wl.channel.music.name }} {{ wl.channel.music.level }} {{ wl.channel.music.muted }}", none));
        assertEquals("Game 10", render("{{ wl.channel.get('odd-id.1') }} {{ wl.channel.get('odd-id.1').level }}", none));
        assertEquals("25 true", render("{{ wl.channel.music.mix.personal.level }} {{ wl.channel.music.mix.personal.muted }}", none));
        assertEquals("Personal Mix 80", render("{{ wl.mix.personal.name }} {{ wl.mix.personal.level }}", none));
        assertEquals("[]", render("[{{ wl.channel.nope.level }}]", none));
    }

    @Test
    void targetFollowsTheControlsCommand() {
        assertEquals("Music 42", render("{{ wl.target }} {{ wl.target.level }}", of(new CommandWaveLinkChangeLevel(WaveLinkCommandTarget.Channel, "music", null, null))));
        assertEquals("Music (Personal Mix) 25 true",
                render("{{ wl.target }} {{ wl.target.level }} {{ wl.target.muted }}", of(new CommandWaveLinkChangeMute(WaveLinkCommandTarget.Mix, "music", "personal", MuteType.toggle))));
        assertEquals("Personal Mix 80", render("{{ wl.target }} {{ wl.target.level }}", of(new CommandWaveLinkChangeLevel(WaveLinkCommandTarget.MixMaster, "personal", null, null))));
    }
}
