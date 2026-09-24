package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SonarRouteTest {
    @Test
    void streamRoutePutsMixBeforeChannel() {
        var route = SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game);
        assertEquals("/volumeSettings/streamer/monitoring/game/Volume/0.5000", route.volumePath(0.5));
        assertEquals("/volumeSettings/streamer/monitoring/game/isMuted/true", route.mutePath(true));
    }

    @Test
    void classicRouteOmitsTheMix() {
        var route = SonarRoute.of(SonarMode.classic, SonarMix.monitoring, SonarChannel.Media);
        assertNull(route.mix());
        assertEquals("/volumeSettings/classic/media/Volume/1.0000", route.volumePath(1.0));
        assertEquals("/volumeSettings/classic/media/Mute/false", route.mutePath(false));
    }

    @Test
    void classicCollapsesBothMixesOntoOneKey() {
        // This equality IS the dedup: one control carrying Game/Monitoring and Game/Streaming
        // produces a single pending write in Classic mode.
        assertEquals(SonarRoute.of(SonarMode.classic, SonarMix.monitoring, SonarChannel.Game),
                     SonarRoute.of(SonarMode.classic, SonarMix.streaming, SonarChannel.Game));
    }

    @Test
    void streamKeepsTheMixesDistinct() {
        assertNotEquals(SonarRoute.of(SonarMode.stream, SonarMix.monitoring, SonarChannel.Game),
                        SonarRoute.of(SonarMode.stream, SonarMix.streaming, SonarChannel.Game));
    }

    @Test
    void channelsUseSonarApiIdsNotDisplayNames() {
        assertEquals("chatRender", SonarChannel.Chat.apiId());
        assertEquals("chatCapture", SonarChannel.Mic.apiId());
        assertEquals("master", SonarChannel.Master.apiId());
        assertEquals("aux", SonarChannel.Aux.apiId());
    }

    @Test
    void compactConstructorNormalizesClassicModeMix() {
        // Pin the invariant: the compact constructor normalizes mix to null in Classic mode
        // regardless of construction path, so direct canonical construction collapses duplicates
        // the same way as via of().
        var direct1 = new SonarRoute(SonarMode.classic, SonarMix.monitoring, SonarChannel.Game);
        var direct2 = new SonarRoute(SonarMode.classic, SonarMix.streaming, SonarChannel.Game);
        assertNull(direct1.mix());
        assertNull(direct2.mix());
        assertEquals(direct1, direct2);
    }
}
