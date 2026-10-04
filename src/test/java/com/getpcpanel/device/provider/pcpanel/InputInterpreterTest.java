package com.getpcpanel.device.provider.pcpanel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.commands.PCPanelControlEvent;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.integration.volume.command.CommandVolumeProcessMute;
import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.profile.BaseLayerService;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.concurrent.Debouncer;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

class InputInterpreterTest {
    private static final String SERIAL = "serial";
    private static final int BUTTON = 1;

    /**
     * A plain toggle button (no double-click action) must fire on every press - even two presses within the
     * double-click interval. Previously the second press was reclassified as a double-click and dropped, so the
     * toggle got stuck on its first state (#72).
     */
    @Test
    void plainButtonFiresEveryPressImmediately() {
        var events = new CapturingEventBus();
        var sut = interpreter(profileWithoutDblAction(), events);

        sut.doClickAction(SERIAL, BUTTON);
        sut.doClickAction(SERIAL, BUTTON); // a fast second press - must not be swallowed

        var clicks = events.clicks();
        assertEquals(2, clicks.size(), "both presses should fire a click event");
        assertTrue(clicks.stream().noneMatch(ButtonClickEvent::dblClick), "plain presses are never double-clicks");
        assertEquals(BUTTON, clicks.get(0).button());
    }

    /** A button that has a double-click action still uses double-click detection (two fast presses = one dbl). */
    @Test
    void buttonWithDblActionDetectsDoubleClick() {
        var events = new CapturingEventBus();
        var sut = interpreter(profileWithDblAction(), events);

        sut.doClickAction(SERIAL, BUTTON);
        sut.doClickAction(SERIAL, BUTTON); // within the default 500ms interval

        assertTrue(events.clicks().stream().anyMatch(ButtonClickEvent::dblClick),
                "a button with a configured double-click action still reports a double-click");
    }

    /** A quick tap on a button with hold actions is an ordinary press; its hold actions never run. */
    @Test
    void tapOnHoldButtonIsAPress() throws InterruptedException {
        var events = new CapturingEventBus();
        var sut = interpreter(profileWithHoldAction(), events);
        sut.save.get().setHoldInterval(100L);

        sut.doPress(SERIAL, BUTTON);
        sut.doRelease(SERIAL, BUTTON);
        Thread.sleep(200);

        assertEquals(1, events.clicks().size(), "a tap is a press");
        assertTrue(events.holds().isEmpty(), "a tap never runs the hold actions");
    }

    /** Held past the hold interval, the hold actions run and the press does not. */
    @Test
    void longPressRunsHoldInsteadOfPress() throws InterruptedException {
        var events = new CapturingEventBus();
        var sut = interpreter(profileWithHoldAction(), events);
        sut.save.get().setHoldInterval(30L);

        sut.doPress(SERIAL, BUTTON);
        Thread.sleep(200);
        sut.doRelease(SERIAL, BUTTON);

        assertEquals(1, events.holds().size(), "held long enough runs the hold actions");
        assertTrue(events.clicks().isEmpty(), "a hold is not also a press");
    }

    /** Contact bounce on a button is absorbed: one press, and the release is applied once the contact has settled. */
    @Test
    void bouncingButtonIsOnePress() throws Exception {
        var events = new CapturingEventBus();
        var sut = interpreter(profileWithRelease(), events);
        sut.save.getProfile(SERIAL).orElseThrow().getKnobSettings(BUTTON).setButtonDebounce(80);

        sut.onButtonPress(new DeviceCommunicationHandler.ButtonPressEvent(SERIAL, BUTTON, true));
        sut.onButtonPress(new DeviceCommunicationHandler.ButtonPressEvent(SERIAL, BUTTON, false));
        sut.onButtonPress(new DeviceCommunicationHandler.ButtonPressEvent(SERIAL, BUTTON, true));
        sut.onButtonPress(new DeviceCommunicationHandler.ButtonPressEvent(SERIAL, BUTTON, false));
        assertEquals(0, events.releases().size(), "the release waits for the contact to settle");
        Thread.sleep(250);

        assertEquals(1, events.clicks().size(), "bounce inside the window is one press");
        assertEquals(1, events.releases().size(), "the settled release is applied");
    }

    /** A stored window beyond 200 ms behaves as 200 ms. */
    @Test
    void oversizedWindowIsClamped() throws Exception {
        var events = new CapturingEventBus();
        var sut = interpreter(profileWithRelease(), events);
        sut.save.getProfile(SERIAL).orElseThrow().getKnobSettings(BUTTON).setButtonDebounce(1000);

        sut.onButtonPress(new DeviceCommunicationHandler.ButtonPressEvent(SERIAL, BUTTON, true));
        sut.onButtonPress(new DeviceCommunicationHandler.ButtonPressEvent(SERIAL, BUTTON, false));
        Thread.sleep(500);

        assertEquals(1, events.releases().size(), "released after the 200 ms cap, not 1000 ms");
    }

    /** A zero window passes every edge, so a fast second press is a second click. */
    @Test
    void zeroDebounceKeepsEveryPress() throws Exception {
        var events = new CapturingEventBus();
        var sut = interpreter(profileWithoutDblAction(), events);
        sut.save.getProfile(SERIAL).orElseThrow().getKnobSettings(BUTTON).setButtonDebounce(0);

        for (var i = 0; i < 2; i++) {
            sut.onButtonPress(new DeviceCommunicationHandler.ButtonPressEvent(SERIAL, BUTTON, true));
            sut.onButtonPress(new DeviceCommunicationHandler.ButtonPressEvent(SERIAL, BUTTON, false));
        }

        assertEquals(2, events.clicks().size());
    }

    private static InputInterpreter interpreter(Profile profile, CapturingEventBus events) {
        var sut = new InputInterpreter();
        sut.save = new FixedSaveService(profile);
        sut.baseLayer = new BaseLayerService(); // no device save -> no base layer, dispatch behaves as the active profile alone
        sut.eventBus = events;
        sut.debouncer = new Debouncer();
        sut.devices = new com.getpcpanel.device.DeviceHolder();
        return sut;
    }

    private static Profile profileWithoutDblAction() {
        var profile = new Profile("p", DeviceType.PCPANEL_PRO);
        profile.setButtonData(BUTTON, mute());
        return profile;
    }

    private static Profile profileWithRelease() {
        var profile = profileWithoutDblAction();
        profile.setReleaseButtonData(BUTTON, mute());
        return profile;
    }

    private static Profile profileWithDblAction() {
        var profile = profileWithoutDblAction();
        profile.setDblButtonData(BUTTON, mute());
        return profile;
    }

    private static Profile profileWithHoldAction() {
        var profile = profileWithoutDblAction();
        profile.setHoldButtonData(BUTTON, mute());
        return profile;
    }

    private static Commands mute() {
        return new Commands(List.<Command>of(new CommandVolumeProcessMute(Set.of("anything"), MuteType.toggle)), CommandsType.allAtOnce);
    }

    /** Minimal SaveService that always serves a single fixed profile and the default settings. */
    private static final class FixedSaveService extends SaveService {
        private final Profile profile;
        private final Save save = new Save();

        private FixedSaveService(Profile profile) {
            this.profile = profile;
        }

        @Override
        public Save get() {
            return save;
        }

        @Override
        public Optional<Profile> getProfile(String serialNum) {
            return Optional.of(profile);
        }
    }

    /** Minimal {@link Event} that records every fired object so the test can inspect emitted click events. */
    private static final class CapturingEventBus implements Event<Object> {
        private final List<Object> fired = new CopyOnWriteArrayList<>();

        private List<ButtonClickEvent> clicks() {
            return fired.stream().filter(ButtonClickEvent.class::isInstance).map(ButtonClickEvent.class::cast).toList();
        }

        private List<PCPanelControlEvent> releases() {
            return fired.stream().filter(PCPanelControlEvent.class::isInstance).map(PCPanelControlEvent.class::cast)
                        .filter(e -> e.source() == PCPanelControlEvent.Source.RELEASE).toList();
        }

        private List<PCPanelControlEvent> holds() {
            return fired.stream().filter(PCPanelControlEvent.class::isInstance).map(PCPanelControlEvent.class::cast)
                        .filter(e -> e.source() == PCPanelControlEvent.Source.HOLD).toList();
        }

        @Override
        public void fire(Object event) {
            fired.add(event);
        }

        @Override
        public <U> CompletionStage<U> fireAsync(U event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Event<Object> select(Annotation... qualifiers) {
            return this;
        }

        @Override
        public <U> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }
    }
}
