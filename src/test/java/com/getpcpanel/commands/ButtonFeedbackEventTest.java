package com.getpcpanel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.getpcpanel.template.TemplateContext;
import com.getpcpanel.template.TemplateScope;

class ButtonFeedbackEventTest {
    @Test
    void anActionRunningForAButtonGetsAnEventForThatButton() {
        var event = inScope(new TemplateScope("serial-1", 4, true, null, null, null, null), "Spotify · Muted", null);

        assertEquals(Optional.of(new ButtonFeedbackEvent("serial-1", 4, "Spotify · Muted", null)), event);
    }

    @Test
    void anActionRunningForADialGetsNone() {
        assertTrue(inScope(new TemplateScope("serial-1", 4, false, null, null, null, null), "Spotify", null).isEmpty());
    }

    @Test
    void anActionRunningOutsideAControlGetsNone() {
        assertTrue(ButtonFeedbackEvent.forCurrentControl("Spotify", null).isEmpty());
        assertFalse(ButtonFeedbackEvent.isButtonContext());
    }

    @Test
    void blankTextNeedsALevel() {
        var button = new TemplateScope("serial-1", 4, true, null, null, null, null);

        assertTrue(inScope(button, " ", null).isEmpty());
        assertEquals(0.5f, inScope(button, "", 0.5f).orElseThrow().level());
    }

    private static Optional<ButtonFeedbackEvent> inScope(TemplateScope scope, String text, Float level) {
        var result = new AtomicReference<Optional<ButtonFeedbackEvent>>();
        TemplateContext.run(scope, () -> result.set(ButtonFeedbackEvent.forCurrentControl(text, level)));
        return result.get();
    }
}
