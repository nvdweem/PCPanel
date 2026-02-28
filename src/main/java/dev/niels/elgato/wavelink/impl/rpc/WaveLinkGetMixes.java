package dev.niels.elgato.wavelink.impl.rpc;

import java.util.List;

import dev.niels.elgato.wavelink.impl.model.WaveLinkMix;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkGetMixes.WaveLinkGetMixesResult;

public class WaveLinkGetMixes extends WaveLinkJsonRpcCommand<Void, WaveLinkGetMixesResult> {
    @Override
    public Class<WaveLinkGetMixesResult> getResultClass() {
        return WaveLinkGetMixesResult.class;
    }

    public record WaveLinkGetMixesResult(
            List<WaveLinkMix> mixes
    ) {
    }
}
