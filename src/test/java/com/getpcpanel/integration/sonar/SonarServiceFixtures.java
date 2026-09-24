package com.getpcpanel.integration.sonar;

import java.lang.annotation.Annotation;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import com.getpcpanel.integration.sonar.dto.SonarSettings;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

/** Builds a SonarService over a fake client and an in-memory Save, with no CDI container. */
final class SonarServiceFixtures {
    private SonarServiceFixtures() {}

    static SonarService service(SonarClient client, boolean enabled) {
        return service(client, enabled, null);
    }

    /** No flush thread: tests drive {@link SonarService#flushDue(long)} with their own clock. */
    static SonarService service(SonarClient client, boolean enabled, @Nullable Event<SonarChangedEvent> changed) {
        return service(client, enabled, changed, null);
    }

    static SonarService service(SonarClient client, boolean enabled, @Nullable Event<SonarChangedEvent> changed,
                                @Nullable ScheduledExecutorService flushExecutor) {
        var save = new Save();
        save.setSonar(new SonarSettings(enabled));
        var saveService = new SaveService() {
            @Override public Save get() {
                return save;
            }
        };
        return new SonarService(client, saveService, changed, flushExecutor);
    }

    /** Counts firings without a CDI container; every other Event method is unused by SonarService. */
    static class RecordingEvent implements Event<SonarChangedEvent> {
        final AtomicInteger fired = new AtomicInteger();

        @Override public void fire(SonarChangedEvent event) {
            fired.incrementAndGet();
        }

        @Override public <U extends SonarChangedEvent> CompletionStage<U> fireAsync(U event) {
            throw new UnsupportedOperationException();
        }

        @Override public <U extends SonarChangedEvent> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override public Event<SonarChangedEvent> select(Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override public <U extends SonarChangedEvent> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override public <U extends SonarChangedEvent> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }
    }
}
