package com.getpcpanel.integration.wavelink;

import java.util.List;

import javax.annotation.Nullable;

import com.getpcpanel.integration.wavelink.command.CommandWaveLinkAddFocusToChannel;
import com.getpcpanel.integration.wavelink.command.CommandWaveLinkChange;
import com.getpcpanel.integration.wavelink.command.CommandWaveLinkChannelEffect;
import com.getpcpanel.template.LazyMap;
import com.getpcpanel.template.TemplateDoc;
import com.getpcpanel.template.TemplateNamespace;
import com.getpcpanel.template.TemplateScope;

import dev.niels.wavelink.impl.model.WaveLinkChannel;
import dev.niels.wavelink.impl.model.WaveLinkInput;
import dev.niels.wavelink.impl.model.WaveLinkInputDevice;
import dev.niels.wavelink.impl.model.WaveLinkMix;
import dev.niels.wavelink.impl.model.WaveLinkOutput;
import dev.niels.wavelink.impl.model.WaveLinkOutputDevice;
import io.quarkus.qute.TemplateData;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** {@code {{ wl.… }}}: Wave Link's live channels, mixes, inputs and outputs. Levels are percentages. */
@ApplicationScoped
public class WaveLinkTemplateNamespace implements TemplateNamespace {
    @Inject
    WaveLinkService waveLink;

    @Override
    public String name() {
        return "wl";
    }

    @Override
    public Class<?> rootType() {
        return Root.class;
    }

    @Override
    public Object root(TemplateScope scope) {
        return new Root(waveLink, scope);
    }

    @Nullable
    static Long percent(@Nullable Double level) {
        return level == null ? null : Math.round(level * 100);
    }

    @TemplateData
    @RegisterForReflection
    @TemplateDoc("Elgato Wave Link: channels, mixes, inputs and outputs")
    public static final class Root {
        private final WaveLinkService waveLink;
        private final TemplateScope scope;

        Root(WaveLinkService waveLink, TemplateScope scope) {
            this.waveLink = waveLink;
            this.scope = scope;
        }

        @TemplateDoc("Whether it is connected")
        public boolean isConnected() {
            return waveLink.isReady();
        }

        /** The application Wave Link reports as focused, by its Wave Link name. */
        @TemplateDoc("The focused application, by its Wave Link name")
        @Nullable
        public String getFocusApp() {
            var app = waveLink.getLastFocusApp();
            return app == null || app.isEmpty() ? null : app.name();
        }

        @TemplateDoc("Channels, by id")
        public LazyMap<ChannelView> getChannel() {
            return LazyMap.of(waveLink.getChannels(), id -> new ChannelView(waveLink.getChannels().get(id)));
        }

        @TemplateDoc("Mixes, by id")
        public LazyMap<MixView> getMix() {
            return LazyMap.of(waveLink.getMixes(), id -> new MixView(waveLink.getMixes().get(id)));
        }

        @TemplateDoc("Inputs, by id")
        public LazyMap<InputView> getInput() {
            return LazyMap.of(waveLink.getInputDevices(), id -> new InputView(waveLink.getInputDevices().get(id)));
        }

        @TemplateDoc("Outputs, by id")
        public LazyMap<OutputView> getOutput() {
            return LazyMap.of(waveLink.getOutputDevices(), id -> new OutputView(waveLink.getOutputDevices().get(id)));
        }

        /** What this control acts on: the channel, the channel-in-mix, the mix, the input or the output. */
        @TemplateDoc("What this control acts on")
        @Nullable
        public TargetView getTarget() {
            var commands = scope.commands();
            if (commands == null) {
                return null;
            }
            var change = commands.getCommand(CommandWaveLinkChange.class).orElse(null);
            if (change != null && change.getCommandType() != null) {
                return switch (change.getCommandType()) {
                    case Channel -> channelTarget(change.getId1());
                    case Mix -> {
                        var channel = waveLink.getChannels().get(change.getId1());
                        var mix = channel == null ? null : channel.findMix(change.getId2()).orElse(null);
                        var mixName = waveLink.getMixes().containsKey(change.getId2()) ? waveLink.getMixes().get(change.getId2()).name() : null;
                        yield channel == null || mix == null ? null
                                : new TargetView(channel.name() + (mixName == null ? "" : " (" + mixName + ")"), percent(mix.level()), mix.isMuted());
                    }
                    case MixMaster -> {
                        var mix = waveLink.getMixes().get(change.getId1());
                        yield mix == null ? null : new TargetView(mix.name(), percent(mix.level()), mix.isMuted());
                    }
                    case Input -> {
                        var device = waveLink.getInputDevices().get(change.getId1());
                        var input = device == null || device.inputs() == null || device.inputs().isEmpty() ? null : device.inputs().getFirst();
                        yield input == null ? null : new TargetView(device.name(), InputView.gain(input), input.isMuted());
                    }
                    case Output -> {
                        var device = waveLink.getOutputDevices().get(change.getId1());
                        var output = device == null || device.outputs() == null || device.outputs().isEmpty() ? null : device.outputs().getFirst();
                        yield output == null ? null : new TargetView(device.name(), percent(output.level()), output.isMuted());
                    }
                };
            }
            var effect = commands.getCommand(CommandWaveLinkChannelEffect.class).map(CommandWaveLinkChannelEffect::getChannelId);
            var addFocus = commands.getCommand(CommandWaveLinkAddFocusToChannel.class).map(CommandWaveLinkAddFocusToChannel::getId);
            return effect.or(() -> addFocus).map(this::channelTarget).orElse(null);
        }

        @Nullable
        private TargetView channelTarget(@Nullable String channelId) {
            var channel = channelId == null ? null : waveLink.getChannels().get(channelId);
            return channel == null ? null : new TargetView(channel.name(), percent(channel.level()), channel.isMuted());
        }
    }

    @TemplateData
    @RegisterForReflection
    public record TargetView(@TemplateDoc("Name") @Nullable String name, @TemplateDoc("Level, 0–100") @Nullable Long level, @TemplateDoc("Muted") @Nullable Boolean muted) {
        @Override
        public String toString() {
            return name == null ? "" : name;
        }
    }

    @TemplateData
    @RegisterForReflection
    public static final class ChannelView {
        private final WaveLinkChannel channel;

        ChannelView(WaveLinkChannel channel) {
            this.channel = channel;
        }

        @TemplateDoc("Id")
        public String getId() {
            return channel.id();
        }

        @TemplateDoc("Name")
        @Nullable
        public String getName() {
            return channel.name();
        }

        @TemplateDoc("Channel type")
        @Nullable
        public String getType() {
            return channel.type();
        }

        @TemplateDoc("Level, 0–100")
        @Nullable
        public Long getLevel() {
            return percent(channel.level());
        }

        @TemplateDoc("Muted")
        @Nullable
        public Boolean getMuted() {
            return channel.isMuted();
        }

        /** This channel's level and mute in each mix, by mix id. */
        @TemplateDoc("This channel's level and mute in each mix, by mix id")
        public LazyMap<MixView> getMix() {
            var byId = new java.util.LinkedHashMap<String, WaveLinkMix>();
            channel.mixes().forEach(m -> byId.put(m.id(), m));
            return LazyMap.of(byId, id -> new MixView(byId.get(id)));
        }

        /** Names of the applications routed to this channel. */
        @TemplateDoc("Applications routed to this channel")
        public List<String> getApps() {
            return channel.apps().stream().map(a -> a.name()).toList();
        }

        @Override
        public String toString() {
            return getName() == null ? "" : getName();
        }
    }

    @TemplateData
    @RegisterForReflection
    public static final class MixView {
        private final WaveLinkMix mix;

        MixView(WaveLinkMix mix) {
            this.mix = mix;
        }

        @TemplateDoc("Id")
        public String getId() {
            return mix.id();
        }

        @TemplateDoc("Name")
        @Nullable
        public String getName() {
            return mix.name();
        }

        @TemplateDoc("Level, 0–100")
        @Nullable
        public Long getLevel() {
            return percent(mix.level());
        }

        @TemplateDoc("Muted")
        @Nullable
        public Boolean getMuted() {
            return mix.isMuted();
        }

        @Override
        public String toString() {
            return getName() == null ? "" : getName();
        }
    }

    @TemplateData
    @RegisterForReflection
    public static final class InputView {
        private final WaveLinkInputDevice device;

        InputView(WaveLinkInputDevice device) {
            this.device = device;
        }

        @Nullable
        private WaveLinkInput first() {
            return device.inputs() == null || device.inputs().isEmpty() ? null : device.inputs().getFirst();
        }

        @Nullable
        static Long gain(@Nullable WaveLinkInput input) {
            return input == null || input.gain() == null ? null : percent(input.gain().value());
        }

        @TemplateDoc("Id")
        public String getId() {
            return device.id();
        }

        @TemplateDoc("Name")
        @Nullable
        public String getName() {
            return device.name();
        }

        @TemplateDoc("Gain, 0–100")
        @Nullable
        public Long getGain() {
            return gain(first());
        }

        @TemplateDoc("Muted")
        @Nullable
        public Boolean getMuted() {
            var input = first();
            return input == null ? null : input.isMuted();
        }

        @Override
        public String toString() {
            return getName() == null ? "" : getName();
        }
    }

    @TemplateData
    @RegisterForReflection
    public static final class OutputView {
        private final WaveLinkOutputDevice device;

        OutputView(WaveLinkOutputDevice device) {
            this.device = device;
        }

        @Nullable
        private WaveLinkOutput first() {
            return device.outputs() == null || device.outputs().isEmpty() ? null : device.outputs().getFirst();
        }

        @TemplateDoc("Id")
        public String getId() {
            return device.id();
        }

        @TemplateDoc("Name")
        @Nullable
        public String getName() {
            return device.name();
        }

        @TemplateDoc("Level, 0–100")
        @Nullable
        public Long getLevel() {
            var output = first();
            return output == null ? null : percent(output.level());
        }

        @TemplateDoc("Muted")
        @Nullable
        public Boolean getMuted() {
            var output = first();
            return output == null ? null : output.isMuted();
        }

        @Override
        public String toString() {
            return getName() == null ? "" : getName();
        }
    }
}
