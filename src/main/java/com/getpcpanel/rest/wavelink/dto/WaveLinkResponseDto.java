package com.getpcpanel.rest.wavelink.dto;

import java.util.List;

import com.getpcpanel.wavelink.WaveLinkService;

import one.util.streamex.StreamEx;

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
