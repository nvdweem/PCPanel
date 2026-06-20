package com.getpcpanel.device.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import lombok.extern.log4j.Log4j2;

/**
 * Discovers every {@link DeviceProvider} bean and drives their lifecycle: starts them on Quarkus
 * startup and stops them on shutdown. Providers are plain {@code @ApplicationScoped} beans present
 * in every build.
 */
@Log4j2
@ApplicationScoped
public class DeviceProviderRegistry {
    @Inject Instance<DeviceProvider> providers;
    private final List<DeviceProvider> started = new ArrayList<>();

    /** Finds a started provider by its {@link DeviceProvider#id()}, narrowed to {@code type}. */
    public <T extends DeviceProvider> Optional<T> find(String id, Class<T> type) {
        for (var provider : providers) {
            if (provider.id().equals(id) && type.isInstance(provider)) {
                return Optional.of(type.cast(provider));
            }
        }
        return Optional.empty();
    }

    public void onStart(@Observes StartupEvent ev) {
        for (var provider : providers) {
            try {
                provider.start();
                started.add(provider);
                log.info("Started device provider '{}'", provider.id());
            } catch (Throwable e) {
                log.error("Failed to start device provider '{}': {}", provider.id(), e.getMessage(), e);
            }
        }
    }

    public void onShutdown(@Observes ShutdownEvent ev) {
        for (var provider : started) {
            try {
                provider.stop();
            } catch (Throwable e) {
                log.error("Failed to stop device provider '{}': {}", provider.id(), e.getMessage(), e);
            }
        }
        started.clear();
    }
}
