package com.getpcpanel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandNoOp;
import com.getpcpanel.integration.analogbands.command.AnalogBand;
import com.getpcpanel.integration.analogbands.command.CommandAnalogBands;
import com.getpcpanel.integration.obs.command.CommandObs;
import com.getpcpanel.integration.obs.command.CommandObsSetScene;
import com.getpcpanel.integration.sonar.SonarMixSelection;
import com.getpcpanel.integration.sonar.command.CommandSonar;
import com.getpcpanel.integration.sonar.command.CommandSonarVolume;

import re.walk.sonar.model.SonarChannel;

class IntegrationUseNotifierTest {
    private static final class RecordingConnection implements IntegrationConnection {
        private final Class<? extends Command> owned;
        int used;

        RecordingConnection(Class<? extends Command> owned) {
            this.owned = owned;
        }

        @Override
        public boolean owns(Command command) {
            return owned.isInstance(command);
        }

        @Override
        public void onUsed() {
            used++;
        }
    }

    // Recreated per test: the suite runs with a per-class test instance lifecycle.
    private RecordingConnection obs;
    private RecordingConnection sonar;
    private IntegrationUseNotifier notifier;

    @BeforeEach
    void setUp() {
        obs = new RecordingConnection(CommandObs.class);
        sonar = new RecordingConnection(CommandSonar.class);
        notifier = new IntegrationUseNotifier(List.of(obs, sonar));
    }

    private static Commands commands(Command... commands) {
        return new Commands(List.of(commands), CommandsType.allAtOnce);
    }

    private static Command sonarVolume() {
        return new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.monitoring, false, null);
    }

    @Test
    void onlyTheIntegrationWhoseCommandRunsIsTold() {
        notifier.onUsed(commands(new CommandObsSetScene("Scene")));

        assertEquals(1, obs.used);
        assertEquals(0, sonar.used);
    }

    @Test
    void aControlWithSeveralCommandsOfOneIntegrationTellsItOnce() {
        notifier.onUsed(commands(new CommandObsSetScene("A"), new CommandObsSetScene("B")));

        assertEquals(1, obs.used);
    }

    @Test
    void aCommandNestedInAStepSwitchBandCountsAsUsed() {
        var bands = new CommandAnalogBands(List.of(
                new AnalogBand(0, 0.5, null, commands(new CommandNoOp())),
                new AnalogBand(0.5, 1, null, commands(sonarVolume()))));

        notifier.onUsed(commands(bands));

        assertEquals(1, sonar.used);
        assertEquals(0, obs.used);
    }

    @Test
    void aControlWithoutIntegrationCommandsTellsNobody() {
        notifier.onUsed(commands(new CommandNoOp()));

        assertEquals(0, obs.used);
        assertEquals(0, sonar.used);
    }
}
