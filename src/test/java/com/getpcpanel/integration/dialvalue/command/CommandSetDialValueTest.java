package com.getpcpanel.integration.dialvalue.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.AppLikeMapper;
import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DeviceAction.DeviceActionParameters;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.commands.command.LevelReadable;
import com.getpcpanel.integration.volume.command.CommandVolumeProcess;
import com.getpcpanel.template.TemplateContext;
import com.getpcpanel.template.TemplateScope;

class CommandSetDialValueTest {
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = AppLikeMapper.build();
    }

    @Test
    void withoutToggleAlwaysSetsTheValue() {
        var c = new CommandSetDialValue(40, null, false);
        assertEquals(40, c.nextValue(70));
        assertEquals(40, c.nextValue(40));
    }

    @Test
    void toggleRemembersTheLevelAndRestoresItOnTheNextPress() {
        var c = new CommandSetDialValue(0, null, true);
        assertEquals(0, c.nextValue(80));   // first press: remember 80, go to 0
        assertEquals(80, c.nextValue(0));   // second press: back to 80
        assertEquals(0, c.nextValue(55));   // third press: remember 55 again
    }

    @Test
    void toggleWithoutReadableLevelJustSets() {
        var c = new CommandSetDialValue(30, null, true);
        assertEquals(30, c.nextValue(null));
        assertEquals(30, c.nextValue(null));
    }

    @Test
    void valueIsClampedToAPercentage() {
        assertEquals(100, new CommandSetDialValue(150, null, false).getValue());
        assertEquals(0, new CommandSetDialValue(-5, null, false).getValue());
    }

    @Test
    void runsEveryNestedDialActionAtTheValue() {
        var seen = new ArrayList<Integer>();
        var c = new CommandSetDialValue(50, new Commands(List.of(new RecordingDialAction(seen), new RecordingDialAction(seen)), CommandsType.allAtOnce), false);
        c.execute(new DeviceActionParameters("serial"));
        assertEquals(List.of(128, 128), seen); // round(50 * 2.55)
    }

    @Test
    void nestedActionsRenderTemplatesForTheValueTheyRunAt() {
        var scopes = new ArrayList<TemplateScope>();
        var nested = new RecordingDialAction(new ArrayList<>()) {
            @Override
            public void execute(DialActionParameters context) {
                scopes.add(TemplateContext.current());
            }
        };
        var c = new CommandSetDialValue(30, new Commands(List.of(nested), CommandsType.allAtOnce), false);
        // A button scope without a control index, so no overlay feedback (a CDI event) is fired after the press.
        var button = new TemplateScope("serial", -1, true, null, null, null, null);
        TemplateContext.run(button, () -> c.execute(new DeviceActionParameters("serial")));

        var scope = scopes.getFirst();
        assertNotNull(scope.dial(), "{{ percent }} and {{ raw }} have a dial to read");
        assertEquals(30, Math.round(scope.dial().getValue(null, 0f, 100f)), "{{ percent }}");
        assertEquals(77, scope.dial().value(), "{{ raw }}: round(30 * 2.55)");
        assertTrue(scope.button(), "{{ control }} still names the button");
        assertEquals("serial", scope.serial());
        assertEquals(-1, scope.control(), "the button's own index");
    }

    @Test
    void sequentialNestedActionsRunOneAtATimeInTurn() {
        var first = new ArrayList<Integer>();
        var second = new ArrayList<Integer>();
        var c = new CommandSetDialValue(100, new Commands(List.of(new RecordingDialAction(first), new RecordingDialAction(second)), CommandsType.sequential), false);
        c.execute(new DeviceActionParameters("serial"));
        c.execute(new DeviceActionParameters("serial"));
        c.execute(new DeviceActionParameters("serial"));
        assertEquals(List.of(255, 255), first);
        assertEquals(List.of(255), second);
    }

    @Test
    void toggleReadsTheLevelOfTheNestedAction() {
        var seen = new ArrayList<Integer>();
        var app = new ReadableDialAction(seen, 0.8f);
        var c = new CommandSetDialValue(0, new Commands(List.of(app), CommandsType.allAtOnce), true);
        c.execute(new DeviceActionParameters("serial"));
        c.execute(new DeviceActionParameters("serial"));
        assertEquals(List.of(0, 204), seen); // muted to 0, then back to 80 %
    }

    @Test
    void toggleReturnsToTheOriginalLevelThroughMoveStartAndEnd() {
        var app = new ReadableDialAction(new ArrayList<>(), 0.8f, new DialCommandParams(false, 10, 10));
        var c = new CommandSetDialValue(30, new Commands(List.of(app), CommandsType.allAtOnce), true);

        c.execute(new DeviceActionParameters("serial"));
        assertEquals(0.25f, app.readLevel(), 0.01f, "30 % of the dial is 25 % of the level between the dead zones");
        c.execute(new DeviceActionParameters("serial"));
        assertEquals(0.8f, app.readLevel(), 0.01f, "the second press puts the level back");
        c.execute(new DeviceActionParameters("serial"));
        assertEquals(0.25f, app.readLevel(), 0.01f);
        c.execute(new DeviceActionParameters("serial"));
        assertEquals(0.8f, app.readLevel(), 0.01f);
    }

    @Test
    void toggleReturnsToTheOriginalLevelWhenInverted() {
        var app = new ReadableDialAction(new ArrayList<>(), 0.7f, new DialCommandParams(true, 5, 15));
        var c = new CommandSetDialValue(20, new Commands(List.of(app), CommandsType.allAtOnce), true);

        c.execute(new DeviceActionParameters("serial"));
        c.execute(new DeviceActionParameters("serial"));
        assertEquals(0.7f, app.readLevel(), 0.01f);
    }

    @Test
    void toggleReturnsFromZeroInsideTheMoveStartDeadZone() {
        assertTogglesBetween(0, new DialCommandParams(false, 10, null), 0f, 0.8f);
    }

    @Test
    void toggleReturnsFromFullInsideTheMoveEndDeadZone() {
        assertTogglesBetween(100, new DialCommandParams(false, null, 10), 1f, 0.4f);
    }

    @Test
    void toggleReturnsFromZeroInsideTheMoveStartDeadZoneWhenInverted() {
        assertTogglesBetween(0, new DialCommandParams(true, 10, null), 1f, 0.3f);
    }

    /** Presses four times: the level goes to {@code atValue}, back to {@code original}, and so on. */
    private static void assertTogglesBetween(int value, DialCommandParams params, float atValue, float original) {
        var app = new ReadableDialAction(new ArrayList<>(), original, params);
        var c = new CommandSetDialValue(value, new Commands(List.of(app), CommandsType.allAtOnce), true);
        for (var i = 0; i < 2; i++) {
            c.execute(new DeviceActionParameters("serial"));
            assertEquals(atValue, app.readLevel(), 0.01f, "press " + (2 * i + 1) + " sets the value");
            c.execute(new DeviceActionParameters("serial"));
            assertEquals(original, app.readLevel(), 0.01f, "press " + (2 * i + 2) + " puts the level back");
        }
    }

    @Test
    void dialPercentForInvertsTheMoveAndInvertMapping() {
        var plain = new RecordingDialAction(new ArrayList<>());
        assertEquals(42f, CommandSetDialValue.dialPercentFor(plain, 42f), 0.001f);
        var moved = new RecordingDialAction(new ArrayList<>(), new DialCommandParams(false, 10, 10));
        assertEquals(30f, CommandSetDialValue.dialPercentFor(moved, 25f), 0.001f);
        var inverted = new RecordingDialAction(new ArrayList<>(), new DialCommandParams(true, null, null));
        assertEquals(70f, CommandSetDialValue.dialPercentFor(inverted, 30f), 0.001f);
    }

    @Test
    void nestedCommandsAreExposed() {
        var nested = new Commands(List.of(new RecordingDialAction(new ArrayList<>())), CommandsType.allAtOnce);
        assertEquals(List.of(nested), new CommandSetDialValue(10, nested, false).nestedCommands());
        assertTrue(new CommandSetDialValue(10, null, false).nestedCommands().isEmpty());
    }

    @Test
    void roundTripsWithANestedAppVolume() throws Exception {
        var json = """
                {"_type":"dial.set-value","value":30,"toggleBack":true,"commands":{"type":"allAtOnce","commands":[
                  {"_type":"volume.process","processName":["spotify.exe"],"device":"","isUnMuteOnVolumeChange":false,"dialParams":{"invert":false}}
                ]}}""";

        var read = assertInstanceOf(CommandSetDialValue.class, mapper.readValue(json, Command.class));
        var again = assertInstanceOf(CommandSetDialValue.class, mapper.readValue(mapper.writeValueAsString(read), Command.class));

        assertEquals(30, again.getValue());
        assertTrue(again.isToggleBack());
        assertNotNull(again.getCommands());
        var nested = again.getCommands().getCommands();
        assertEquals(1, nested.size());
        assertEquals(List.of("spotify.exe"), assertInstanceOf(CommandVolumeProcess.class, nested.getFirst()).getProcessName());
    }

    /** A dial action whose level follows the last value it was run with. */
    static final class ReadableDialAction extends RecordingDialAction implements LevelReadable {
        private float level;

        ReadableDialAction(List<Integer> seen, float level) {
            this(seen, level, null);
        }

        ReadableDialAction(List<Integer> seen, float level, @Nullable DialCommandParams dialParams) {
            super(seen, dialParams);
            this.level = level;
        }

        @Override
        public void execute(DialActionParameters context) {
            super.execute(context);
            level = context.dial().getValue(this, 0f, 1f); // what the dial engine makes of it, as a volume command applies it
        }

        @Override
        public Float readLevel() {
            return level;
        }
    }

    /** A dial action that records the raw value it was run with. */
    static class RecordingDialAction extends Command implements DialAction {
        private final List<Integer> seen;
        @Nullable private final DialCommandParams dialParams;

        RecordingDialAction(List<Integer> seen) {
            this(seen, null);
        }

        RecordingDialAction(List<Integer> seen, @Nullable DialCommandParams dialParams) {
            this.seen = seen;
            this.dialParams = dialParams;
        }

        @Override
        public void execute(DialActionParameters context) {
            seen.add(context.dial().value());
        }

        @Override
        @Nullable
        public DialCommandParams getDialParams() {
            return dialParams;
        }

        @Override
        public String buildLabel() {
            return "";
        }
    }
}
