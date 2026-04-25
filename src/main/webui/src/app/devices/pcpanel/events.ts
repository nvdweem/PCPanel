export type ControlType = 'slider' | 'logo' | 'dial';

export interface DeviceClickEvent {
  idx: number;
  type: ControlType;
  contextClicked: boolean;

  event: MouseEvent;
}
