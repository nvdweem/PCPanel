import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { DeviceService } from '../../services/device.service';
import { DeviceStateService } from '../../services/device-state.service';
import { ConnectionStatusComponent } from '../../components/connection-status/connection-status.component';
import { Command, Commands } from '../../models/generated/backend.types';

@Component({
  selector: 'app-device',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterModule, FormsModule,
    MatToolbarModule, MatButtonModule, MatButtonToggleModule,
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatIconModule, MatProgressSpinnerModule,
    ConnectionStatusComponent,
  ],
  templateUrl: './device.component.html',
  styleUrl: './device.component.scss'
})
export class DeviceComponent {
  private readonly deviceState = inject(DeviceStateService);
  private readonly deviceService = inject(DeviceService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  readonly serial = input.required<string>();

  // Derived from DeviceStateService — auto-updates on any WS patch
  protected readonly device = computed(() => this.deviceState.snapshotFor(this.serial)());

  protected readonly dialCommands = computed<Record<number, Commands>>(() =>
    (this.device()?.currentProfileSnapshot?.dialData ?? {}) as Record<number, Commands>
  );

  protected readonly buttonCommands = computed<Record<number, Commands>>(() =>
    (this.device()?.currentProfileSnapshot?.buttonData ?? {}) as Record<number, Commands>
  );

  protected readonly dialLabels = computed<Record<number, string>>(() => {
    const labels: Record<number, string> = {};
    Object.entries(this.dialCommands()).forEach(([k, v]) => {
      labels[Number(k)] = this.formatCommands(v);
    });
    return labels;
  });

  protected readonly buttonLabels = computed<Record<number, string>>(() => {
    const labels: Record<number, string> = {};
    Object.entries(this.buttonCommands()).forEach(([k, v]) => {
      labels[Number(k)] = this.formatCommands(v);
    });
    return labels;
  });

  protected readonly editingName = signal(false);
  protected readonly newName = signal('');

  saveName(): void {
    const trimmedName = this.newName().trim();
    const dev = this.device();
    if (!dev || !trimmedName) return;
    // HTTP mutation — backend emits device_renamed which updates DeviceStateService
    this.deviceService.renameDevice(dev.serial, trimmedName)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.editingName.set(false));
  }

  startEditName(): void {
    const dev = this.device();
    if (!dev) return;
    this.newName.set(dev.displayName);
    this.editingName.set(true);
  }

  cancelEditName(): void {
    this.editingName.set(false);
  }

  switchProfile(name: string): void {
    const dev = this.device();
    if (!dev) return;
    // HTTP mutation — backend emits profile_switched which updates DeviceStateService
    this.deviceService.switchProfile(dev.serial, name)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe();
  }

  addProfile(): void {
    const dev = this.device();
    const name = prompt('Profile name:');
    if (!name || !dev) return;
    this.deviceService.createProfile(dev.serial, name)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(p => this.switchProfile(p.name));
  }

  editDial(index: number): void {
    this.openDialog('dial', index, this.dialCommands()[index] ?? null);
  }

  editButton(index: number): void {
    this.openDialog('button', index, this.buttonCommands()[index] ?? null);
  }

  private openDialog(kind: 'dial' | 'button', index: number, currentCommands: Commands | null): void {
    console.log('TODO!');
    // const dev = this.device();
    // const data: CommandDialogData = {kind, index, currentCommands, profiles: dev?.profiles ?? []};
    // const ref = this.dialog.open(CommandConfigComponent, {data, width: '560px', panelClass: 'command-dialog-panel'});
    // ref.afterClosed()
    //   .pipe(takeUntilDestroyed(this.destroyRef))
    //   .subscribe((result: Commands | null | undefined) => {
    //     const currentDevice = this.device();
    //     if (!result || !currentDevice?.currentProfile) return;
    //     const {serial, currentProfile} = currentDevice;
    //     // HTTP mutation — backend emits assignment_changed which updates DeviceStateService
    //     if (kind === 'dial') {
    //       this.deviceService.setDialCommands(serial, currentProfile, index, result)
    //         .pipe(takeUntilDestroyed(this.destroyRef))
    //         .subscribe();
    //     } else {
    //       this.deviceService.setButtonCommands(serial, currentProfile, index, result)
    //         .pipe(takeUntilDestroyed(this.destroyRef))
    //         .subscribe();
    //     }
    //   });
  }

  getDialLabel(i: number): string {
    return this.dialLabels()[i] ?? '— unassigned —';
  }

  getButtonLabel(i: number): string {
    return this.buttonLabels()[i] ?? '— unassigned —';
  }

  range(n: number): number[] {
    return Array.from({length: n}, (_, i) => i);
  }

  friendlyType(type: string): string {
    switch (type) {
      case 'PCPANEL_RGB':
        return 'PCPanel RGB';
      case 'PCPANEL_MINI':
        return 'PCPanel Mini';
      case 'PCPANEL_PRO':
        return 'PCPanel Pro';
      default:
        return type;
    }
  }

  private formatCommands(cmds: Commands | null | undefined): string {
    if (!cmds?.commands?.length) return '— unassigned —';
    return cmds.commands.map((c: Command) => (c._type ?? '').split('.').pop() ?? 'Command').join(', ');
  }
}
