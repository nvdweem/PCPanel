package dev.niels.wavelink;

import org.junit.jupiter.api.Test;

class WaveLinkClientImplTest {
    @Test
    void test() throws InterruptedException {
        System.out.println("Test");
        var client = new WaveLinkClient();

        Thread.sleep(1_000);

        // var channel = client.getChannels().getFirst();
        // client.setChannel(new WaveLinkChannel(channel.id()).withIsMuted(true)).thenRun(System.out::println);
        // client.setChannel(new WaveLinkChannel(channel.id()).withLevel(.5)).thenRun(System.out::println);

        // var mix = client.getMixes().getFirst();
        // client.setMix(new WaveLinkMix(mix.id()).withIsMuted(true));
        // client.setMix(new WaveLinkMix(mix.id()).withLevel(.5));

        // var outDevice = client.getOutputDevices().getFirst();
        // System.out.println(outDevice);
        // client.setOutput(outDevice, 50, false);

        // client.setOutputDevice(
        //         outDevice.blank()
        //                  .withOutputs(List.of(
        //                          outDevice.outputs().getFirst().blank().withLevel(1.0).withIsMuted(true)
        //                  ))
        // );
        // client.setMix(new WaveLinkMix(mix.id()).withLevel(.5));

        Thread.sleep(1_000_000);
    }
}
