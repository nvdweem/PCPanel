package com.getpcpanel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.PCPanelControlEvent.Source;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.profile.dto.KnobSetting;

/**
 * Routing behaviour of {@link CommandDispatcher} and the {@link Command#toRunnable} action-selection
 * contract it relies on:
 * <ul>
 *     <li>a control event's configured command executes on the handler thread;</li>
 *     <li>the coalescing map keys on serial+source+knob, so a button's press and release never
 *     overwrite each other while a burst of dial events for one knob collapses to the latest;</li>
 *     <li>a command that throws does not take down the handler — later commands still run;</li>
 *     <li>a command that is both a {@link DialAction} and a {@link ButtonAction} runs as a dial when a
 *     dial value is present and as a button otherwise;</li>
 *     <li>a {@code sequential} {@link Commands} advances one command per event, wrapping around.</li>
 * </ul>
 */
@DisplayName("CommandDispatcher routing")
class CommandDispatcherTest {
    @Test
    @DisplayName("a control event's configured command executes on the handler thread")
    void eventExecutesConfiguredCommand() throws Exception {
        var dispatcher = startedDispatcher();
        var latch = new CountDownLatch(1);

        dispatcher.onCommand(event("serial", 1, Source.PRESS, null, new ButtonCommand(latch::countDown)));

        assertTrue(latch.await(2, TimeUnit.SECONDS), "the command must run on the handler thread");
    }

    @Test
    @DisplayName("press and release of the same knob keep separate map entries; dial events coalesce to the latest")
    void coalescingKeys() {
        var dispatcher = dispatcher(); // handler not started: the map contents stay observable

        dispatcher.onCommand(event("serial", 1, Source.PRESS, null, new ButtonCommand(() -> {
        })));
        dispatcher.onCommand(event("serial", 1, Source.RELEASE, null, new ButtonCommand(() -> {
        })));
        assertEquals(2, dispatcher.map.size(), "a quick tap must not lose the press to the release");

        var ran = new ArrayList<String>();
        dispatcher.onCommand(event("serial", 2, Source.DIAL, dial(10), new ButtonCommand(() -> ran.add("older"))));
        dispatcher.onCommand(event("serial", 2, Source.DIAL, dial(20), new ButtonCommand(() -> ran.add("newest"))));
        assertEquals(3, dispatcher.map.size(), "dial events for one knob coalesce into a single entry");

        dispatcher.map.get("serial|DIAL|2").run();
        assertEquals(List.of("newest"), ran, "the coalesced entry holds the most recent event's command");
    }

    @Test
    @DisplayName("a throwing command does not stop the handler; later commands still run")
    void throwingCommandIsIsolated() throws Exception {
        var dispatcher = startedDispatcher();
        var latch = new CountDownLatch(1);

        dispatcher.onCommand(event("serial", 1, Source.PRESS, null, new ButtonCommand(() -> {
            throw new IllegalStateException("boom");
        })));
        dispatcher.onCommand(event("serial", 2, Source.PRESS, null, new ButtonCommand(latch::countDown)));

        assertTrue(latch.await(2, TimeUnit.SECONDS), "the handler must survive a throwing command");
    }

    @Test
    @DisplayName("a dual dial+button command runs as a dial when a dial value is present, as a button otherwise")
    void toRunnablePrefersDialActionWithDialValue() {
        var command = new DualCommand();

        command.toRunnable(false, "serial", dial(42)).run();
        assertTrue(command.dialExecuted, "a present dial value selects the DialAction path");
        assertFalse(command.buttonExecuted, "the ButtonAction path must not also run");

        command.dialExecuted = false;
        command.toRunnable(false, "serial", null).run();
        assertTrue(command.buttonExecuted, "without a dial value the command runs as a button");
        assertFalse(command.dialExecuted);
    }

    @Test
    @DisplayName("a sequential Commands runs one command per event, wrapping around")
    void sequentialCommandsCycle() {
        var ran = new ArrayList<String>();
        var commands = new Commands(List.of(new ButtonCommand(() -> ran.add("first")), new ButtonCommand(() -> ran.add("second"))), CommandsType.sequential);
        var event = new PCPanelControlEvent("serial", 1, commands, false, null, Source.PRESS);

        event.buildRunnable().run();
        event.buildRunnable().run();
        event.buildRunnable().run();

        assertEquals(List.of("first", "second", "first"), ran);
    }

    /** Handler threads are daemons, so instances left running do not keep the test JVM alive. */
    private static CommandDispatcher startedDispatcher() {
        var dispatcher = dispatcher();
        dispatcher.init();
        return dispatcher;
    }

    private static CommandDispatcher dispatcher() {
        try {
            var ctor = CommandDispatcher.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to construct CommandDispatcher", e);
        }
    }

    private static PCPanelControlEvent event(String serial, int knob, Source source, @Nullable DialValue vol, Command command) {
        return new PCPanelControlEvent(serial, knob, new Commands(List.of(command), CommandsType.allAtOnce), false, vol, source);
    }

    private static DialValue dial(int value) {
        return new DialValue((KnobSetting) null, value);
    }

    private static final class ButtonCommand extends Command implements ButtonAction {
        private final Runnable onExecute;

        private ButtonCommand(Runnable onExecute) {
            this.onExecute = onExecute;
        }

        @Override public void execute() {
            onExecute.run();
        }

        @Override public String buildLabel() {
            return "button stub";
        }
    }

    private static final class DualCommand extends Command implements DialAction, ButtonAction {
        private boolean dialExecuted;
        private boolean buttonExecuted;

        @Override public void execute(DialActionParameters context) {
            dialExecuted = true;
        }

        @Override public void execute() {
            buttonExecuted = true;
        }

        @Override public @Nullable DialCommandParams getDialParams() {
            return null;
        }

        @Override public boolean hasOverlay() {
            // DialAction and ButtonAction both declare a default; a dual-role command must pick one.
            return false;
        }

        @Override public String buildLabel() {
            return "dual stub";
        }
    }
}
