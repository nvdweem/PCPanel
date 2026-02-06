package dev.niels.wavelink.impl.rpc;

import dev.niels.wavelink.impl.rpc.WaveLinkSetSubscription.WaveLinkSetSubscriptionParams;

public class WaveLinkSetSubscription extends WaveLinkJsonRpcCommand<WaveLinkSetSubscriptionParams, WaveLinkSetSubscriptionParams> {
    @Override
    public Class<WaveLinkSetSubscriptionParams> getResultClass() {
        return WaveLinkSetSubscriptionParams.class;
    }

    public record WaveLinkSetSubscriptionParams(
            Boolean focusedAppChanged,
            Boolean levelMeterChanged
    ) {
    }
}
