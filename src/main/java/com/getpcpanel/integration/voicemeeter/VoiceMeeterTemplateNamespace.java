package com.getpcpanel.integration.voicemeeter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.getpcpanel.integration.voicemeeter.Voicemeeter.ControlType;
import com.getpcpanel.integration.voicemeeter.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.integration.voicemeeter.command.CommandVoiceMeeterAdvancedButton;
import com.getpcpanel.integration.voicemeeter.command.CommandVoiceMeeterBasic;
import com.getpcpanel.integration.voicemeeter.command.CommandVoiceMeeterBasicButton;
import com.getpcpanel.template.TemplateNamespace;
import com.getpcpanel.template.TemplateScope;

import io.quarkus.qute.TemplateData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** {@code {{ vm.… }}}: VoiceMeeter strips and buses ({@code vm.strip.get(0).gain}); gains are in dB. */
@ApplicationScoped
public class VoiceMeeterTemplateNamespace implements TemplateNamespace {
    private static final Pattern ADVANCED = Pattern.compile("^(Strip|Bus)\\[(\\d+)]", Pattern.CASE_INSENSITIVE);

    @Inject
    Voicemeeter voicemeeter;

    @Override
    public String name() {
        return "vm";
    }

    @Override
    public Class<?> rootType() {
        return Root.class;
    }

    @Override
    public Object root(TemplateScope scope) {
        return new Root(voicemeeter, scope);
    }

    @TemplateData
    public static final class Root {
        private final Voicemeeter vm;
        private final TemplateScope scope;

        Root(Voicemeeter vm, TemplateScope scope) {
            this.vm = vm;
            this.scope = scope;
        }

        public boolean isConnected() {
            return vm.isConnected();
        }

        public List<ControlView> getStrip() {
            return controls(ControlType.STRIP, vm.isConnected() ? vm.getNumStrips() : 0);
        }

        public List<ControlView> getBus() {
            return controls(ControlType.BUS, vm.isConnected() ? vm.getNumBuses() : 0);
        }

        private List<ControlView> controls(ControlType type, int count) {
            var list = new ArrayList<ControlView>(count);
            for (var i = 0; i < count; i++) {
                list.add(new ControlView(vm, type, i));
            }
            return list;
        }

        /** The strip or bus this control's VoiceMeeter action acts on. */
        @Nullable
        public ControlView getTarget() {
            var commands = scope.commands();
            if (commands == null || !vm.isConnected()) {
                return null;
            }
            var basic = commands.getCommand(CommandVoiceMeeterBasic.class);
            if (basic.isPresent()) {
                return new ControlView(vm, basic.get().getCt(), basic.get().getIndex());
            }
            var basicButton = commands.getCommand(CommandVoiceMeeterBasicButton.class);
            if (basicButton.isPresent()) {
                return new ControlView(vm, basicButton.get().getCt(), basicButton.get().getIndex());
            }
            var param = commands.getCommand(CommandVoiceMeeterAdvanced.class).map(CommandVoiceMeeterAdvanced::getFullParam)
                                .or(() -> commands.getCommand(CommandVoiceMeeterAdvancedButton.class).map(CommandVoiceMeeterAdvancedButton::getFullParam))
                                .orElse(null);
            var matcher = param == null ? null : ADVANCED.matcher(param);
            if (matcher == null || !matcher.find()) {
                return null;
            }
            return new ControlView(vm, ControlType.fromDn(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        }
    }

    @TemplateData
    public static final class ControlView {
        private final Voicemeeter vm;
        private final ControlType type;
        private final int index;

        ControlView(Voicemeeter vm, @Nullable ControlType type, int index) {
            this.vm = vm;
            this.type = type == null ? ControlType.STRIP : type;
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

        @Nullable
        public String getLabel() {
            return vm.readString(vm.makeParameterString(type, index, "Label"));
        }

        /** Gain in dB, one decimal. */
        @Nullable
        public Double getGain() {
            var gain = vm.readFloat(vm.makeParameterString(type, index, "Gain"));
            return gain == null ? null : Math.round(gain * 10) / 10d;
        }

        @Nullable
        public Boolean getMuted() {
            var mute = vm.readFloat(vm.makeParameterString(type, index, "Mute"));
            return mute == null ? null : mute > 0;
        }

        @Override
        public String toString() {
            var label = getLabel();
            return label == null || label.isBlank() ? type.getName() + " " + (index + 1) : label;
        }
    }
}
