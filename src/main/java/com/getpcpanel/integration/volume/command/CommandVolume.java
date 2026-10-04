package com.getpcpanel.integration.volume.command;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.getpcpanel.commands.ButtonFeedbackEvent;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.util.CdiHelper;

import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ToString(callSuper = true)
public abstract class CommandVolume extends Command {
    @JsonIgnore
    protected ISndCtrl getSndCtrl() {
        return CdiHelper.getBean(ISndCtrl.class);
    }

    /**
     * The overlay text {@code compute} works out before the action changes anything, or null outside a button press.
     * Cosmetic: a failure is logged and gives null, so it can never stop the action itself.
     */
    @Nullable
    protected static String feedback(Supplier<String> compute) {
        if (!ButtonFeedbackEvent.isButtonContext()) {
            return null;
        }
        try {
            return compute.get();
        } catch (RuntimeException e) {
            log.debug("Could not work out the button feedback", e);
            return null;
        }
    }

    /** Tells the overlay what the button did; a failure is logged, never thrown. */
    protected static void fireFeedback(@Nullable String text) {
        try {
            ButtonFeedbackEvent.forCurrentControl(text, null).ifPresent(CdiHelper::fire);
        } catch (RuntimeException e) {
            log.debug("Button feedback not delivered", e);
        }
    }
}
