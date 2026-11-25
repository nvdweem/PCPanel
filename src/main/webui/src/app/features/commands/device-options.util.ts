import { SelectOption } from '../../ui';

/** An audio device as the device pickers list it. */
export interface NamedDevice {
  id: string;
  name: string;
}

/** Whether two device names are the same apart from letter case, compared by the user's locale. */
const sameName = (a: string, b: string) => a.localeCompare(b, undefined, { sensitivity: 'accent' }) === 0;

/** How many of {@code devices} carry {@code name}. */
function nameCount(devices: NamedDevice[], name: string): number {
  return devices.filter(d => sameName(d.name, name)).length;
}

/** Whether two of {@code devices} share a name. */
export function hasDuplicateNames(devices: NamedDevice[]): boolean {
  return devices.some(d => nameCount(devices, d.name) > 1);
}

/** The part of an endpoint id that differs between devices: `{0.0.0.00000000}.{guid}` → `{guid}`. */
export function shortEndpointId(id: string): string {
  const i = id.lastIndexOf('.{');
  return i >= 0 ? id.slice(i + 1) : id;
}

/**
 * The options of a device picker. Storing ids, every device is its own option, and a name two devices share
 * carries each one's id so they can be told apart. Storing names, a shared name is one option: the backend
 * switches to one of the devices carrying it, not one the user can choose. A name field holding an endpoint id
 * gets that device as an extra option, so the field reads as the device it points at.
 */
export function deviceSelectOptions(devices: NamedDevice[], storeIds: boolean, current?: unknown): SelectOption[] {
  const idLabel = (d: NamedDevice) => nameCount(devices, d.name) > 1 ? `${d.name} ${shortEndpointId(d.id)}` : d.name;
  if (storeIds) {
    return devices.map(d => ({ value: d.id, label: idLabel(d) }));
  }
  const opts: SelectOption[] = [];
  for (const d of devices) {
    if (opts.some(o => sameName(o.value, d.name))) continue;
    const count = nameCount(devices, d.name);
    opts.push(count > 1 ? { value: d.name, label: d.name, hint: `${count} devices` } : { value: d.name, label: d.name });
  }
  const byId = devices.find(d => d.id === current);
  if (byId) opts.unshift({ value: byId.id, label: idLabel(byId), hint: 'by id' });
  return opts;
}

/**
 * A stored device reference in the other form: a name becomes the id of the one device carrying it, an id the
 * name of its device. A reference that does not name exactly one listed device (blank, part of a name, a shared
 * name, an unplugged device) is kept unchanged; the backend resolves an id or a name either way.
 */
export function convertDeviceRef(value: string, devices: NamedDevice[], toIds: boolean): string {
  if (!value) return value;
  if (toIds) {
    const matches = devices.filter(d => sameName(d.name, value));
    return matches.length === 1 ? matches[0].id : value;
  }
  return devices.find(d => d.id === value)?.name ?? value;
}
