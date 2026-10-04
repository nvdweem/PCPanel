package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.integration.sonar.command.CommandSonarMute;
import com.getpcpanel.integration.sonar.command.CommandSonarVolume;
import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.template.TemplateScope;
import com.getpcpanel.template.TestTemplates;

import re.walk.sonar.SonarClient;
import re.walk.sonar.model.SonarChannel;
import re.walk.sonar.model.SonarLevel;
import re.walk.sonar.model.SonarMix;
import re.walk.sonar.model.SonarMode;
import re.walk.sonar.model.SonarRoute;
import re.walk.sonar.model.SonarState;

class SonarTemplateNamespaceTest {
    private static final Commands NONE = new Commands(List.of(), CommandsType.allAtOnce);

    private static SonarService service(SonarState state) {
        var service = SonarServiceFixtures.service(new SonarClient(Path.of("no-such-coreProps.json")), true);
        service.replaceState(state);
        return service;
    }

    private static SonarState streamer() {
        return new SonarState(SonarMode.stream, Map.of(
                SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game), new SonarLevel(0.42, false),
                SonarRoute.of(SonarMode.stream, SonarMix.streaming, SonarChannel.Game), new SonarLevel(0.8, true)));
    }

    private static String render(SonarState state, String source, Commands commands) {
        var namespace = new SonarTemplateNamespace();
        namespace.sonar = service(state);
        var scope = new TemplateScope(null, 0, false, commands, null, null, null);
        return TestTemplates.render(source, Map.of("sonar", namespace.root(scope)));
    }

    private static Commands of(Command command) {
        return new Commands(List.of(command), CommandsType.allAtOnce);
    }

    @Test
    void connectionAndMode() {
        assertEquals("true streamer", render(streamer(), "{{ sonar.connected }} {{ sonar.mode }}", NONE));
        assertEquals("false []", render(SonarState.UNKNOWN, "{{ sonar.connected }} [{{ sonar.mode }}]", NONE));
    }

    @Test
    void channelsAsPercentWithTheirPersonalMixByDefault() {
        assertEquals("Game 42 false", render(streamer(), "{{ sonar.channel.game }} {{ sonar.channel.game.level }} {{ sonar.channel.game.muted }}", NONE));
        assertEquals("Stream Mix 80 true",
                render(streamer(), "{{ sonar.channel.game.mix.stream }} {{ sonar.channel.game.mix.stream.level }} {{ sonar.channel.game.mix.stream.muted }}", NONE));
        assertEquals("[] []", render(streamer(), "[{{ sonar.channel.chat.level }}] [{{ sonar.channel.nope.level }}]", NONE));
    }

    @Test
    void classicModeHasOneLevelPerChannel() {
        var classic = new SonarState(SonarMode.classic, Map.of(
                SonarRoute.of(SonarMode.classic, SonarMix.monitoring, SonarChannel.Media), new SonarLevel(0.3, true)));

        assertEquals("classic 30 30 true",
                render(classic, "{{ sonar.mode }} {{ sonar.channel.media.level }} {{ sonar.channel.media.mix.stream.level }} {{ sonar.channel.media.mix.stream.muted }}", NONE));
    }

    @Test
    void targetFollowsTheControlsChannelAndMix() {
        assertEquals("Game (Stream Mix) 80 true", render(streamer(), "{{ sonar.target }} {{ sonar.target.level }} {{ sonar.target.muted }}",
                of(new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.streaming, false, null))));
        // Both mixes: muted only when both are, and the level shown is the Personal Mix's.
        assertEquals("Game (Both mixes) 42 false", render(streamer(), "{{ sonar.target }} {{ sonar.target.level }} {{ sonar.target.muted }}",
                of(new CommandSonarMute(SonarChannel.Game, SonarMixSelection.both, MuteType.toggle))));
        assertEquals("[]", render(streamer(), "[{{ sonar.target }}]", NONE));
    }
}
