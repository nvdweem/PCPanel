package com.getpcpanel.integration.sonar;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import com.getpcpanel.integration.sonar.command.CommandSonar;
import com.getpcpanel.template.LazyMap;
import com.getpcpanel.template.TemplateDoc;
import com.getpcpanel.template.TemplateNamespace;
import com.getpcpanel.template.TemplateScope;

import dev.niels.sonar.model.SonarChannel;
import dev.niels.sonar.model.SonarLevel;
import dev.niels.sonar.model.SonarMix;
import dev.niels.sonar.model.SonarMode;
import io.quarkus.qute.TemplateData;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * {@code {{ sonar.… }}}: SteelSeries Sonar's mode and channel levels, read from {@link SonarService}'s cached
 * snapshot. Levels are percentages. Channel levels are only held while a control uses a Sonar action.
 */
@ApplicationScoped
public class SonarTemplateNamespace implements TemplateNamespace {
    /** Template keys for {@code sonar.channel.<id>}. */
    private static final Map<String, SonarChannel> CHANNELS = new LinkedHashMap<>();
    /** Template keys for {@code sonar.channel.<id>.mix.<id>}, named after the mixes as GG shows them. */
    private static final Map<String, SonarMix> MIXES = Map.of("personal", SonarMix.monitoring, "stream", SonarMix.streaming);

    static {
        for (var channel : SonarChannel.values()) {
            CHANNELS.put(channel.name().toLowerCase(Locale.ROOT), channel);
        }
    }

    @Inject
    SonarService sonar;

    @Override
    public String name() {
        return "sonar";
    }

    @Override
    public Class<?> rootType() {
        return Root.class;
    }

    @Override
    public Object root(TemplateScope scope) {
        return new Root(sonar, scope);
    }

    @Nullable
    static Long percent(@Nullable Double level) {
        return level == null ? null : Math.round(level * 100);
    }

    @TemplateData
    @RegisterForReflection
    @TemplateDoc("SteelSeries Sonar: mode and channel levels")
    public static final class Root {
        private final SonarService sonar;
        private final TemplateScope scope;

        Root(SonarService sonar, TemplateScope scope) {
            this.sonar = sonar;
            this.scope = scope;
        }

        @TemplateDoc("Whether it is connected")
        public boolean isConnected() {
            return sonar.isReady();
        }

        @TemplateDoc("Mode: streamer or classic")
        @Nullable
        public String getMode() {
            var mode = sonar.snapshot().mode();
            return mode == null ? null : mode == SonarMode.classic ? "classic" : "streamer";
        }

        @TemplateDoc("Channels, by id (game, chat, mic, media, aux, master)")
        public LazyMap<ChannelView> getChannel() {
            return LazyMap.of(CHANNELS, id -> new ChannelView(sonar, CHANNELS.get(id)));
        }

        /** The channel this control's Sonar volume or mute action acts on, in the mix it targets. */
        @TemplateDoc("What this control acts on")
        @Nullable
        public TargetView getTarget() {
            var commands = scope.commands();
            var command = commands == null ? null : commands.getCommand(CommandSonar.class).orElse(null);
            if (command == null) {
                return null;
            }
            var selection = command.getMix();
            var level = sonar.level(command.getChannel(), selection.mixes().getFirst()).map(SonarLevel::volume).orElse(null);
            return new TargetView(command.getChannel().name() + " (" + selection.label() + ")", percent(level),
                    sonar.mutedOrNull(command.getChannel(), selection));
        }
    }

    /** A channel; its own level and mute are the Personal Mix's, which in Classic mode is the channel's only one. */
    @TemplateData
    @RegisterForReflection
    public static final class ChannelView {
        private final SonarService sonar;
        private final SonarChannel channel;

        ChannelView(SonarService sonar, SonarChannel channel) {
            this.sonar = sonar;
            this.channel = channel;
        }

        @TemplateDoc("Name")
        public String getName() {
            return channel.name();
        }

        @TemplateDoc("Level, 0–100")
        @Nullable
        public Long getLevel() {
            return new MixView(sonar, channel, SonarMix.monitoring).getLevel();
        }

        @TemplateDoc("Muted")
        @Nullable
        public Boolean getMuted() {
            return new MixView(sonar, channel, SonarMix.monitoring).getMuted();
        }

        @TemplateDoc("This channel in each mix (personal, stream)")
        public LazyMap<MixView> getMix() {
            return LazyMap.of(MIXES, id -> new MixView(sonar, channel, MIXES.get(id)));
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    @TemplateData
    @RegisterForReflection
    public static final class MixView {
        private final SonarService sonar;
        private final SonarChannel channel;
        private final SonarMix mix;

        MixView(SonarService sonar, SonarChannel channel, SonarMix mix) {
            this.sonar = sonar;
            this.channel = channel;
            this.mix = mix;
        }

        @TemplateDoc("Name")
        public String getName() {
            return mix.label();
        }

        @TemplateDoc("Level, 0–100")
        @Nullable
        public Long getLevel() {
            return percent(sonar.level(channel, mix).map(SonarLevel::volume).orElse(null));
        }

        @TemplateDoc("Muted")
        @Nullable
        public Boolean getMuted() {
            return sonar.mutedOrNull(channel, mix);
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    @TemplateData
    @RegisterForReflection
    public record TargetView(@TemplateDoc("Name") String name, @TemplateDoc("Level, 0–100") @Nullable Long level,
                             @TemplateDoc("Muted") @Nullable Boolean muted) {
        @Override
        public String toString() {
            return name;
        }
    }
}
