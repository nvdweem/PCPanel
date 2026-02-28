package dev.niels.elgato.wavelink.impl.rpc;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.annotation.Nullable;

@JsonInclude(NON_NULL)
public record WaveLinkSetSubscriptionParams(
        @Nullable IsEnabled focusedAppChanged,
        @Nullable IsEnabled levelMeterChanged
) {
    public WaveLinkSetSubscriptionParams(@Nullable Boolean focusedAppChanged, @Nullable Boolean levelMeterChanged) {
        this(IsEnabled.create(focusedAppChanged), IsEnabled.create(levelMeterChanged));
    }

    public record IsEnabled(boolean isEnabled) {
        @JsonCreator
        @Nullable
        public static IsEnabled create(@Nullable Boolean isEnabled) {
            if (isEnabled == null)
                return null;
            return new IsEnabled(isEnabled);
        }
    }
}
