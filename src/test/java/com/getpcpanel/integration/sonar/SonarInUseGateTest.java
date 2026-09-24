package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.integration.analogbands.command.AnalogBand;
import com.getpcpanel.integration.analogbands.command.CommandAnalogBands;
import com.getpcpanel.integration.sonar.command.CommandSonarMute;
import com.getpcpanel.integration.sonar.command.CommandSonarVolume;
import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService.SaveEvent;

import org.junit.jupiter.api.Test;

/**
 * The in-use gate on the level read is derived from the save file (any profile of any device), not from which
 * controls the mute-colour layer happens to ask {@link SonarMuteResolver} to resolve — see
 * {@link SonarService#onSaveChanged}.
 */
class SonarInUseGateTest {
    private SonarService service() {
        var client = new SonarClient(Path.of("no-such-coreProps.json"));
        return SonarServiceFixtures.service(client, true);
    }

    private DeviceSave deviceWithProfile(Save save) {
        return new DeviceSave(save, "pcpanel", () -> null);
    }

    @Test
    void aSaveWithASonarDialIsInUse() {
        var service = service();
        var save = new Save();
        var deviceSave = deviceWithProfile(save);
        save.getDevices().put("serial1", deviceSave);
        var profile = deviceSave.getProfile("profile1").orElseThrow();
        profile.setDialData(0, new Commands(List.of(
                new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.monitoring, false, null)), null));

        service.onSaveChanged(new SaveEvent(save, false));

        assertTrue(service.isInUse());
    }

    @Test
    void aSaveWithASonarButtonIsInUse() {
        var service = service();
        var save = new Save();
        var deviceSave = deviceWithProfile(save);
        save.getDevices().put("serial1", deviceSave);
        var profile = deviceSave.getProfile("profile1").orElseThrow();
        profile.setButtonData(0, new Commands(List.of(
                new CommandSonarMute(SonarChannel.Game, SonarMixSelection.monitoring, MuteType.toggle)), null));

        service.onSaveChanged(new SaveEvent(save, false));

        assertTrue(service.isInUse());
    }

    @Test
    void aSonarCommandNestedInAnAnalogBandIsInUse() {
        var service = service();
        var save = new Save();
        var deviceSave = deviceWithProfile(save);
        save.getDevices().put("serial1", deviceSave);
        var profile = deviceSave.getProfile("profile1").orElseThrow();
        var bandCommands = new Commands(List.of(
                new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.monitoring, false, null)), null);
        var band = new AnalogBand(0, 100, null, bandCommands);
        // The band editor reuses the normal command picker, so a Sonar command can live inside a
        // stepped-switch dial's band rather than directly on the control.
        profile.setDialData(0, new Commands(List.of(new CommandAnalogBands(List.of(band))), null));

        service.onSaveChanged(new SaveEvent(save, false));

        assertTrue(service.isInUse());
    }

    @Test
    void aSaveWithNoSonarControlsIsNotInUse() {
        var service = service();
        var save = new Save();
        var deviceSave = deviceWithProfile(save);
        save.getDevices().put("serial1", deviceSave);

        service.onSaveChanged(new SaveEvent(save, false));

        assertFalse(service.isInUse());
    }

    @Test
    void removingTheLastSonarControlAndRecomputingClosesTheGate() {
        var service = service();
        var save = new Save();
        var deviceSave = deviceWithProfile(save);
        save.getDevices().put("serial1", deviceSave);
        var profile = deviceSave.getProfile("profile1").orElseThrow();
        profile.setDialData(0, new Commands(List.of(
                new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.monitoring, false, null)), null));
        service.onSaveChanged(new SaveEvent(save, false));
        assertTrue(service.isInUse(), "precondition: the gate opened while the control existed");

        profile.setDialData(0, Commands.EMPTY);
        service.onSaveChanged(new SaveEvent(save, false));

        assertFalse(service.isInUse());
    }

    @Test
    void theSaveChangedObserverIsWhatDrivesTheRecompute() {
        var service = service();
        assertFalse(service.isInUse(), "precondition: nothing has set the gate yet");
        var save = new Save();
        var deviceSave = deviceWithProfile(save);
        save.getDevices().put("serial1", deviceSave);
        var profile = deviceSave.getProfile("profile1").orElseThrow();
        profile.setDialData(0, new Commands(List.of(
                new CommandSonarVolume(SonarChannel.Game, SonarMixSelection.monitoring, false, null)), null));

        service.onSaveChanged(new SaveEvent(save, false));

        assertTrue(service.isInUse(), "the SaveEvent observer must recompute the gate from the save it carries");
    }
}
