package com.getpcpanel.integration.volume.command;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.getpcpanel.integration.volume.AppOutputRouter;
import com.getpcpanel.integration.volume.EverythingElse;
import com.getpcpanel.integration.volume.platform.AudioDevice;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/** Button action that makes the listed apps play on one output device, or on the default output when none is set. */
@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("volume.app-output")
@CommandMeta(label = "Send app to audio device", category = CommandCategory.audio, kinds = {CommandKind.button}, icon = "cable")
public class CommandVolumeAppOutput extends CommandVolume implements ButtonAction {
    private final List<String> processName;
    @Nullable private final String device;

    @JsonCreator
    public CommandVolumeAppOutput(@JsonProperty("processName") @Nullable List<String> processName, @JsonProperty("device") @Nullable String device) {
        this.processName = Objects.requireNonNullElseGet(processName, List::of);
        this.device = StringUtils.trimToNull(device);
    }

    @Override
    public void execute() {
        CdiHelper.getOptionalBean(AppOutputRouter.class).ifPresent(router -> {
            var apps = CdiHelper.getBean(EverythingElse.class).expand(processName);
            if (!apps.isEmpty() && router.route(apps, device) == null) {
                log.debug("Send app to audio device: none of {} is playing", apps);
            }
        });
    }

    @Override
    public String buildLabel() {
        var deviceName = device == null ? null : CdiHelper.getOptionalBean(ISndCtrl.class).map(s -> s.getDevice(device)).map(AudioDevice::name).orElse(device);
        return label(processName, deviceName);
    }

    static String label(List<String> apps, @Nullable String deviceName) {
        return String.join(", ", apps) + " → " + StringUtils.defaultIfBlank(deviceName, "Default");
    }
}
