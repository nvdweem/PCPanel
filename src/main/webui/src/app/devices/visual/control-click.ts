/** Shared control-click contract emitted by the device renderers
 *  ({@link PcDeviceComponent} / {@link GenericDeviceComponent}) and consumed by
 *  the editor pages. Kept stable so callers are renderer-agnostic. */
export type ControlKind = 'dial' | 'slider' | 'logo';

export interface ControlClick {
  kind: ControlKind;
  /** analog index: knob = i, Pro slider = i+5, logo = 0 */
  index: number;
  contextClicked: boolean;
  /** when set, open this slot's tab instead of the default (from a press / double-press chip) */
  slot?: 'press' | 'dblpress';
  event: Event;
}
