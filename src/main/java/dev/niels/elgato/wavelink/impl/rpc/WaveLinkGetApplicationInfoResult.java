package dev.niels.elgato.wavelink.impl.rpc;

public record WaveLinkGetApplicationInfoResult(
        String appID,
        String operatingSystem,
        String name,
        String version,
        String build, // int?
        String interfaceRevision
) {
}
