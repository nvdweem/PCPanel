import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { DeviceStateService } from '../../services/device-state.service';
import { breathBaseColor, lightingAnimClass, lightingAnimDuration, lightingBreathMin, rainbowBaseColor, resolveColorVisual, waveBaseColor, } from './lighting-animation';
import { ensureCommands, mapRange } from '../../../shared';
import { LightingConfig } from '../../models/generated/backend.types';
import { ControlType } from './events';
import { CommandDialogData } from '../command-config/command-config.component';

interface SliderPosition {
  left: number;
  mirrored: boolean;
}

const PRO_EMPTY_ANALOG = [0, 0, 0, 0, 0, 0, 0, 0, 0];
const BLACK = 'black';
const PRO_EMPTY_DIAL_COLORS = [BLACK, BLACK, BLACK, BLACK, BLACK];
const PRO_EMPTY_SLIDER_LABEL_COLORS = [BLACK, BLACK, BLACK, BLACK];
const PRO_EMPTY_SLIDER_COLORS = [[BLACK, BLACK, BLACK, BLACK, BLACK], [BLACK, BLACK, BLACK, BLACK, BLACK], [BLACK, BLACK, BLACK, BLACK, BLACK], [BLACK, BLACK, BLACK, BLACK, BLACK]];
const PRO_EMPTY_LOGO_COLOR = BLACK;

@Component({
  selector: 'app-pcpanel-pro',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './device-visual.scss',
  templateUrl: './pcpanel-pro.component.html',
  imports: [
    NgTemplateOutlet,
  ]
})
export class PcpanelProComponent {
  protected deviceService = inject(DeviceStateService);
  serial = input.required<string>();
  onedit = output<CommandDialogData>();

  protected device = this.deviceService.snapshotFor(this.serial);
  protected sliderPositions: SliderPosition[] = [
    {left: 15, mirrored: false},
    {left: 90, mirrored: false},
    {left: 265, mirrored: true},
    {left: 340, mirrored: true},
  ];

  protected analogValues = computed(() => {
    return this.device()?.analogValues ?? PRO_EMPTY_ANALOG;
  });

  protected dialColors = computed(() => {
    const dev = this.device();
    const cfg = dev?.lightingConfig;
    if (!cfg) return dev?.dialColors ?? PRO_EMPTY_DIAL_COLORS;
    return this.animatedDialColors(cfg, dev?.dialColors ?? PRO_EMPTY_DIAL_COLORS);
  });

  protected sliderLabelColors = computed(() => {
    const dev = this.device();
    const cfg = dev?.lightingConfig;
    if (!cfg) return dev?.sliderLabelColors ?? PRO_EMPTY_SLIDER_LABEL_COLORS;
    return this.animatedSliderLabelColors(cfg, dev?.sliderLabelColors ?? PRO_EMPTY_SLIDER_LABEL_COLORS);
  });

  protected sliderColors = computed(() => {
    const dev = this.device();
    const cfg = dev?.lightingConfig;
    if (!cfg) return dev?.sliderColors ?? PRO_EMPTY_SLIDER_COLORS;
    return this.animatedSliderColors(cfg, dev?.sliderColors ?? PRO_EMPTY_SLIDER_COLORS);
  });

  protected logoColor = computed(() => {
    const dev = this.device();
    const cfg = dev?.lightingConfig;
    if (!cfg) return dev?.logoColor ?? PRO_EMPTY_LOGO_COLOR;
    return this.animatedLogoColor(cfg, dev?.logoColor ?? PRO_EMPTY_LOGO_COLOR);
  });

  protected readonly logoVisual = computed(() => {
    return resolveColorVisual(
      this.logoColor(),
      this.ledAnimClass(),
      this.ledAnimDuration(),
      this.ledBreathMin(),
    );
  });

  protected readonly ledAnimClass = computed(() => {
    const cfg = this.device()?.lightingConfig;
    return cfg ? lightingAnimClass(cfg) : '';
  });

  protected readonly ledAnimDuration = computed(() => {
    const cfg = this.device()?.lightingConfig;
    return cfg ? lightingAnimDuration(cfg) : '0s';
  });

  protected readonly ledBreathMin = computed(() => {
    const cfg = this.device()?.lightingConfig;
    return cfg ? lightingBreathMin(cfg) : 0.18;
  });

  protected edit(event: MouseEvent, type: ControlType, idx: number, contextClicked: boolean) {
    if (contextClicked) {
      event?.preventDefault();
    }
    const device = this.deviceService.devices()[this.serial()];

    const title = `${type} ${(idx % 5) + 1}`;
    const analog = ensureCommands(true, device?.currentProfileSnapshot?.dialData[String(idx)]);
    const button = ensureCommands(idx < 5, device?.currentProfileSnapshot?.buttonData[String(idx)]);
    const dblButton = ensureCommands(idx < 5, device?.currentProfileSnapshot?.dblButtonData[String(idx)]);
    const knobSetting = device?.currentProfileSnapshot?.knobSettings[String(idx)];

    this.onedit.emit({
      title,
      analog,
      button,
      dblButton,
      knobSetting,
    });
  }

  protected dialRotation(number: number) {
    return mapRange(number, 0, 255, 30, 330);
  }

  protected knobTop(value: number) {
    return mapRange(value, 0, 255, 170, -22);
  }

  private animatedDialColors(cfg: LightingConfig, fallback: string[]): string[] {
    if (cfg.lightingMode === 'ALL_RAINBOW') {
      return Array.from({length: 5}, (_, i) => rainbowBaseColor(cfg, i, 5));
    }
    if (cfg.lightingMode === 'ALL_WAVE') {
      return Array.from({length: 5}, (_, i) => waveBaseColor(cfg, i, 5));
    }
    if (cfg.lightingMode === 'ALL_BREATH') {
      const c = breathBaseColor(cfg);
      return Array.from({length: 5}, () => c);
    }
    return fallback;
  }

  private animatedSliderLabelColors(cfg: LightingConfig, fallback: string[]): string[] {
    if (cfg.lightingMode === 'ALL_RAINBOW') {
      return Array.from({length: 4}, (_, i) => rainbowBaseColor(cfg, i + 5, 9));
    }
    if (cfg.lightingMode === 'ALL_WAVE') {
      return Array.from({length: 4}, (_, i) => waveBaseColor(cfg, i, 4));
    }
    if (cfg.lightingMode === 'ALL_BREATH') {
      const c = breathBaseColor(cfg);
      return Array.from({length: 4}, () => c);
    }
    return fallback;
  }

  private animatedSliderColors(cfg: LightingConfig, fallback: string[][]): string[][] {
    if (cfg.lightingMode === 'ALL_RAINBOW') {
      const vertical = cfg.rainbowVertical === 1;
      return Array.from({length: 4}, (_, sliderIdx) =>
        Array.from({length: 5}, (_, segIdx) => {
          const idx = vertical ? segIdx : sliderIdx * 5 + segIdx;
          const total = vertical ? 5 : 20;
          return rainbowBaseColor(cfg, idx, total);
        }),
      );
    }
    if (cfg.lightingMode === 'ALL_WAVE') {
      return Array.from({length: 4}, () =>
        Array.from({length: 5}, (_, segIdx) => waveBaseColor(cfg, segIdx, 5)),
      );
    }
    if (cfg.lightingMode === 'ALL_BREATH') {
      const c = breathBaseColor(cfg);
      return Array.from({length: 4}, () => Array.from({length: 5}, () => c));
    }
    return fallback;
  }

  private animatedLogoColor(cfg: LightingConfig, fallback: string): string {
    if (cfg.lightingMode === 'ALL_RAINBOW') {
      return rainbowBaseColor(cfg, 9, 10);
    }
    if (cfg.lightingMode === 'ALL_WAVE') {
      return waveBaseColor(cfg, 0, 1);
    }
    if (cfg.lightingMode === 'ALL_BREATH') {
      return breathBaseColor(cfg);
    }
    return fallback;
  }
}
