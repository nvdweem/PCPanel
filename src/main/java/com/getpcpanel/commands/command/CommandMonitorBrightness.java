package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.MainFX;
import com.getpcpanel.monitor.MonitorBrightnessService;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
@ToString(callSuper = true)
public class CommandMonitorBrightness extends Command implements DialAction {
    private final String monitorId;
    private final String monitorName;
    private final DialCommandParams dialParams;

    @JsonCreator
    public CommandMonitorBrightness(
            @JsonProperty("monitorId") String monitorId,
            @JsonProperty("monitorName") String monitorName,
            @JsonProperty("dialParams") DialCommandParams dialParams
    ) {
        this.monitorId = monitorId;
        this.monitorName = monitorName;
        this.dialParams = dialParams;
    }

    @Override
    public void execute(DialActionParameters context) {
        var value = Math.max(0, Math.min(100, context.dial().getValue(this)));
        MainFX.getBean(MonitorBrightnessService.class).setBrightness(monitorId, value);
    }

    @Override
    public String buildLabel() {
        if (monitorName != null && !monitorName.isBlank()) {
            return monitorName;
        }
        return monitorId == null ? "" : monitorId;
    }
}
