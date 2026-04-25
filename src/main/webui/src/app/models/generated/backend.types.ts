/* tslint:disable */
/* eslint-disable */

export interface ButtonAction {
    overlayText?: string;
}

export interface Command {
    _type: "com.getpcpanel.commands.command.CommandBrightness" | "com.getpcpanel.commands.command.CommandEndProgram" | "com.getpcpanel.commands.command.CommandKeystroke" | "com.getpcpanel.commands.command.CommandMedia" | "com.getpcpanel.commands.command.CommandNoOp" | "com.getpcpanel.commands.command.CommandObs" | "com.getpcpanel.commands.command.CommandObsMuteSource" | "com.getpcpanel.commands.command.CommandObsSetScene" | "com.getpcpanel.commands.command.CommandObsSetSourceVolume" | "com.getpcpanel.commands.command.CommandProfile" | "com.getpcpanel.commands.command.CommandRun" | "com.getpcpanel.commands.command.CommandShortcut" | "com.getpcpanel.commands.command.CommandVoiceMeeter" | "com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced" | "com.getpcpanel.commands.command.CommandVoiceMeeterAdvancedButton" | "com.getpcpanel.commands.command.CommandVoiceMeeterBasic" | "com.getpcpanel.commands.command.CommandVoiceMeeterBasicButton" | "com.getpcpanel.commands.command.CommandVolume" | "com.getpcpanel.commands.command.CommandVolumeApplicationDeviceToggle" | "com.getpcpanel.commands.command.CommandVolumeDefaultDevice" | "com.getpcpanel.commands.command.CommandVolumeDefaultDeviceAdvanced" | "com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggle" | "com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggleAdvanced" | "com.getpcpanel.commands.command.CommandVolumeDevice" | "com.getpcpanel.commands.command.CommandVolumeDeviceMute" | "com.getpcpanel.commands.command.CommandVolumeFocus" | "com.getpcpanel.commands.command.CommandVolumeFocusMute" | "com.getpcpanel.commands.command.CommandVolumeProcess" | "com.getpcpanel.commands.command.CommandVolumeProcessMute" | "com.getpcpanel.wavelink.command.CommandWaveLink" | "com.getpcpanel.wavelink.command.CommandWaveLinkAddFocusToChannel" | "com.getpcpanel.wavelink.command.CommandWaveLinkChange" | "com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel" | "com.getpcpanel.wavelink.command.CommandWaveLinkChangeMute" | "com.getpcpanel.wavelink.command.CommandWaveLinkChannelEffect" | "com.getpcpanel.wavelink.command.CommandWaveLinkMainOutput";
}

export interface CommandBrightness extends Command, DialAction {
    _type: "com.getpcpanel.commands.command.CommandBrightness";
}

export interface CommandConverter {
}

export interface CommandEndProgram extends Command, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandEndProgram";
    name: string;
    specific: boolean;
}

export interface CommandKeystroke extends Command, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandKeystroke";
    keystroke: string;
}

export interface CommandMedia extends Command, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandMedia";
    button: VolumeButton;
    spotify: boolean;
}

export interface CommandNoOp extends Command, ButtonAction, DialAction {
    _type: "com.getpcpanel.commands.command.CommandNoOp";
}

export interface CommandObs extends Command {
    _type: "com.getpcpanel.commands.command.CommandObs" | "com.getpcpanel.commands.command.CommandObsMuteSource" | "com.getpcpanel.commands.command.CommandObsSetScene" | "com.getpcpanel.commands.command.CommandObsSetSourceVolume";
}

export interface CommandObsMuteSource extends CommandObs, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandObsMuteSource";
    source: string;
    type: MuteType;
}

export interface CommandObsSetScene extends CommandObs, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandObsSetScene";
    scene: string;
}

export interface CommandObsSetSourceVolume extends CommandObs, DialAction {
    _type: "com.getpcpanel.commands.command.CommandObsSetSourceVolume";
    sourceName: string;
}

export interface CommandProfile extends Command, DeviceAction {
    _type: "com.getpcpanel.commands.command.CommandProfile";
    profile?: string;
}

export interface CommandRun extends Command, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandRun";
    command: string;
}

export interface Commands {
    commands: Command[];
    type?: CommandsType;
}

export interface CommandShortcut extends Command, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandShortcut";
    shortcut: string;
}

export interface CommandType {
    category: CommandCategory;
    command: string;
    kind: Kinds;
    name: string;
}

export interface CommandVoiceMeeter extends Command {
    _type: "com.getpcpanel.commands.command.CommandVoiceMeeter" | "com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced" | "com.getpcpanel.commands.command.CommandVoiceMeeterAdvancedButton" | "com.getpcpanel.commands.command.CommandVoiceMeeterBasic" | "com.getpcpanel.commands.command.CommandVoiceMeeterBasicButton";
}

export interface CommandVoiceMeeterAdvanced extends CommandVoiceMeeter, DialAction {
    _type: "com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced";
    ct: DialControlMode;
    fullParam: string;
}

export interface CommandVoiceMeeterAdvancedButton extends CommandVoiceMeeter, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandVoiceMeeterAdvancedButton";
    bt: ButtonControlMode;
    fullParam: string;
    stringValue?: string;
}

export interface CommandVoiceMeeterBasic extends CommandVoiceMeeter, DialAction {
    _type: "com.getpcpanel.commands.command.CommandVoiceMeeterBasic";
    ct: ControlType;
    dt: DialType;
    index: number;
}

export interface CommandVoiceMeeterBasicButton extends CommandVoiceMeeter, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandVoiceMeeterBasicButton";
    bt: ButtonType;
    ct: ControlType;
    index: number;
}

export interface CommandVolume extends Command {
    _type: "com.getpcpanel.commands.command.CommandVolume" | "com.getpcpanel.commands.command.CommandVolumeApplicationDeviceToggle" | "com.getpcpanel.commands.command.CommandVolumeDefaultDevice" | "com.getpcpanel.commands.command.CommandVolumeDefaultDeviceAdvanced" | "com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggle" | "com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggleAdvanced" | "com.getpcpanel.commands.command.CommandVolumeDevice" | "com.getpcpanel.commands.command.CommandVolumeDeviceMute" | "com.getpcpanel.commands.command.CommandVolumeFocus" | "com.getpcpanel.commands.command.CommandVolumeFocusMute" | "com.getpcpanel.commands.command.CommandVolumeProcess" | "com.getpcpanel.commands.command.CommandVolumeProcessMute";
}

export interface CommandVolumeApplicationDeviceToggle extends CommandVolume, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandVolumeApplicationDeviceToggle";
    currentIdx: number;
    devices: DeviceSet[];
    followFocus: boolean;
    processes: string[];
}

export interface CommandVolumeDefaultDevice extends CommandVolume, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandVolumeDefaultDevice";
    deviceId: string;
}

export interface CommandVolumeDefaultDeviceAdvanced extends CommandVolume, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandVolumeDefaultDeviceAdvanced";
    communicationPb: string;
    communicationRec: string;
    mediaPb: string;
    mediaRec: string;
    name: string;
}

export interface CommandVolumeDefaultDeviceAdvancedBuilder {
}

export interface CommandVolumeDefaultDeviceToggle extends CommandVolume, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggle";
    currentIdx: number;
    devices: string[];
}

export interface CommandVolumeDefaultDeviceToggleAdvanced extends CommandVolume, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggleAdvanced";
    currentIdx: number;
    devices: DeviceSet[];
}

export interface CommandVolumeDevice extends CommandVolume, DialAction {
    _type: "com.getpcpanel.commands.command.CommandVolumeDevice";
    deviceId: string;
    isUnMuteOnVolumeChange: boolean;
    unMuteOnVolumeChange: boolean;
}

export interface CommandVolumeDeviceMute extends CommandVolume, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandVolumeDeviceMute";
    deviceId: string;
    muteType: MuteType;
}

export interface CommandVolumeFocus extends CommandVolume, DialAction {
    _type: "com.getpcpanel.commands.command.CommandVolumeFocus";
}

export interface CommandVolumeFocusMute extends CommandVolume, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandVolumeFocusMute";
    muteType: MuteType;
}

export interface CommandVolumeProcess extends CommandVolume, DialAction {
    _type: "com.getpcpanel.commands.command.CommandVolumeProcess";
    device: string;
    isUnMuteOnVolumeChange: boolean;
    processName: string[];
    unMuteOnVolumeChange: boolean;
}

export interface CommandVolumeProcessMute extends CommandVolume, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandVolumeProcessMute";
    muteType: MuteType;
    processName: string[];
}

export interface CommandWaveLink extends Command {
    _type: "com.getpcpanel.wavelink.command.CommandWaveLink" | "com.getpcpanel.wavelink.command.CommandWaveLinkAddFocusToChannel" | "com.getpcpanel.wavelink.command.CommandWaveLinkChange" | "com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel" | "com.getpcpanel.wavelink.command.CommandWaveLinkChangeMute" | "com.getpcpanel.wavelink.command.CommandWaveLinkChannelEffect" | "com.getpcpanel.wavelink.command.CommandWaveLinkMainOutput";
}

export interface CommandWaveLinkAddFocusToChannel extends CommandWaveLink, ButtonAction {
    _type: "com.getpcpanel.wavelink.command.CommandWaveLinkAddFocusToChannel";
    channelId?: string;
    channelName?: string;
    id?: string;
    name?: string;
}

export interface CommandWaveLinkChange extends CommandWaveLink {
    _type: "com.getpcpanel.wavelink.command.CommandWaveLinkChange" | "com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel" | "com.getpcpanel.wavelink.command.CommandWaveLinkChangeMute";
    commandType: WaveLinkCommandTarget;
    id1?: string;
    id2?: string;
}

export interface CommandWaveLinkChangeLevel extends CommandWaveLinkChange, DialAction {
    _type: "com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel";
}

export interface CommandWaveLinkChangeMute extends CommandWaveLinkChange, ButtonAction {
    _type: "com.getpcpanel.wavelink.command.CommandWaveLinkChangeMute";
    muteType: MuteType;
}

export interface CommandWaveLinkChannelEffect extends CommandWaveLink, ButtonAction {
    _type: "com.getpcpanel.wavelink.command.CommandWaveLinkChannelEffect";
    channelId?: string;
    channelName?: string;
    effectId?: string;
    effectName?: string;
    toggleType: MuteType;
}

export interface CommandWaveLinkMainOutput extends CommandWaveLink, ButtonAction {
    _type: "com.getpcpanel.wavelink.command.CommandWaveLinkMainOutput";
    id?: string;
    name?: string;
}

export interface ControlAssignmentsUpdateDto {
    analog?: Commands;
    button?: Commands;
    dblButton?: Commands;
    knobSetting?: KnobSetting;
}

export interface DeviceAction {
}

export interface DeviceActionParameters {
    device: string;
}

export interface DeviceDto {
    analogCount: number;
    buttonCount: number;
    currentProfile: string;
    deviceType: DeviceType;
    displayName: string;
    hasLogoLed: boolean;
    profiles: string[];
    serial: string;
}

export interface DeviceSet {
    communicationPlayback: string;
    communicationRecord: string;
    mediaPlayback: string;
    mediaRecord: string;
    name: string;
}

export interface DeviceSnapshotDto extends WsEvent {
    analogCount: number;
    analogValues: number[];
    buttonCount: number;
    currentProfile: string;
    currentProfileSnapshot: ProfileSnapshotDto;
    deviceType: string;
    dialColors: string[];
    displayName: string;
    hasLogoLed: boolean;
    lightingConfig: LightingConfig;
    logoColor: string;
    profiles: string[];
    serial: string;
    sliderColors: string[][];
    sliderLabelColors: string[];
    type: "device_snapshot";
}

export interface DialAction {
    dialParams?: DialCommandParams;
    invert: boolean;
}

export interface DialActionParameters {
    device: string;
    dial: DialValue;
    initial: boolean;
}

export interface DialCommandParams {
    invert: boolean;
    moveEnd?: number;
    moveStart?: number;
}

export interface DialValue {
    settings: DialValueCalculator;
    value: number;
}

export interface DialValueCalculator {
}

export interface HomeAssistantSettings {
    availability: boolean;
    baseTopic: string;
    enableDiscovery: boolean;
}

export interface KnobSetting {
    buttonDebounce: number;
    logarithmic: boolean;
    maxTrim: number;
    minTrim: number;
    overlayIcon: string;
}

export interface LightingConfig {
    allColor: string;
    breathBrightness: number;
    breathHue: number;
    breathSpeed: number;
    globalBrightness: number;
    individualColors: string[];
    knobConfigs: SingleKnobLightingConfig[];
    lightingMode: LightingMode;
    logoConfig: SingleLogoLightingConfig;
    rainbowBrightness: number;
    rainbowPhaseShift: number;
    rainbowReverse: number;
    rainbowSpeed: number;
    rainbowVertical: number;
    sliderConfigs: SingleSliderLightingConfig[];
    sliderLabelConfigs: SingleSliderLabelLightingConfig[];
    volumeBrightnessTrackingEnabled: boolean[];
    waveBounce: number;
    waveBrightness: number;
    waveHue: number;
    waveReverse: number;
    waveSpeed: number;
}

export interface LightingConfigBuilder {
}

export interface MqttSettings {
    baseTopic: string;
    enabled: boolean;
    homeAssistant: HomeAssistantSettings;
    host: string;
    password: string;
    port: number;
    secure: boolean;
    username: string;
}

export interface OSCBinding {
    address: string;
    max: number;
    min: number;
    toggle: boolean;
}

export interface OSCConnectionInfo {
    host: string;
    port: number;
}

export interface ProcessDto {
    icon?: string;
    name: string;
    path: string;
    pid: number;
}

export interface ProfileDto {
    isMainProfile: boolean;
    name: string;
}

export interface ProfileSnapshotDto {
    buttonData: { [index: string]: Commands };
    dblButtonData: { [index: string]: Commands };
    dialData: { [index: string]: Commands };
    knobSettings: { [index: string]: KnobSetting };
    name: string;
}

export interface SettingsDto {
    dblClickInterval: number;
    forceVolume: boolean;
    mainUIIcons: boolean;
    obsAddress: string;
    obsEnabled: boolean;
    obsPassword: string;
    obsPort: string;
    oscConnections: OSCConnectionInfo[];
    oscListenPort: number;
    overlayBackgroundColor: string;
    overlayBarBackgroundColor: string;
    overlayBarColor: string;
    overlayBarCornerRounding?: number;
    overlayBarHeight?: number;
    overlayEnabled: boolean;
    overlayPadding?: number;
    overlayPosition?: OverlayPosition;
    overlayShowNumber: boolean;
    overlayTextColor: string;
    overlayUseLog: boolean;
    overlayWindowCornerRounding: number;
    preventClickWhenDblClick: boolean;
    preventSliderTwitchDelay?: number;
    sendOnlyIfDelta?: number;
    sliderRollingAverage?: number;
    startupVersionCheck: boolean;
    voicemeeterEnabled: boolean;
    voicemeeterPath: string;
    workaroundsOnlySliders: boolean;
}

export interface SingleKnobLightingConfig {
    color1: string;
    color2: string;
    mode: SINGLE_KNOB_MODE;
    muteOverrideColor?: string;
    muteOverrideDeviceOrFollow?: string;
}

export interface SingleLogoLightingConfig {
    brightness: number;
    color: string;
    hue: number;
    mode: SINGLE_LOGO_MODE;
    speed: number;
}

export interface SingleSliderLabelLightingConfig {
    color: string;
    mode: SINGLE_SLIDER_LABEL_MODE;
    muteOverrideColor: string;
    muteOverrideDeviceOrFollow: string;
}

export interface SingleSliderLightingConfig {
    color1: string;
    color2: string;
    mode: SINGLE_SLIDER_MODE;
    muteOverrideColor: string;
    muteOverrideDeviceOrFollow: string;
}

export interface WaveLinkSettings {
    enabled: boolean;
}

export interface WsAssignmentChangedEvent extends WsEvent {
    commands: Commands;
    index: number;
    kind: Kinds;
    serial: string;
    type: "assignment_changed";
}

export interface WsButtonEvent extends WsEvent {
    button: number;
    pressed: boolean;
    serial: string;
    type: "button_press";
}

export interface WsDeviceConnectedEvent extends WsEvent {
    deviceSnapshot: DeviceSnapshotDto;
    type: "device_connected";
}

export interface WsDeviceDisconnectedEvent extends WsEvent {
    serial: string;
    type: "device_disconnected";
}

export interface WsDeviceRenamedEvent extends WsEvent {
    displayName: string;
    serial: string;
    type: "device_renamed";
}

export interface WsEvent {
    type: "device_snapshot" | "assignment_changed" | "button_press" | "device_connected" | "device_disconnected" | "device_renamed" | "knob_rotate" | "lighting_changed" | "profile_switched" | "visual_colors_changed";
}

export interface WsKnobEvent extends WsEvent {
    knob: number;
    serial: string;
    type: "knob_rotate";
    value: number;
}

export interface WsLightingChangedEvent extends WsEvent {
    dialColors: string[];
    lightingConfig: LightingConfig;
    logoColor: string;
    serial: string;
    sliderColors: string[][];
    sliderLabelColors: string[];
    type: "lighting_changed";
}

export interface WsProfileSwitchedEvent extends WsEvent {
    dialColors: string[];
    logoColor: string;
    profileName: string;
    profileSnapshot: ProfileSnapshotDto;
    serial: string;
    sliderColors: string[][];
    sliderLabelColors: string[];
    type: "profile_switched";
}

export interface WsVisualColorsChangedEvent extends WsEvent {
    dialColors: string[];
    logoColor: string;
    serial: string;
    sliderColors: string[][];
    sliderLabelColors: string[];
    type: "visual_colors_changed";
}

export type ButtonControlMode = "ENABLE" | "DISABLE" | "TOGGLE" | "STRING";

export type ButtonType = "MONO" | "MUTE" | "SOLO" | "MC" | "EQ" | "A1" | "A2" | "A3" | "A4" | "A5" | "B1" | "B2" | "B3" | "SEL" | "MIXA" | "MIXB" | "REPEAT" | "COMPOSITE";

export type CommandCategory = "standard" | "voicemeeter" | "obs" | "wavelink";

export type CommandsType = "allAtOnce" | "sequential";

export type ControlType = "STRIP" | "BUS";

export type DeviceType = "PCPANEL_RGB" | "PCPANEL_MINI" | "PCPANEL_PRO";

export type DialControlMode = "NEG_12_TO_12" | "ZERO_TO_10" | "NEG_40_TO_12" | "NEG_60_TO_12" | "NEG_INF_TO_12" | "NEG_INF_TO_ZERO";

export type DialType = "GAIN" | "AUDIBILITY" | "COMP" | "GATE" | "LIMIT" | "EQGAIN1" | "EQGAIN2" | "EQGAIN3" | "REVERB" | "DELAY" | "FX1" | "FX2" | "RETURNREVERB" | "RETURNDELAY" | "RETURNFX1" | "RETURNFX2";

export type Kinds = "dial" | "button" | "dblbutton";

export type LightingMode = "ALL_COLOR" | "ALL_RAINBOW" | "ALL_WAVE" | "ALL_BREATH" | "SINGLE_COLOR" | "CUSTOM";

export type MuteType = "mute" | "unmute" | "toggle";

export type OverlayPosition = "topLeft" | "topMiddle" | "topRight" | "middleLeft" | "middleMiddle" | "middleRight" | "bottomLeft" | "bottomMiddle" | "bottomRight";

export type SINGLE_KNOB_MODE = "NONE" | "STATIC" | "VOLUME_GRADIENT";

export type SINGLE_LOGO_MODE = "NONE" | "STATIC" | "RAINBOW" | "BREATH";

export type SINGLE_SLIDER_LABEL_MODE = "NONE" | "STATIC";

export type SINGLE_SLIDER_MODE = "NONE" | "STATIC" | "STATIC_GRADIENT" | "VOLUME_GRADIENT";

export type VolumeButton = "mute" | "next" | "prev" | "stop" | "playPause";

export type WaveLinkCommandTarget = "Input" | "Channel" | "Mix" | "Output";

export type WsEventUnion = WsAssignmentChangedEvent | WsButtonEvent | WsDeviceConnectedEvent | WsDeviceDisconnectedEvent | WsDeviceRenamedEvent | WsKnobEvent | WsLightingChangedEvent | WsProfileSwitchedEvent | WsVisualColorsChangedEvent | DeviceSnapshotDto;
