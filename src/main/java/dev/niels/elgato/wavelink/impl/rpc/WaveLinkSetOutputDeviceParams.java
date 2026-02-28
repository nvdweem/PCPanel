package dev.niels.elgato.wavelink.impl.rpc;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.niels.elgato.wavelink.impl.model.WaveLinkMainOutput;
import dev.niels.elgato.wavelink.impl.model.WaveLinkOutputDevice;
import jakarta.annotation.Nullable;

@JsonInclude(NON_NULL)
public record WaveLinkSetOutputDeviceParams(
        @Nullable WaveLinkOutputDevice outputDevice,
        @Nullable WaveLinkMainOutput mainOutput
) {
}
