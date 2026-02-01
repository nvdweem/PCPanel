package dev.niels.wavelink.impl;

import org.junit.jupiter.api.Test;

import dev.niels.wavelink.impl.model.WaveLinkChannel;

class WaveLinkClientTest {
    @Test
    void test() throws InterruptedException {
        System.out.println("Test");
        var client = new WaveLinkClient();

        Thread.sleep(1_000);

        var channel = client.getChannels().getFirst();
        // client.setChannel(new WaveLinkChannel(channel.id()).withIsMuted(true)).thenRun(System.out::println);
        client.setChannel(new WaveLinkChannel(channel.id()).withLevel(.5)).thenRun(System.out::println);

        Thread.sleep(1_000_000);
    }
}
