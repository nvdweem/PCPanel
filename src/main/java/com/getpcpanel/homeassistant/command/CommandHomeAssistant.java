package com.getpcpanel.homeassistant.command;

import javax.annotation.Nullable;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.homeassistant.HomeAssistantService;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;

/**
 * Base for the Home Assistant commands. Holds the selected {@code server} id (blank means
 * auto-select the single configured server) and resolves the {@link HomeAssistantService} lazily so
 * the polymorphic, JSON-deserialised command instances stay plain data objects.
 */
@Getter
public abstract class CommandHomeAssistant extends Command {
    @Nullable protected final String server;

    protected CommandHomeAssistant(@Nullable String server) {
        this.server = server;
    }

    protected HomeAssistantService service() {
        return CdiHelper.getBean(HomeAssistantService.class);
    }
}
