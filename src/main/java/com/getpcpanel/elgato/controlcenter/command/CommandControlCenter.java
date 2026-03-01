package com.getpcpanel.elgato.controlcenter.command;

import javax.annotation.Nullable;

import com.getpcpanel.MainFX;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.elgato.controlcenter.ControlCenterService;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@RequiredArgsConstructor
public abstract class CommandControlCenter extends Command {
    @Nullable private final String id;

    protected ControlCenterService getControlCenterService() {
        return MainFX.getBean(ControlCenterService.class);
    }
}
