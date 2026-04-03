// Core data models matching Java backend DTOs

export interface DeviceDto {
  serial: string;
  displayName: string;
  deviceType: 'PCPANEL_RGB' | 'PCPANEL_MINI' | 'PCPANEL_PRO';
  analogCount: number;
  buttonCount: number;
  hasLogoLed: boolean;
  currentProfile: string;
  profiles: string[];
}

export interface ProfileDto {
  name: string;
  isMainProfile: boolean;
}

export interface KnobSetting {
  minTrim: number;
  maxTrim: number;
  logarithmic: boolean;
  overlayIcon: string;
  buttonDebounce: number;
}

export interface LightingConfig {
  lightingMode: 'ALL_COLOR' | 'ALL_RAINBOW' | 'ALL_WAVE' | 'ALL_BREATH' | 'SINGLE_COLOR' | 'CUSTOM';
  individualColors: string[];
  volumeBrightnessTrackingEnabled: boolean[];
  allColor: string;
  rainbowPhaseShift: number;
  rainbowBrightness: number;
  rainbowSpeed: number;
  rainbowReverse: number;
  rainbowVertical: number;
  waveHue: number;
  waveBrightness: number;
  waveSpeed: number;
  waveReverse: number;
  waveBounce: number;
  breathHue: number;
  breathBrightness: number;
  breathSpeed: number;
  globalBrightness: number;
}

export interface AudioDevice {
  name: string;
  id: string;
  volume: number;
  muted: boolean;
  output: boolean;
  input: boolean;
}

export interface AudioSession {
  pid: number;
  title: string;
  volume: number;
  muted: boolean;
  icon: string | null;
}

export interface SettingsDto {
  mainUIIcons: boolean;
  startupVersionCheck: boolean;
  forceVolume: boolean;
  dblClickInterval: number;
  preventClickWhenDblClick: boolean;
  preventSliderTwitchDelay: number | null;
  sliderRollingAverage: number | null;
  sendOnlyIfDelta: number | null;
  workaroundsOnlySliders: boolean;
  obsEnabled: boolean;
  obsAddress: string;
  obsPort: string;
  obsPassword: string;
  voicemeeterEnabled: boolean;
  voicemeeterPath: string;
  oscListenPort: number | null;
  overlayEnabled: boolean;
  overlayUseLog: boolean;
  overlayShowNumber: boolean;
  overlayBackgroundColor: string;
  overlayTextColor: string;
  overlayBarColor: string;
  overlayBarBackgroundColor: string;
  overlayWindowCornerRounding: number | null;
  overlayBarHeight: number | null;
  overlayBarCornerRounding: number | null;
  overlayPosition: string | null;
  overlayPadding: number | null;
}

export interface MqttSettings {
  enabled: boolean;
  host: string;
  port: number;
  username: string;
  password: string;
  baseTopic: string;
  homeAssistant: boolean;
}

// WebSocket event types
export interface WsEvent {
  type: 'device_connected' | 'device_disconnected' | 'knob_rotate' | 'button_press';
  serial: string;
}

export interface WsKnobEvent extends WsEvent {
  knob: number;
  value: number;
}

export interface WsButtonEvent extends WsEvent {
  button: number;
  pressed: boolean;
}
