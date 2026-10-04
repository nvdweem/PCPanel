package com.getpcpanel.integration.volume.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.ButtonFeedbackEvent;
import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.commands.IconService;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.integration.volume.command.CommandVolumeDefaultDevice;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.template.TemplateContext;
import com.getpcpanel.template.TemplateScope;

class OverlayButtonFeedbackTest {
    private static final String SERIAL = "serial-1";

    private final List<OverlayContent> shown = new ArrayList<>();
    private Save save;
    private Overlay overlay;

    @BeforeEach
    void setUp() {
        shown.clear();
        save = new Save();
        save.setOverlayEnabled(true);
        var saveService = mock(SaveService.class);
        when(saveService.get()).thenReturn(save);
        overlay = new Overlay(saveService, mock(IconService.class), null, null, null, null, null, null, new CapturingWindow());
    }

    @Test
    void buttonFeedbackIsOnByDefault() {
        assertTrue(new Save().isOverlayButtonFeedback());
    }

    @Test
    void showsTheTextOfWhatTheButtonDid() {
        overlay.onButtonFeedback(new ButtonFeedbackEvent(SERIAL, 2, "Spotify · Muted", null));

        assertEquals(1, shown.size());
        assertEquals("Spotify · Muted", shown.getFirst().name());
        assertEquals(-1f, shown.getFirst().value());
    }

    @Test
    void showsALevelAsTheBar() {
        overlay.onButtonFeedback(new ButtonFeedbackEvent(SERIAL, 2, "Spotify", 0.3f));

        assertEquals(0.3f, shown.getFirst().value());
    }

    @Test
    void showsNothingWhenButtonFeedbackIsOff() {
        save.setOverlayButtonFeedback(false);

        overlay.onButtonFeedback(new ButtonFeedbackEvent(SERIAL, 2, "Spotify · Muted", null));

        assertTrue(shown.isEmpty());
    }

    @Test
    void showsNothingWhenTheOverlayIsOff() {
        save.setOverlayEnabled(false);

        overlay.onButtonFeedback(new ButtonFeedbackEvent(SERIAL, 2, "Spotify · Muted", null));

        assertTrue(shown.isEmpty());
    }

    @Test
    void aTypedOverlayTextOnTheButtonWins() {
        var commands = new Commands(List.of(new TypedOverlayText("Mic off")), CommandsType.allAtOnce);

        TemplateContext.run(buttonScope(2, commands),
                () -> overlay.onButtonFeedback(new ButtonFeedbackEvent(SERIAL, 2, "Spotify · Muted", null)));

        assertTrue(shown.isEmpty());
    }

    @Test
    void aTypedOverlayTextOnAnotherButtonDoesNotSuppress() {
        var commands = new Commands(List.of(new TypedOverlayText("Mic off")), CommandsType.allAtOnce);

        TemplateContext.run(buttonScope(3, commands),
                () -> overlay.onButtonFeedback(new ButtonFeedbackEvent(SERIAL, 2, "Spotify · Muted", null)));

        assertEquals(1, shown.size());
    }

    @Test
    void theDefaultDeviceNameIsNotATypedOverlayText() {
        // Set default device derives its overlay text from the device it switches to; that is what the feedback shows.
        var commands = new Commands(List.of(new DefaultDeviceWithName()), CommandsType.allAtOnce);

        TemplateContext.run(buttonScope(2, commands),
                () -> overlay.onButtonFeedback(new ButtonFeedbackEvent(SERIAL, 2, "Default: Headphones", null)));

        assertEquals("Default: Headphones", shown.getFirst().name());
    }

    private static TemplateScope buttonScope(int button, Commands commands) {
        return new TemplateScope(SERIAL, button, true, commands, null, null, null);
    }

    private class CapturingWindow extends NoOpOverlayWindow {
        @Override
        public void show(OverlayContent content) {
            shown.add(content);
        }
    }

    private static final class TypedOverlayText extends Command implements ButtonAction {
        private final String text;

        TypedOverlayText(String text) {
            this.text = text;
        }

        @Override
        public void execute() {
        }

        @Override
        public @Nullable String getOverlayText() {
            return text;
        }

        @Override
        public String buildLabel() {
            return "";
        }
    }

    private static final class DefaultDeviceWithName extends CommandVolumeDefaultDevice {
        DefaultDeviceWithName() {
            super("device-1");
        }

        @Override
        public @Nullable String getOverlayText() {
            return "Headphones";
        }
    }
}
