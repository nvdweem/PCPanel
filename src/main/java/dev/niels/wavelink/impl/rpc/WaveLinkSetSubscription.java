package dev.niels.wavelink.impl.rpc;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import dev.niels.wavelink.impl.rpc.WaveLinkSetSubscription.WaveLinkSetSubscriptionParams;

public class WaveLinkSetSubscription extends WaveLinkJsonRpcCommand<WaveLinkSetSubscriptionParams, WaveLinkSetSubscriptionParams> {
    @Override
    public Class<WaveLinkSetSubscriptionParams> getResultClass() {
        return WaveLinkSetSubscriptionParams.class;
    }

    public static WaveLinkSetSubscription setFocusAppChanged(Boolean focusedAppChanged) {
        return from(focusedAppChanged, null);
    }

    public static WaveLinkSetSubscription setLevelMeterChanged(Boolean levelMeterChanged) {
        return from(null, levelMeterChanged);
    }

    private static WaveLinkSetSubscription from(@Nullable Boolean focusedAppChanged, @Nullable Boolean levelMeterChanged) {
        var result = new WaveLinkSetSubscription();
        result.setParams(new WaveLinkSetSubscriptionParams(focusedAppChanged, levelMeterChanged));
        return result;
    }

    @JsonInclude(Include.NON_NULL)
    public record WaveLinkSetSubscriptionParams(
            @Nullable IsEnabled focusedAppChanged,
            @Nullable IsEnabled levelMeterChanged
    ) {
        public WaveLinkSetSubscriptionParams(@Nullable Boolean focusedAppChanged, @Nullable Boolean levelMeterChanged) {
            this(IsEnabled.create(focusedAppChanged), IsEnabled.create(levelMeterChanged));
        }
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
