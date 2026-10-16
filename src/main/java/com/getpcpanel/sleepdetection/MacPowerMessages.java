package com.getpcpanel.sleepdetection;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import lombok.extern.log4j.Log4j2;

/**
 * Maps the {@code IOMessage.h} system power messages onto {@link SystemEventType}s. Every message that asks for
 * an answer ({@code kIOMessageCanSystemSleep}, {@code kIOMessageSystemWillSleep}) is answered with
 * {@code IOAllowPowerChange} straight away, without waiting for any listener, so the sleep is never delayed by this
 * app. The events are delivered on the {@code events} executor, which must run its tasks in submission order; a
 * suspend is queued just before its answer.
 */
@Log4j2
final class MacPowerMessages {
    /** {@code iokit_common_msg(0x270)}, with {@code iokit_common_msg(m) = sys_iokit | sub_iokit_common | m}. */
    static final int CAN_SYSTEM_SLEEP = 0xE0000270;
    static final int SYSTEM_WILL_SLEEP = 0xE0000280;
    static final int SYSTEM_HAS_POWERED_ON = 0xE0000300;

    private final Consumer<SystemEventType> onEvent;
    private final LongConsumer allowPowerChange;
    private final Executor events;

    /**
     * @param allowPowerChange answers a power message by its notification id ({@code IOAllowPowerChange})
     * @param events           runs the event deliveries in order, off the IOKit callback
     */
    MacPowerMessages(Consumer<SystemEventType> onEvent, LongConsumer allowPowerChange, Executor events) {
        this.onEvent = onEvent;
        this.allowPowerChange = allowPowerChange;
        this.events = events;
    }

    /** Never throws: it runs on IOKit's callback and an exception must not reach native code. */
    void handle(int messageType, long messageArgument) {
        try {
            switch (messageType) {
                case CAN_SYSTEM_SLEEP -> allowPowerChange.accept(messageArgument);
                case SYSTEM_WILL_SLEEP -> {
                    // Queued before the answer: once answered the Mac may sleep at once, and a resume queued after
                    // waking must not overtake this suspend.
                    try {
                        deliver(SystemEventType.goingToSuspend);
                    } finally {
                        allowPowerChange.accept(messageArgument);
                    }
                }
                case SYSTEM_HAS_POWERED_ON -> deliver(SystemEventType.resumedFromSuspend);
                default -> { /* other power messages need no answer */ }
            }
        } catch (RuntimeException | LinkageError e) {
            log.warn("Handling macOS power message {} failed", Integer.toHexString(messageType), e);
        }
    }

    private void deliver(SystemEventType type) {
        events.execute(() -> {
            try {
                onEvent.accept(type);
            } catch (RuntimeException e) {
                log.warn("Handling macOS power event {} failed", type, e);
            }
        });
    }
}
