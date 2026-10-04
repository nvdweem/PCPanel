package com.getpcpanel.profile.dto;

import javax.annotation.Nullable;

/**
 * Something the music visualizer can listen to; a {@link VisualizerConfig} holds an ordered list of them and listens to
 * the first that has sound.
 *
 * @param kind   what it is
 * @param device {@link SourceKind#OUTPUT}/{@link SourceKind#INPUT}: the device id; null is the default one, following it when it
 *               changes
 * @param app    {@link SourceKind#APP}: the app (exe name or app id)
 */
public record VisualizerSource(SourceKind kind, @Nullable String device, @Nullable String app) {
    public enum SourceKind {
        /** Everything that plays on an output. */
        OUTPUT,
        /** A microphone or line in. */
        INPUT,
        /** The output an app plays on, while it plays. */
        APP,
        /** The output whatever plays loudest is on. */
        ANY_APP
    }

    public static VisualizerSource output(@Nullable String device) {
        return new VisualizerSource(SourceKind.OUTPUT, device, null);
    }

    public static VisualizerSource input(@Nullable String device) {
        return new VisualizerSource(SourceKind.INPUT, device, null);
    }

    public static VisualizerSource app(String app) {
        return new VisualizerSource(SourceKind.APP, null, app);
    }

    public static VisualizerSource anyApp() {
        return new VisualizerSource(SourceKind.ANY_APP, null, null);
    }
}
