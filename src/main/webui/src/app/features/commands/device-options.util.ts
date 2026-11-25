import { SelectOption } from '../../ui';

/** An audio device as the device pickers list it. */
export interface NamedDevice {
  id: string;
  name: string;
}

/** The backend matches names regardless of case, so two names differing only in case pick the same device. */
const nameKey = (name: string) => name.toLocaleLowerCase();

/** How many devices carry each name, keyed by {@link nameKey}. */
function nameCounts(devices: NamedDevice[]): Map<string, number> {
  const counts = new Map<string, number>();
  for (const d of devices) counts.set(nameKey(d.name), (counts.get(nameKey(d.name)) ?? 0) + 1);
  return counts;
}

/** Whether two of {@code devices} share a name. */
export function hasDuplicateNames(devices: NamedDevice[]): boolean {
  return [...nameCounts(devices).values()].some(n => n > 1);
}

/** The part of an endpoint id that differs between devices: `{0.0.0.00000000}.{guid}` → `{guid}`. */
export function shortEndpointId(id: string): string {
  const i = id.lastIndexOf('.{');
  return i >= 0 ? id.slice(i + 1) : id;
}

/**
 * The options of a device picker. Storing ids, every device is its own option, and a name two devices share
 * carries each one's id so they can be told apart. Storing names, a shared name is one option: it matches
 * every device carrying it. A name field still holding an endpoint id (configured before it stored names,
 * or with ids switched off) gets that device as an extra option, so the field reads as the device it points at.
 */
export function deviceSelectOptions(devices: NamedDevice[], storeIds: boolean, current?: unknown): SelectOption[] {
  const counts = nameCounts(devices);
  const shared = (d: NamedDevice) => (counts.get(nameKey(d.name)) ?? 0) > 1;
  const idLabel = (d: NamedDevice) => shared(d) ? `${d.name} ${shortEndpointId(d.id)}` : d.name;
  if (storeIds) {
    return devices.map(d => ({ value: d.id, label: idLabel(d) }));
  }
  const opts: SelectOption[] = [];
  const seen = new Set<string>();
  for (const d of devices) {
    const key = nameKey(d.name);
    if (seen.has(key)) continue;
    seen.add(key);
    opts.push(shared(d) ? { value: d.name, label: d.name, hint: `${counts.get(key)} devices` } : { value: d.name, label: d.name });
  }
  const byId = devices.find(d => d.id === current);
  if (byId) opts.unshift({ value: byId.id, label: idLabel(byId), hint: 'by id' });
  return opts;
}

/**
 * A stored device reference in the other form: a name becomes the id of the one device carrying it, an id the
 * name of its device. A reference that does not name exactly one listed device (blank, part of a name, a shared
 * name, an unplugged device) is kept as it is, and the backend resolves it as before.
 */
export function convertDeviceRef(value: string, devices: NamedDevice[], toIds: boolean): string {
  if (!value) return value;
  if (toIds) {
    const matches = devices.filter(d => nameKey(d.name) === nameKey(value));
    return matches.length === 1 ? matches[0].id : value;
  }
  return devices.find(d => d.id === value)?.name ?? value;
}
