import { SelectOption } from '../../ui';

/** An audio device as the device pickers list it. */
export interface NamedDevice {
  id: string;
  name: string;
}

/** Whether two device names are the same apart from letter case, compared by the user's locale. */
const sameName = (a: string, b: string) => a.localeCompare(b, undefined, { sensitivity: 'accent' }) === 0;

/** The part of an endpoint id that differs between devices: `{0.0.0.00000000}.{guid}` → `{guid}`. */
function shortEndpointId(id: string): string {
  const i = id.lastIndexOf('.{');
  return i >= 0 ? id.slice(i + 1) : id;
}

/**
 * The options of a device picker: one per device, by id. A name two devices share carries each one's id, so they can
 * be told apart. A field that remembers the name of the device it was set to ({@code currentName}) offers an id no
 * listed device has under that name.
 */
export function deviceSelectOptions(devices: NamedDevice[], current?: unknown, currentName?: string): SelectOption[] {
  const shared = (d: NamedDevice) => devices.filter(o => sameName(o.name, d.name)).length > 1;
  const opts: SelectOption[] = devices.map(d => ({ value: d.id, label: shared(d) ? `${d.name} ${shortEndpointId(d.id)}` : d.name }));
  if (currentName && typeof current === 'string' && current && !devices.some(d => d.id === current)) {
    opts.unshift({ value: current, label: currentName, hint: 'offline' });
  }
  return opts;
}
