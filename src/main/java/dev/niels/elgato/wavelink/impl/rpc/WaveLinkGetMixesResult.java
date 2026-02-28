package dev.niels.elgato.wavelink.impl.rpc;

import java.util.List;

import dev.niels.elgato.wavelink.impl.model.WaveLinkMix;

public record WaveLinkGetMixesResult(
        List<WaveLinkMix> mixes
) {
}
