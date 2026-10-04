import { ChangeDetectionStrategy, Component, computed, effect, inject, model, signal, untracked } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DeviceStateService } from '../../services/device-state.service';
import { IconComponent, ModalComponent, ToastService } from '../../ui';

interface ControlCheck { index: number; label: string; hasButton: boolean; min: boolean; max: boolean; press: boolean; }

/** Raw analog values (0–255) this close to an end count as reaching it. */
const END_MARGIN = 4;

/**
 * "Test my panel": while open, the panel's controls act on nothing (the backend holds them back), the lights run
 * through red, green, blue, white and each light on its own, and every knob and slider is ticked off once it has
 * been turned to both ends (and pressed, for knobs).
 */
@Component({
  selector: 'pc-panel-test',
  standalone: true,
  imports: [ModalComponent, IconComponent],
  template: `
    <pc-modal [open]="open()" heading="Test my panel" [width]="560" (dismiss)="close()">
      <div class="pt">
        @if (serial(); as s) {
          @if (serials().length > 1) {
            <div class="pt-devices">
              @for (d of serials(); track d) {
                <button class="pc-chip" [class.dashed]="d !== s" (click)="select(d)">{{ name(d) }}</button>
              }
            </div>
          }
          <div class="pt-step">
            <div class="pt-title">1 · Lights</div>
            <div class="pt-sub">Every light turns red, green, blue and white, then lights up on its own, one after the other.</div>
            <button class="pc-btn ghost" (click)="replayLights()"><pc-icon name="refresh" [size]="13"></pc-icon> Play again</button>
          </div>
          <div class="pt-step">
            <div class="pt-title">2 · Controls</div>
            <div class="pt-sub">Turn every knob and slider all the way down and all the way up, and press every knob. Your volumes don't change while this is open.</div>
            <div class="pt-grid">
              @for (c of checks(); track c.index) {
                <div class="pt-ctl" [class.done]="c.min && c.max && (!c.hasButton || c.press)">
                  <span class="pt-name mono">{{ c.label }}</span>
                  <span class="pt-chk" [class.on]="c.min">min</span>
                  <span class="pt-chk" [class.on]="c.max">max</span>
                  @if (c.hasButton) { <span class="pt-chk" [class.on]="c.press">press</span> }
                </div>
              }
            </div>
            <div class="pt-sum">{{ doneCount() }} of {{ checks().length }} controls checked</div>
          </div>
        } @else {
          <p class="pt-sub">Connect a panel to test it.</p>
        }
        <div class="pt-actions"><button class="pc-btn primary" (click)="close()">Done</button></div>
      </div>
    </pc-modal>
  `,
  styles: [`
    .pt { display: flex; flex-direction: column; gap: 18px; }
    .pt-devices { display: flex; gap: 6px; flex-wrap: wrap; }
    .pt-step { display: flex; flex-direction: column; gap: 8px; align-items: flex-start; }
    .pt-title { font-weight: 600; font-size: 13.5px; }
    .pt-sub { font-size: 12px; color: var(--text-3); line-height: 1.45; }
    .pt-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 6px; width: 100%; }
    .pt-ctl { display: flex; align-items: center; gap: 5px; padding: 7px 9px; border: 1px solid var(--line); border-radius: var(--r-md, 8px); }
    .pt-ctl.done { border-color: var(--accent); background: var(--accent-tint-soft); }
    .pt-name { font-size: 12px; margin-right: auto; }
    .pt-chk { font-size: 10px; font-family: var(--font-mono); padding: 2px 5px; border-radius: 4px; color: var(--text-3); background: rgba(255,255,255,0.04); }
    .pt-chk.on { color: var(--accent-ink); background: var(--accent); }
    .pt-sum { font-size: 12px; color: var(--text-2); }
    .pt-actions { display: flex; justify-content: flex-end; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelTestComponent {
  private readonly http = inject(HttpClient);
  private readonly state = inject(DeviceStateService);
  private readonly toast = inject(ToastService);

  readonly open = model(false);

  readonly serials = computed(() => Object.keys(this.state.devices()));
  private readonly chosen = signal<string | null>(null);
  readonly serial = computed(() => {
    const c = this.chosen();
    return c && this.serials().includes(c) ? c : this.serials()[0] ?? null;
  });
  private readonly progress = signal<Record<number, { min: boolean; max: boolean; press: boolean }>>({});
  private testing: string | null = null;

  readonly checks = computed<ControlCheck[]>(() => {
    const s = this.serial();
    const inputs = s ? this.state.devices()[s]?.descriptor?.analogInputs ?? [] : [];
    const p = this.progress();
    return inputs.map(i => ({
      index: i.index, label: i.label || `#${i.index + 1}`, hasButton: i.hasButton,
      min: !!p[i.index]?.min, max: !!p[i.index]?.max, press: !!p[i.index]?.press,
    }));
  });
  readonly doneCount = computed(() => this.checks().filter(c => c.min && c.max && (!c.hasButton || c.press)).length);

  constructor() {
    // Start or end the backend test as the dialog opens, closes or switches device.
    effect(() => {
      const want = this.open() ? this.serial() : null;
      untracked(() => {
        if (want === this.testing) return;
        if (this.testing) this.http.post(`/api/panel-test/${encodeURIComponent(this.testing)}/stop`, {}).subscribe();
        this.testing = want;
        this.progress.set({});
        if (want) {
          this.http.post(`/api/panel-test/${encodeURIComponent(want)}/start`, {}).subscribe({
            error: () => this.toast.show('Could not start the panel test', { kind: 'error' }),
          });
        }
      });
    });
    // Tick off knob/slider ends from the live values.
    effect(() => {
      // Read every signal up front: a short-circuit here would leave the effect tracking nothing.
      const open = this.open();
      const serial = this.serial();
      const devices = this.state.devices();
      const s = open && serial && this.testing === serial ? serial : null;
      const values = s ? devices[s]?.analogValues ?? [] : [];
      untracked(() => {
        if (!s) return;
        const next = { ...this.progress() };
        values.forEach((v, i) => {
          if (v == null) return;
          const cur = next[i] ?? { min: false, max: false, press: false };
          if (v <= END_MARGIN && !cur.min) next[i] = { ...cur, min: true };
          else if (v >= 255 - END_MARGIN && !cur.max) next[i] = { ...cur, max: true };
        });
        this.progress.set(next);
      });
    });
    // And presses.
    effect(() => {
      const press = this.state.lastButton();
      untracked(() => {
        if (!press || !press.pressed || press.serial !== this.testing) return;
        const cur = this.progress()[press.button] ?? { min: false, max: false, press: false };
        this.progress.set({ ...this.progress(), [press.button]: { ...cur, press: true } });
      });
    });
  }

  select(serial: string): void { this.chosen.set(serial); }

  name(serial: string): string { return this.state.devices()[serial]?.displayName || serial; }

  replayLights(): void {
    const s = this.serial();
    if (s) this.http.post(`/api/panel-test/${encodeURIComponent(s)}/lights`, {}).subscribe();
  }

  close(): void { this.open.set(false); }
}
