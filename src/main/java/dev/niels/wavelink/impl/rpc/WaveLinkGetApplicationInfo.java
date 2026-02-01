package dev.niels.wavelink.impl.rpc;

import dev.niels.wavelink.impl.rpc.WaveLinkGetApplicationInfo.WaveLinkGetApplicationInfoResult;

public class WaveLinkGetApplicationInfo extends WaveLinkJsonRpcCommand<Void, WaveLinkGetApplicationInfoResult> {
    @Override
    public Class<WaveLinkGetApplicationInfoResult> getResultClass() {
        return WaveLinkGetApplicationInfoResult.class;
    }

    public record WaveLinkGetApplicationInfoResult(
            String appID,
            String operatingSystem,
            String name,
            String version,
            String build, // int?
            String interfaceRevision
    ) {
    }
}
