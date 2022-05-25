package com.getpcpanel.obs.remote.communication;

import com.getpcpanel.obs.remote.communication.request.AuthenticateRequest;
import com.getpcpanel.obs.remote.communication.request.BaseRequest;
import com.getpcpanel.obs.remote.communication.request.GetAuthRequiredRequest;
import com.getpcpanel.obs.remote.communication.request.GetCurrentProfileRequest;
import com.getpcpanel.obs.remote.communication.request.GetCurrentSceneRequest;
import com.getpcpanel.obs.remote.communication.request.GetPreviewSceneRequest;
import com.getpcpanel.obs.remote.communication.request.GetSceneItemPropertiesRequest;
import com.getpcpanel.obs.remote.communication.request.GetSceneListRequest;
import com.getpcpanel.obs.remote.communication.request.GetSourceSettingsRequest;
import com.getpcpanel.obs.remote.communication.request.GetSourceTypesListRequest;
import com.getpcpanel.obs.remote.communication.request.GetSourcesListRequest;
import com.getpcpanel.obs.remote.communication.request.GetStreamingStatusRequest;
import com.getpcpanel.obs.remote.communication.request.GetStudioModeStatusRequest;
import com.getpcpanel.obs.remote.communication.request.GetTransitionDurationRequest;
import com.getpcpanel.obs.remote.communication.request.GetTransitionListRequest;
import com.getpcpanel.obs.remote.communication.request.GetVersionRequest;
import com.getpcpanel.obs.remote.communication.request.GetVolumeRequest;
import com.getpcpanel.obs.remote.communication.request.ListProfilesRequest;
import com.getpcpanel.obs.remote.communication.request.SaveReplayBufferRequest;
import com.getpcpanel.obs.remote.communication.request.SetCurrentProfileRequest;
import com.getpcpanel.obs.remote.communication.request.SetCurrentSceneRequest;
import com.getpcpanel.obs.remote.communication.request.SetCurrentTransitionRequest;
import com.getpcpanel.obs.remote.communication.request.SetMuteRequest;
import com.getpcpanel.obs.remote.communication.request.SetPreviewSceneRequest;
import com.getpcpanel.obs.remote.communication.request.SetSceneItemPropertiesRequest;
import com.getpcpanel.obs.remote.communication.request.SetSourceSettingsRequest;
import com.getpcpanel.obs.remote.communication.request.SetStudioModeEnabledRequest;
import com.getpcpanel.obs.remote.communication.request.SetTransitionDurationRequest;
import com.getpcpanel.obs.remote.communication.request.SetVolumeRequest;
import com.getpcpanel.obs.remote.communication.request.StartRecordingRequest;
import com.getpcpanel.obs.remote.communication.request.StartReplayBufferRequest;
import com.getpcpanel.obs.remote.communication.request.StartStreamingRequest;
import com.getpcpanel.obs.remote.communication.request.StopRecordingRequest;
import com.getpcpanel.obs.remote.communication.request.StopReplayBufferRequest;
import com.getpcpanel.obs.remote.communication.request.StopStreamingRequest;
import com.getpcpanel.obs.remote.communication.request.ToggleMuteRequest;
import com.getpcpanel.obs.remote.communication.request.TransitionToProgramRequest;
import com.getpcpanel.obs.remote.communication.response.BaseResponse;
import com.getpcpanel.obs.remote.communication.response.GetAuthRequiredResponse;
import com.getpcpanel.obs.remote.communication.response.GetCurrentProfileResponse;
import com.getpcpanel.obs.remote.communication.response.GetCurrentSceneResponse;
import com.getpcpanel.obs.remote.communication.response.GetPreviewSceneResponse;
import com.getpcpanel.obs.remote.communication.response.GetSceneListResponse;
import com.getpcpanel.obs.remote.communication.response.GetSourceSettingsResponse;
import com.getpcpanel.obs.remote.communication.response.GetSourceTypesListResponse;
import com.getpcpanel.obs.remote.communication.response.GetSourcesListResponse;
import com.getpcpanel.obs.remote.communication.response.GetStreamingStatusResponse;
import com.getpcpanel.obs.remote.communication.response.GetStudioModeStatusResponse;
import com.getpcpanel.obs.remote.communication.response.GetTransitionDurationResponse;
import com.getpcpanel.obs.remote.communication.response.GetTransitionListResponse;
import com.getpcpanel.obs.remote.communication.response.GetVersionResponse;
import com.getpcpanel.obs.remote.communication.response.GetVolumeResponse;
import com.getpcpanel.obs.remote.communication.response.ListProfilesResponse;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RequestType {
    GetVersion(GetVersionRequest.class, GetVersionResponse.class),
    GetAuthRequired(GetAuthRequiredRequest.class, GetAuthRequiredResponse.class),
    Authenticate(AuthenticateRequest.class, BaseResponse.class),
    SetCurrentScene(SetCurrentSceneRequest.class, BaseResponse.class),
    GetSceneList(GetSceneListRequest.class, GetSceneListResponse.class),
    GetCurrentScene(GetCurrentSceneRequest.class, GetCurrentSceneResponse.class),
    GetSourcesList(GetSourcesListRequest.class, GetSourcesListResponse.class),
    GetSourceTypesList(GetSourceTypesListRequest.class, GetSourceTypesListResponse.class),
    SetCurrentTransition(SetCurrentTransitionRequest.class, BaseResponse.class),
    GetSceneItemProperties(GetSceneItemPropertiesRequest.class, BaseResponse.class),
    SetSceneItemProperties(SetSceneItemPropertiesRequest.class, BaseResponse.class),
    GetTransitionList(GetTransitionListRequest.class, GetTransitionListResponse.class),
    GetStudioModeStatus(GetStudioModeStatusRequest.class, GetStudioModeStatusResponse.class),
    EnableStudioMode(SetStudioModeEnabledRequest.class, BaseResponse.class),
    DisableStudioMode(SetStudioModeEnabledRequest.class, BaseResponse.class),
    TransitionToProgram(TransitionToProgramRequest.class, BaseResponse.class),
    GetPreviewScene(GetPreviewSceneRequest.class, GetPreviewSceneResponse.class),
    SetPreviewScene(SetPreviewSceneRequest.class, BaseResponse.class),
    GetSourceSettings(GetSourceSettingsRequest.class, GetSourceSettingsResponse.class),
    SetSourceSettings(SetSourceSettingsRequest.class, BaseResponse.class),
    GetStreamingStatus(GetStreamingStatusRequest.class, GetStreamingStatusResponse.class),
    StartRecording(StartRecordingRequest.class, BaseResponse.class),
    StopRecording(StopRecordingRequest.class, BaseResponse.class),
    StartStreaming(StartStreamingRequest.class, BaseResponse.class),
    StopStreaming(StopStreamingRequest.class, BaseResponse.class),
    SetCurrentProfile(SetCurrentProfileRequest.class, BaseResponse.class),
    GetCurrentProfile(GetCurrentProfileRequest.class, GetCurrentProfileResponse.class),
    ListProfiles(ListProfilesRequest.class, ListProfilesResponse.class),
    SetVolume(SetVolumeRequest.class, BaseResponse.class),
    SetMute(SetMuteRequest.class, BaseResponse.class),
    ToggleMute(ToggleMuteRequest.class, BaseResponse.class),
    GetVolume(GetVolumeRequest.class, GetVolumeResponse.class),
    GetTransitionDuration(GetTransitionDurationRequest.class, GetTransitionDurationResponse.class),
    SetTransitionDuration(SetTransitionDurationRequest.class, BaseResponse.class),
    StartReplayBuffer(StartReplayBufferRequest.class, BaseResponse.class),
    StopReplayBuffer(StopReplayBufferRequest.class, BaseResponse.class),
    SaveReplayBuffer(SaveReplayBufferRequest.class, BaseResponse.class);

    private final Class<? extends BaseRequest> request;
    private final Class<? extends BaseResponse> response;
}

