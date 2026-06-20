package com.getpcpanel.rest.wavelink.dto;

import java.util.List;

import com.getpcpanel.wavelink.WaveLinkService;

import io.quarkus.runtime.annotations.RegisterForReflection;
import one.util.streamex.StreamEx;

// The native image serializes these records via reflection. Registering the nested element types
// here makes serialization independent of whether a tracing run happened to observe non-empty
// channel/input/output/app/effect lists — when Wave Link is connected those lists are populated and
// Jackson must reflect over every element type, not just the top-level response. The array forms are
// registered too because Jackson reflectively instantiates the element array type for each List.
@RegisterForReflection(targets = {
        WaveLinkResponseDto.class,
        WaveLinkChannelDto.class, WaveLinkChannelDto[].class,
        WaveLinkInputDto.class, WaveLinkInputDto[].class,
        WaveLinkMixDto.class, WaveLinkMixDto[].class,
        WaveLinkOutputDto.class, WaveLinkOutputDto[].class,
        WaveLinkAppDto.class, WaveLinkAppDto[].class,
        WaveLinkEffectDto.class, WaveLinkEffectDto[].class,
})
public record WaveLinkResponseDto(
        List<WaveLinkChannelDto> channels,
        List<WaveLinkInputDto> inputs,
        List<WaveLinkMixDto> mixes,
        List<WaveLinkOutputDto> outputs
) {
    public static WaveLinkResponseDto from(WaveLinkService waveLinkService) {
        return new WaveLinkResponseDto(
                StreamEx.ofValues(waveLinkService.getChannels()).map(WaveLinkChannelDto::from).toList(),
                StreamEx.ofValues(waveLinkService.getInputDevices()).map(WaveLinkInputDto::from).toList(),
                StreamEx.ofValues(waveLinkService.getMixes()).map(WaveLinkMixDto::from).toList(),
                StreamEx.ofValues(waveLinkService.getOutputDevices()).map(WaveLinkOutputDto::from).toList());
    }
}
