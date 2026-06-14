import { inject, Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { CommandConfigComponent, CommandDialogData } from './command-config/command-config.component';
import { filter, forkJoin } from 'rxjs';
import { DeviceService } from '../../services/device.service';
import { DeviceStateService } from '../../services/device-state.service';
import { LightingConfig, SingleKnobLightingConfig, SingleSliderLightingConfig } from '../../models/generated/backend.types';

interface OpenCommandDialogParams {
  serial: string;
  controlIdx: number;
  data: CommandDialogData;
}

@Injectable({
  providedIn: 'root',
})
export class PcpanelCommandService {
  private readonly dialog = inject(MatDialog);
  private readonly deviceService = inject(DeviceService);
  private readonly deviceState = inject(DeviceStateService);

  openCommandDialog({serial, controlIdx, data}: OpenCommandDialogParams) {
    const ref = this.dialog.open(CommandConfigComponent, {data, width: '560px'});
    ref.afterClosed().pipe(filter((x): x is CommandDialogData => !!x)).subscribe((result) => {
      const currentProfile = this.deviceState.devices()[serial]?.currentProfile;
      if (!currentProfile) {
        return;
      }

      const requests = [
        this.deviceService.setControlAssignments(serial, currentProfile, controlIdx, {
          analog: result.analog,
          button: result.button,
          dblButton: result.dblButton,
          knobSetting: result.knobSetting,
        })
      ];

      const lightingUpdate = this.buildLightingUpdate(serial, controlIdx, result);
      if (lightingUpdate) {
        requests.push(this.deviceService.setLighting(serial, lightingUpdate));
      }

      forkJoin(requests).subscribe({
        error: err => console.error('Failed to save command settings', err),
      });
    });
  }

  private buildLightingUpdate(serial: string, controlIdx: number, result: CommandDialogData): LightingConfig | null {
    const currentLighting = this.deviceState.devices()[serial]?.lightingConfig;
    if (!currentLighting || !result.lighting) {
      return null;
    }

    // PCPanel RGB stores per-knob colors as a single hex in the global
    // SINGLE_COLOR individualColors[] array (no CUSTOM/knobConfigs support).
    if (result.lightingVariant === 'rgb-single') {
      const color = (result.lighting as SingleKnobLightingConfig).color1 || '#000000';
      return {
        ...currentLighting,
        lightingMode: 'SINGLE_COLOR',
        individualColors: this.patchIndividualColor(currentLighting.individualColors, controlIdx, color),
      };
    }

    if (result.controlType === 'dial') {
      return {
        ...currentLighting,
        lightingMode: 'CUSTOM',
        knobConfigs: this.patchKnobConfig(currentLighting.knobConfigs, controlIdx, result.lighting as SingleKnobLightingConfig),
      };
    }

    if (result.controlType === 'slider') {
      const sliderIdx = controlIdx - 5;
      if (sliderIdx < 0) {
        return null;
      }
      return {
        ...currentLighting,
        lightingMode: 'CUSTOM',
        sliderConfigs: this.patchSliderConfig(currentLighting.sliderConfigs, sliderIdx, result.lighting as SingleSliderLightingConfig),
      };
    }

    return null;
  }

  private patchKnobConfig(source: SingleKnobLightingConfig[], idx: number, value: SingleKnobLightingConfig): SingleKnobLightingConfig[] {
    const next = [...(source ?? [])];
    next[idx] = {...value};
    return next;
  }

  private patchSliderConfig(source: SingleSliderLightingConfig[], idx: number, value: SingleSliderLightingConfig): SingleSliderLightingConfig[] {
    const next = [...(source ?? [])];
    next[idx] = {...value};
    return next;
  }

  private patchIndividualColor(source: string[] | undefined, idx: number, color: string): string[] {
    const length = Math.max(idx + 1, source?.length ?? 0);
    const next = Array.from({length}, (_, i) => source?.[i] || '#000000');
    next[idx] = color;
    return next;
  }
}
