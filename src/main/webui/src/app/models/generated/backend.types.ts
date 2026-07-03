/* tslint:disable */
/* eslint-disable */

export interface AddDeejDeviceDto {
    baud?: number;
    name?: string;
    noiseReduction?: string;
    port: string;
}

export interface AnalogBand {
    color?: string;
    commands?: Commands;
    end: number;
    start: number;
}

export interface AnalogBandsCommandModule extends CommandModule {
}

export interface AnalogInputSpec {
    hasButton: boolean;
    id: string;
    index: number;
    kind: AnalogKind;
    label: string;
    lightOutputIndex?: number;
    sourceMax: number;
    sourceMin: number;
}

export interface AnalogOutputSpec {
    id: string;
    index: number;
    label: string;
    max: number;
    min: number;
}

export interface BandTransition {
    band: number;
    changed: boolean;
    fire: boolean;
}

export interface ButtonAction {
    overlayText?: string;
}

export interface Command {
    _type: "com.getpcpanel.commands.command.CommandNoOp" | "mqtt.publish" | "osc.send" | "output.http-request" | "analogbands.ranges" | "device.brightness" | "discord.join-voice" | "discord.leave-voice" | "discord.mute" | "discord.screen-share" | "discord.self-deafen" | "com.getpcpanel.discord.command.CommandDiscordSelfInputVolume" | "com.getpcpanel.discord.command.CommandDiscordSelfMute" | "com.getpcpanel.discord.command.CommandDiscordSelfOutputVolume" | "discord.toggle-video" | "com.getpcpanel.discord.command.CommandDiscordUserMute" | "com.getpcpanel.discord.command.CommandDiscordUserVolume" | "discord.volume" | "homeassistant.action" | "homeassistant.value" | "keyboard.keystroke" | "keyboard.media" | "obs.action" | "obs.mute-source" | "obs.set-scene" | "obs.set-source-volume" | "profile.switch" | "program.end-program" | "program.run" | "program.shortcut" | "voicemeeter.advanced" | "voicemeeter.advanced-button" | "com.getpcpanel.commands.command.CommandVoiceMeeterBasic" | "com.getpcpanel.commands.command.CommandVoiceMeeterBasicButton" | "com.getpcpanel.commands.command.CommandVolumeApplicationDeviceToggle" | "volume.default-device" | "volume.default-device-advanced" | "volume.default-device-toggle" | "com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggleAdvanced" | "volume.device" | "volume.device-mute" | "volume.focus" | "volume.focus-mute" | "volume.process" | "volume.process-mute" | "wavelink.add-focus-to-channel" | "wavelink.change-level" | "wavelink.change-mute" | "com.getpcpanel.wavelink.command.CommandWaveLinkChannelEffect" | "wavelink.main-output";
}

export interface CommandAnalogBands extends Command, DialAction {
    _type: "analogbands.ranges";
    bands?: AnalogBand[];
}

export interface CommandBrightness extends Command, DialAction {
    _type: "device.brightness";
}

export interface CommandConverter {
}

export interface CommandDiscord extends Command {
    _type: "discord.join-voice" | "discord.leave-voice" | "discord.mute" | "discord.screen-share" | "discord.self-deafen" | "com.getpcpanel.discord.command.CommandDiscordSelfInputVolume" | "com.getpcpanel.discord.command.CommandDiscordSelfMute" | "com.getpcpanel.discord.command.CommandDiscordSelfOutputVolume" | "discord.toggle-video" | "com.getpcpanel.discord.command.CommandDiscordUserMute" | "com.getpcpanel.discord.command.CommandDiscordUserVolume" | "discord.volume";
}

export interface CommandDiscordJoinVoice extends CommandDiscord, ButtonAction {
    _type: "discord.join-voice";
    channelId?: string;
    channelName?: string;
}

export interface CommandDiscordLeaveVoice extends CommandDiscord, ButtonAction {
    _type: "discord.leave-voice";
}

export interface CommandDiscordMute extends CommandDiscord, ButtonAction {
    _type: "discord.mute";
    muteType?: MuteType;
    target?: string;
}

export interface CommandDiscordScreenShare extends CommandDiscord, ButtonAction {
    _type: "discord.screen-share";
    mode?: Mode;
    processName?: string[];
}

export interface CommandDiscordSelfDeafen extends CommandDiscord, ButtonAction {
    _type: "discord.self-deafen";
    muteType?: MuteType;
}

export interface CommandDiscordSelfInputVolume extends CommandDiscord, DialAction {
    _type: "com.getpcpanel.discord.command.CommandDiscordSelfInputVolume";
    unmuteOnChange: boolean;
}

export interface CommandDiscordSelfMute extends CommandDiscord, ButtonAction {
    _type: "com.getpcpanel.discord.command.CommandDiscordSelfMute";
    muteType?: MuteType;
}

export interface CommandDiscordSelfOutputVolume extends CommandDiscord, DialAction {
    _type: "com.getpcpanel.discord.command.CommandDiscordSelfOutputVolume";
    undeafenOnChange: boolean;
}

export interface CommandDiscordToggleVideo extends CommandDiscord, ButtonAction {
    _type: "discord.toggle-video";
}

export interface CommandDiscordUserMute extends CommandDiscord, ButtonAction {
    _type: "com.getpcpanel.discord.command.CommandDiscordUserMute";
    muteType?: MuteType;
    username?: string;
}

export interface CommandDiscordUserVolume extends CommandDiscord, DialAction {
    _type: "com.getpcpanel.discord.command.CommandDiscordUserVolume";
    username?: string;
}

export interface CommandDiscordVolume extends CommandDiscord, DialAction {
    _type: "discord.volume";
    clearMuteOnChange: boolean;
    target?: string;
}

export interface CommandEndProgram extends Command, ButtonAction {
    _type: "program.end-program";
    name: string;
    specific: boolean;
}

export interface CommandHomeAssistant extends Command {
    _type: "homeassistant.action" | "homeassistant.value";
    server?: string;
}

export interface CommandHomeAssistantAction extends CommandHomeAssistant, ButtonAction {
    _type: "homeassistant.action";
    action: string;
}

export interface CommandHomeAssistantValue extends CommandHomeAssistant, DialAction {
    _type: "homeassistant.value";
    action: string;
    formula?: string;
    max?: number;
    min?: number;
}

export interface CommandHttpRequest extends CommandValueOutput {
    _type: "output.http-request";
    body?: string;
    headers?: string;
    method: string;
    url: string;
}

export interface CommandKeystroke extends Command, ButtonAction {
    _type: "keyboard.keystroke";
    keystroke: string;
    text: string;
    type: KeystrokeType;
}

export interface CommandMedia extends Command, ButtonAction {
    _type: "keyboard.media";
    apps: string[];
    button: VolumeButton;
    spotify: boolean;
}

export interface CommandModule {
}

export interface CommandMqttPublish extends CommandValueOutput {
    _type: "mqtt.publish";
    payload: string;
    topic: string;
}

export interface CommandNoOp extends Command, ButtonAction, DialAction {
    _type: "com.getpcpanel.commands.command.CommandNoOp";
}

export interface CommandObs extends Command {
    _type: "obs.action" | "obs.mute-source" | "obs.set-scene" | "obs.set-source-volume";
}

export interface CommandObsAction extends CommandObs, ButtonAction {
    _type: "obs.action";
    action: ObsActionType;
}

export interface CommandObsMuteSource extends CommandObs, ButtonAction {
    _type: "obs.mute-source";
    source: string;
    type: MuteType;
}

export interface CommandObsSetScene extends CommandObs, ButtonAction {
    _type: "obs.set-scene";
    scene: string;
}

export interface CommandObsSetSourceVolume extends CommandObs, DialAction {
    _type: "obs.set-source-volume";
    sourceName: string;
}

export interface CommandOscSend extends CommandValueOutput {
    _type: "osc.send";
    address: string;
}

export interface CommandProfile extends Command, DeviceAction {
    _type: "profile.switch";
    profile?: string;
}

export interface CommandRun extends Command, ButtonAction {
    _type: "program.run";
    command: string;
}

export interface Commands {
    commands: Command[];
    type?: CommandsType;
}

export interface CommandShortcut extends Command, ButtonAction {
    _type: "program.shortcut";
    shortcut: string;
}

export interface CommandValueOutput extends Command, DialAction, ButtonAction {
    _type: "mqtt.publish" | "osc.send" | "output.http-request";
    formula?: string;
    max?: number;
    min?: number;
}

export interface CommandVoiceMeeter extends Command {
    _type: "voicemeeter.advanced" | "voicemeeter.advanced-button" | "com.getpcpanel.commands.command.CommandVoiceMeeterBasic" | "com.getpcpanel.commands.command.CommandVoiceMeeterBasicButton";
}

export interface CommandVoiceMeeterAdvanced extends CommandVoiceMeeter, DialAction {
    _type: "voicemeeter.advanced";
    ct: DialControlMode;
    fullParam: string;
}

export interface CommandVoiceMeeterAdvancedButton extends CommandVoiceMeeter, ButtonAction {
    _type: "voicemeeter.advanced-button";
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
    _type: "com.getpcpanel.commands.command.CommandVolumeApplicationDeviceToggle" | "volume.default-device" | "volume.default-device-advanced" | "volume.default-device-toggle" | "com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggleAdvanced" | "volume.device" | "volume.device-mute" | "volume.focus" | "volume.focus-mute" | "volume.process" | "volume.process-mute";
}

export interface CommandVolumeApplicationDeviceToggle extends CommandVolume, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandVolumeApplicationDeviceToggle";
    currentIdx: number;
    devices: DeviceSet[];
    followFocus: boolean;
    processes: string[];
}

export interface CommandVolumeDefaultDevice extends CommandVolume, ButtonAction {
    _type: "volume.default-device";
    deviceId: string;
}

export interface CommandVolumeDefaultDeviceAdvanced extends CommandVolume, ButtonAction {
    _type: "volume.default-device-advanced";
    communicationPb: string;
    communicationRec: string;
    mediaPb: string;
    mediaRec: string;
    name: string;
}

export interface CommandVolumeDefaultDeviceAdvancedBuilder {
}

export interface CommandVolumeDefaultDeviceToggle extends CommandVolume, ButtonAction {
    _type: "volume.default-device-toggle";
    currentIdx: number;
    devices: string[];
}

export interface CommandVolumeDefaultDeviceToggleAdvanced extends CommandVolume, ButtonAction {
    _type: "com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggleAdvanced";
    currentIdx: number;
    devices: DeviceSet[];
}

export interface CommandVolumeDevice extends CommandVolume, DialAction {
    _type: "volume.device";
    deviceId: string;
    isUnMuteOnVolumeChange: boolean;
    unMuteOnVolumeChange: boolean;
}

export interface CommandVolumeDeviceMute extends CommandVolume, ButtonAction {
    _type: "volume.device-mute";
    deviceId: string;
    muteType: MuteType;
}

export interface CommandVolumeFocus extends CommandVolume, DialAction {
    _type: "volume.focus";
}

export interface CommandVolumeFocusMute extends CommandVolume, ButtonAction {
    _type: "volume.focus-mute";
    muteType: MuteType;
}

export interface CommandVolumeProcess extends CommandVolume, DialAction {
    _type: "volume.process";
    device: string;
    isUnMuteOnVolumeChange: boolean;
    processName: string[];
    unMuteOnVolumeChange: boolean;
}

export interface CommandVolumeProcessMute extends CommandVolume, ButtonAction {
    _type: "volume.process-mute";
    muteType: MuteType;
    processName: string[];
}

export interface CommandWaveLink extends Command {
    _type: "wavelink.add-focus-to-channel" | "wavelink.change-level" | "wavelink.change-mute" | "com.getpcpanel.wavelink.command.CommandWaveLinkChannelEffect" | "wavelink.main-output";
}

export interface CommandWaveLinkAddFocusToChannel extends CommandWaveLink, ButtonAction {
    _type: "wavelink.add-focus-to-channel";
    id?: string;
    name?: string;
}

export interface CommandWaveLinkChange extends CommandWaveLink {
    _type: "wavelink.change-level" | "wavelink.change-mute";
    commandType: WaveLinkCommandTarget;
    id1?: string;
    id2?: string;
}

export interface CommandWaveLinkChangeLevel extends CommandWaveLinkChange, DialAction {
    _type: "wavelink.change-level";
}

export interface CommandWaveLinkChangeMute extends CommandWaveLinkChange, ButtonAction {
    _type: "wavelink.change-mute";
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
    _type: "wavelink.main-output";
    id?: string;
    name?: string;
}

export interface ControlAssignmentsUpdateDto {
    analog?: Commands;
    button?: Commands;
    dblButton?: Commands;
    knobSetting?: KnobSetting;
    releaseButton?: Commands;
}

export interface DeviceAction {
}

export interface DeviceActionParameters {
    device: string;
}

export interface DeviceCommandModule extends CommandModule {
}

export interface DeviceDescriptor {
    analogInputs: AnalogInputSpec[];
    analogOutputs: AnalogOutputSpec[];
    deviceKindId: string;
    digitalInputs: DigitalInputSpec[];
    displayName: string;
    globalLighting?: GlobalLightingSpec;
    lightOutputs: LightOutputSpec[];
    providerId: string;
}

export interface DeviceDto {
    analogCount: number;
    buttonCount: number;
    connected: boolean;
    currentProfile: string;
    descriptor?: DeviceDescriptor;
    deviceType?: DeviceType;
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
    baseLayerSnapshot?: ProfileSnapshotDto;
    buttonCount: number;
    currentProfile: string;
    currentProfileSnapshot: ProfileSnapshotDto;
    descriptor: DeviceDescriptor;
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

export interface DigitalInputSpec {
    id: string;
    index: number;
    label: string;
    standalone: boolean;
}

export interface DiscordAuth {
    accessToken?: string;
    expiresAtEpochMs?: number;
    refreshToken?: string;
    scope?: string;
    userId?: string;
    userName?: string;
}

export interface DiscordCommandModule extends CommandModule {
}

export interface DiscordSeenUser {
    displayName: string;
    id: string;
    username: string;
}

export interface DiscordSettings {
    clientId?: string;
    clientSecret?: string;
    enabled: boolean;
    redirectUri?: string;
}

export interface DiscordStatusDto {
    authenticated: boolean;
    authorized: boolean;
    connected: boolean;
    enabled: boolean;
    lastError?: string;
    user?: string;
}

export interface DiscordUserDto {
    displayName: string;
    friend: boolean;
    id: string;
    inVoice: boolean;
    username: string;
}

export interface DiscordVoiceChannelDto {
    guildName: string;
    id: string;
    name: string;
}

export interface EngineCommandModule extends CommandModule {
}

export interface FocusVolumeOverride {
    includeSource: boolean;
    sources: string[];
    targets: FocusVolumeTarget[];
}

export interface FocusVolumeTarget {
    command: Command;
}

export interface GlobalLightingSpec {
    brightnessMax: number;
    brightnessMin: number;
    firmwareAnimated: boolean;
    hasGlobalBrightness: boolean;
    supportedModes: string[];
}

export interface HomeAssistantCommandModule extends CommandModule {
}

export interface HomeAssistantServer {
    id: string;
    name: string;
    token?: string;
    url: string;
}

export interface HomeAssistantServerStatus {
    connected: boolean;
    id: string;
    name: string;
    url?: string;
}

export interface HomeAssistantSettings {
    availability: boolean;
    baseTopic: string;
    enableDiscovery: boolean;
}

export interface KeyboardCommandModule extends CommandModule {
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

export interface LightOutputSpec {
    colorModel: LightColorModel;
    group: LightGroupKind;
    id: string;
    index: number;
    label: string;
    supportedElementModes: string[];
}

export interface MidiDeviceDto {
    connected: boolean;
    id: string;
    name: string;
}

export interface MqttCommandModule extends CommandModule {
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

export interface ObsCommandModule extends CommandModule {
}

export interface OnboardingDto {
    changelogUrl: string;
    intent: string;
    version: string;
}

export interface OSCBinding {
    address: string;
    max: number;
    min: number;
    toggle: boolean;
}

export interface OscCommandModule extends CommandModule {
}

export interface OSCConnectionInfo {
    host: string;
    port: number;
}

export interface OutputCommandModule extends CommandModule {
}

export interface ProcessDto {
    icon?: string;
    name: string;
    path: string;
    pid: number;
}

export interface ProfileCommandModule extends CommandModule {
}

export interface ProfileDto {
    isMainProfile: boolean;
    name: string;
}

export interface ProfileSettingsDto {
    activateApplications: string[];
    focusBackOnLost: boolean;
    isBaseLayer: boolean;
    isMainProfile: boolean;
    name: string;
}

export interface ProfileSnapshotDto {
    buttonData: { [index: string]: Commands };
    dblButtonData: { [index: string]: Commands };
    dialData: { [index: string]: Commands };
    knobSettings: { [index: string]: KnobSetting };
    name: string;
    releaseButtonData: { [index: string]: Commands };
}

export interface ProgramCommandModule extends CommandModule {
}

export interface SerialPortDto {
    description: string;
    port: string;
}

export interface SettingsDto {
    dblClickInterval: number;
    focusVolumeOverrides: FocusVolumeOverride[];
    forceVolume: boolean;
    homeAssistantDebounceMs?: number;
    homeAssistantServers: HomeAssistantServer[];
    mainUIIcons: boolean;
    mqtt: MqttSettings;
    obsAddress: string;
    obsEnabled: boolean;
    obsPassword: string;
    obsPort: string;
    openBrowserOnStartup: boolean;
    oscConnections: OSCConnectionInfo[];
    oscEnabled: boolean;
    oscListenPort: number;
    overlayBackgroundColor: string;
    overlayBarBackgroundColor: string;
    overlayBarColor: string;
    overlayBarCornerRounding?: number;
    overlayBarFollowsLight: boolean;
    overlayBarHeight?: number;
    overlayContentPadding?: number;
    overlayElementGap?: number;
    overlayEnabled: boolean;
    overlayFontBold: boolean;
    overlayFontFamily?: string;
    overlayIconSize?: number;
    overlayPadding?: number;
    overlayPosition?: OverlayPosition;
    overlayShowAppName: boolean;
    overlayShowIcon: boolean;
    overlayShowNumber: boolean;
    overlayTextColor: string;
    overlayTextSize?: number;
    overlayUseLog: boolean;
    overlayWidth?: number;
    overlayWindowCornerRounding: number;
    preventClickWhenDblClick: boolean;
    preventSliderTwitchDelay?: number;
    sendOnlyIfDelta?: number;
    skipControlledFocusApps: boolean;
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

export interface VoiceMeeterCommandModule extends CommandModule {
}

export interface VolumeCommandModule extends CommandModule {
}

export interface WaveLinkAppDto {
    id: string;
    name: string;
}

export interface WaveLinkChannelDto {
    apps: WaveLinkAppDto[];
    effects: WaveLinkEffectDto[];
    id: string;
    image?: string;
    mixes: WaveLinkMixDto[];
    name?: string;
    type?: string;
}

export interface WaveLinkCommandModule extends CommandModule {
}

export interface WaveLinkEffectDto {
    id: string;
    isEnabled: boolean;
    name?: string;
}

export interface WaveLinkInputDto {
    id: string;
    name?: string;
}

export interface WaveLinkMixDto {
    id: string;
    name?: string;
}

export interface WaveLinkOutputDto {
    id: string;
    name?: string;
}

export interface WaveLinkResponseDto {
    channels: WaveLinkChannelDto[];
    inputs: WaveLinkInputDto[];
    mixes: WaveLinkMixDto[];
    outputs: WaveLinkOutputDto[];
}

export interface WaveLinkSettings {
    controlledVolumePercent?: number;
    enabled: boolean;
    enforceControlledVolume: boolean;
    focusVolumeRedirect?: boolean;
}

export interface WsAssignmentChangedEvent extends WsEvent {
    commands: Commands;
    index: number;
    kind: Kinds;
    profile: string;
    serial: string;
    type: "assignment_changed";
}

export interface WsButtonEvent extends WsEvent {
    button: number;
    pressed: boolean;
    serial: string;
    type: "button_press";
}

export interface WsControlSettingChangedEvent extends WsEvent {
    index: number;
    profile: string;
    serial: string;
    settings: KnobSetting;
    type: "control_setting_changed";
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
    type: "device_snapshot" | "assignment_changed" | "button_press" | "control_setting_changed" | "device_connected" | "device_disconnected" | "device_renamed" | "knob_rotate" | "lighting_changed" | "new_version_available" | "profile_switched" | "visual_colors_changed";
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

export interface WsNewVersionAvailableEvent extends WsEvent {
    type: "new_version_available";
    url: string;
    version: string;
}

export interface WsProfileSwitchedEvent extends WsEvent {
    baseLayerSnapshot?: ProfileSnapshotDto;
    dialColors: string[];
    lightingConfig: LightingConfig;
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

export type AnalogKind = "KNOB" | "SLIDER" | "ENCODER";

export type ButtonControlMode = "ENABLE" | "DISABLE" | "TOGGLE" | "STRING";

export type ButtonType = "MONO" | "MUTE" | "SOLO" | "MC" | "EQ" | "A1" | "A2" | "A3" | "A4" | "A5" | "B1" | "B2" | "B3" | "SEL" | "MIXA" | "MIXB" | "REPEAT" | "COMPOSITE";

export type CommandsType = "allAtOnce" | "sequential";

export type ControlType = "STRIP" | "BUS";

export type DeviceType = "PCPANEL_RGB" | "PCPANEL_MINI" | "PCPANEL_PRO";

export type DialControlMode = "NEG_12_TO_12" | "ZERO_TO_10" | "NEG_40_TO_12" | "NEG_60_TO_12" | "NEG_INF_TO_12" | "NEG_INF_TO_ZERO";

export type DialType = "GAIN" | "AUDIBILITY" | "COMP" | "GATE" | "LIMIT" | "EQGAIN1" | "EQGAIN2" | "EQGAIN3" | "REVERB" | "DELAY" | "FX1" | "FX2" | "RETURNREVERB" | "RETURNDELAY" | "RETURNFX1" | "RETURNFX2";

export type DiscoveryMode = "AUTO" | "MANUAL";

export type KeystrokeType = "KEY" | "TEXT";

export type Kinds = "dial" | "button" | "dblbutton" | "releasebutton";

export type LightColorModel = "NONE" | "MONOCHROME" | "RGB" | "SCALAR_0_254";

export type LightGroupKind = "DIAL" | "SLIDER" | "SLIDER_LABEL" | "LOGO" | "GENERIC";

export type LightingMode = "ALL_COLOR" | "ALL_RAINBOW" | "ALL_WAVE" | "ALL_BREATH" | "SINGLE_COLOR" | "CUSTOM";

export type Mode = "SCREEN" | "PROCESS" | "FOCUS";

export type MuteType = "mute" | "unmute" | "toggle";

export type ObsActionType = "START_STREAM" | "STOP_STREAM" | "TOGGLE_STREAM" | "START_RECORD" | "STOP_RECORD" | "TOGGLE_RECORD" | "TOGGLE_RECORD_PAUSE" | "START_VIRTUAL_CAM" | "STOP_VIRTUAL_CAM" | "TOGGLE_VIRTUAL_CAM" | "TOGGLE_REPLAY_BUFFER" | "SAVE_REPLAY_BUFFER";

export type OverlayPosition = "topLeft" | "topMiddle" | "topRight" | "middleLeft" | "middleMiddle" | "middleRight" | "bottomLeft" | "bottomMiddle" | "bottomRight";

export type SINGLE_KNOB_MODE = "NONE" | "STATIC" | "VOLUME_GRADIENT";

export type SINGLE_LOGO_MODE = "NONE" | "STATIC" | "RAINBOW" | "BREATH";

export type SINGLE_SLIDER_LABEL_MODE = "NONE" | "STATIC";

export type SINGLE_SLIDER_MODE = "NONE" | "STATIC" | "STATIC_GRADIENT" | "VOLUME_GRADIENT";

export type VolumeButton = "mute" | "next" | "prev" | "stop" | "playPause";

export type WaveLinkCommandTarget = "Input" | "Channel" | "Mix" | "Output";

export type WsEventUnion = WsAssignmentChangedEvent | WsButtonEvent | WsDeviceConnectedEvent | WsDeviceDisconnectedEvent | WsDeviceRenamedEvent | WsKnobEvent | WsLightingChangedEvent | WsProfileSwitchedEvent | WsVisualColorsChangedEvent | DeviceSnapshotDto | WsControlSettingChangedEvent | WsNewVersionAvailableEvent;
