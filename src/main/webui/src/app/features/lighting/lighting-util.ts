import { LightingConfig } from '../../models/generated/backend.types';

/**
 * Animated logo modes (RAINBOW/BREATH) need a non-zero brightness/speed to light
 * up on the device — these are signed bytes where -1 = full. Older/blank configs
 * may have them at 0; normalize on save so an animated logo is never dark.
 */
export function normalizeLogo(cfg: LightingConfig): LightingConfig {
  const l = cfg.logoConfig;
  if (!l || (l.mode !== 'RAINBOW' && l.mode !== 'BREATH')) return cfg;
  if (l.brightness && l.speed) return cfg;
  return { ...cfg, logoConfig: { ...l, brightness: l.brightness || -1, speed: l.speed || 32 } };
}
