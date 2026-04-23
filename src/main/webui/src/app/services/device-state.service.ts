import { computed, Injectable, OnDestroy, Signal, signal } from '@angular/core';
import { DeviceSnapshotDto, ProfileSnapshotDto, WsAssignmentChangedEvent, WsEvent, WsEventUnion } from '../models/generated/backend.types';

type DeviceMap = Record<string, DeviceSnapshotDto>;

/**
 * Single source of truth for all connected device state.
 *
 * Populated entirely from WebSocket events — no HTTP reads needed for display.
 * HTTP is used only for mutations (save, rename, switch profile, etc.) which
 * trigger patch events from the backend that update this service automatically.
 */
@Injectable({providedIn: 'root'})
export class DeviceStateService implements OnDestroy {
  private readonly _devices = signal<DeviceMap>({});
  private socket: WebSocket | null = null;
  private reconnectTimer?: ReturnType<typeof setTimeout>;
  private destroyed = false;

  /** All currently connected devices as a readonly signal. */
  readonly devices = this._devices.asReadonly();
  readonly deviceCount = computed(() => Object.keys(this.devices()).length);

  /**
   * True once at least one full device state event has been received.
   * Components can use this to decide whether to show a loading state.
   */
  readonly ready = signal(false);

  readonly connected = signal(false);
  readonly reconnecting = signal(false);
  readonly lastError = signal<string | null>(null);

  constructor() {
    this.connect();
  }

  snapshotFor(serial: () => (string | null | undefined)): Signal<DeviceSnapshotDto | null> {
    return computed(() => {
      const s = serial();
      return s ? this._devices()[s] : null;
    });
  }

  // Own the websocket lifecycle to avoid missing early events before subscription.
  private connect(): void {
    if (this.destroyed) return;

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = undefined;
    }

    this.reconnecting.set(this.socket !== null);
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = `${protocol}//${window.location.host}/ws/events`;
    this.socket = new WebSocket(url);

    this.socket.onopen = () => {
      this.connected.set(true);
      this.reconnecting.set(false);
      this.lastError.set(null);
    };

    this.socket.onmessage = (event) => {
      if (typeof event.data !== 'string') {
        this.lastError.set('Received non-text websocket payload');
        return;
      }

      const parsed = this.tryParseEvent(event.data);
      if (parsed) {
        this.apply(parsed);
      }
    };

    this.socket.onclose = () => {
      this.connected.set(false);
      if (this.destroyed) return;

      this.reconnecting.set(true);
      this.reconnectTimer = setTimeout(() => this.connect(), 3000);
    };

    this.socket.onerror = () => {
      this.lastError.set('WebSocket transport error');
      this.socket?.close();
    };
  }

  private tryParseEvent(raw: string): WsEventUnion | null {
    try {
      const data: unknown = JSON.parse(raw);
      if (this.isWsEvent(data)) return data as WsEventUnion;
      this.lastError.set('Received malformed websocket event payload');
      return null;
    } catch {
      this.lastError.set('Failed to parse websocket event JSON');
      return null;
    }
  }

  private isWsEvent(value: unknown): value is WsEvent {
    if (!value || typeof value !== 'object') return false;
    const event = value as Record<string, unknown>;
    if (typeof event['type'] !== 'string') return false;

    switch (event['type']) {
      case 'device_connected':
      case 'device_snapshot':
      case 'device_disconnected':
      case 'knob_rotate':
      case 'button_press':
      case 'device_renamed':
      case 'profile_switched':
      case 'lighting_changed':
      case 'visual_colors_changed':
      case 'assignment_changed':
        return true;
      default:
        return false;
    }
  }

  // ── Event application ──────────────────────────────────────────────────────

  private apply(event: WsEventUnion): void {
    switch (event.type) {
      case 'device_connected':
        this.updateDevice(event.deviceSnapshot);
        break;
      case 'device_snapshot':
        this.updateDevice(event);
        break;
      case 'device_disconnected':
        this._devices.update(ds => {
          const c = {...ds};
          delete c[event.serial];
          return c;
        });
        break;

      case 'device_renamed':
        this._devices.update(patch(event.serial, d => ({...d, displayName: event.displayName})));
        break;

      case 'profile_switched':
        this._devices.update(patch(event.serial, d => ({
          ...d,
          currentProfile: event.profileName,
          currentProfileSnapshot: event.profileSnapshot,
          dialColors: event.dialColors,
          sliderLabelColors: event.sliderLabelColors,
          sliderColors: event.sliderColors,
          logoColor: event.logoColor,
        })));
        break;

      case 'lighting_changed':
        this._devices.update(patch(event.serial, d => ({
          ...d,
          lightingConfig: event.lightingConfig,
          dialColors: event.dialColors,
          sliderLabelColors: event.sliderLabelColors,
          sliderColors: event.sliderColors,
          logoColor: event.logoColor,
        })));
        break;

      case 'visual_colors_changed':
        this._devices.update(patch(event.serial, d => ({
          ...d,
          dialColors: event.dialColors,
          sliderLabelColors: event.sliderLabelColors,
          sliderColors: event.sliderColors,
          logoColor: event.logoColor,
        })));
        break;

      case 'assignment_changed':
        this._devices.update(patch(event.serial, d => ({
          ...d,
          currentProfileSnapshot: applyAssignment(d.currentProfileSnapshot, event),
        })));
        break;

      case 'knob_rotate':
        let analogValues = this.devices()[event.serial]?.analogValues;
        analogValues = analogValues ? [...analogValues] : [];
        analogValues[event.knob] = event.value;
        this._devices.update(patch(event.serial, d => ({
          ...d,
          analogValues,
        })));
        break;
    }
  }

  private updateDevice(event: DeviceSnapshotDto) {
    this._devices.update(ds => ({
      ...ds,
      [event.serial]: event,
    }));
    this.ready.set(true);
  }

  ngOnDestroy(): void {
    this.destroyed = true;

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = undefined;
    }

    this.socket?.close();
    this.socket = null;
    this.connected.set(false);
    this.reconnecting.set(false);
  }
}

// ── Pure helpers (no side effects) ────────────────────────────────────────────

function patch(
  serial: string,
  updater: (d: DeviceSnapshotDto) => DeviceSnapshotDto,
): (devices: DeviceMap) => DeviceMap {
  return devices => ({
    ...devices,
    [serial]: updater(devices[serial]),
  });
}

function applyAssignment(
  snapshot: ProfileSnapshotDto,
  event: WsAssignmentChangedEvent,
): ProfileSnapshotDto {
  switch (event.kind) {
    case 'dial':
      return {...snapshot, dialData: {...snapshot.dialData, [event.index]: event.commands}};
    case 'button':
      return {...snapshot, buttonData: {...snapshot.buttonData, [event.index]: event.commands}};
    case 'dblbutton':
      return {...snapshot, dblButtonData: {...snapshot.dblButtonData, [event.index]: event.commands}};
  }
}
