// ── Color utilities ───────────────────────────────────────────────────────────

import { LightingConfig } from '../../models/generated/backend.types';

function clamp01(v: number): number {
  return Math.max(0, Math.min(1, v));
}

function normalizeHue(h: number): number {
  const x = h % 1;
  return x < 0 ? x + 1 : x;
}

function unitByte(v: number | null | undefined): number {
  return clamp01((v ?? 0) / 255);
}

function toHex(n: number): string {
  return Math.max(0, Math.min(255, Math.round(n))).toString(16).padStart(2, '0');
}

export function hsbToHex(h: number, s: number, b: number): string {
  const hue = normalizeHue(h);
  const sat = clamp01(s);
  const val = clamp01(b);
  const i = Math.floor(hue * 6);
  const f = hue * 6 - i;
  const p = val * (1 - sat);
  const q = val * (1 - f * sat);
  const t = val * (1 - (1 - f) * sat);
  const cases = [[val, t, p], [q, val, p], [p, val, t], [p, q, val], [t, p, val], [val, p, q]];
  const [r, g, bl] = cases[i % 6];
  return `#${toHex(r * 255)}${toHex(g * 255)}${toHex(bl * 255)}`;
}

// ── Static base-color helpers (t=0 fill; CSS animates from here) ─────────────
// These give each LED its phase-offset starting hue so CSS hue-rotate produces
// a spread across the device rather than all elements cycling in sync.

export function rainbowBaseColor(config: LightingConfig, index: number, total: number): string {
  const base = unitByte(config.rainbowPhaseShift);
  const brightness = unitByte(config.rainbowBrightness);
  const reverse = config.rainbowReverse === 1;
  const span = 0.7;
  const shift = total <= 1 ? 0 : (span * index) / (total - 1);
  const hue = reverse ? base - shift : base + shift;
  return hsbToHex(hue, 1, brightness);
}

export function waveBaseColor(config: LightingConfig, index: number, total: number): string {
  const base = unitByte(config.waveHue);
  const brightness = unitByte(config.waveBrightness);
  const reverse = config.waveReverse === 1;
  const bounce = config.waveBounce === 1;
  let progress = total <= 1 ? 0 : index / (total - 1);
  if (reverse) progress = 1 - progress;
  const spread = 0.12;
  const offset = bounce ? Math.abs(progress - 0.5) * 2 * spread : (progress - 0.5) * 2 * spread;
  return hsbToHex(normalizeHue(base + offset), 1, brightness);
}

/** Base fill for breath: full-brightness hue so CSS brightness() can pulse it down. */
export function breathBaseColor(config: LightingConfig): string {
  return hsbToHex(unitByte(config.breathHue), 1, unitByte(config.breathBrightness));
}

/**
 * Resolve the visual color of a single knob LED ring (index of `total`) for a
 * non-Pro device (Mini/RGB). Animated modes return their phase-shifted base
 * color (CSS animation classes handle the motion); static modes return the
 * configured color. Handles every {@link LightingConfig} mode so the same
 * resolver works for Mini (CUSTOM/knobConfigs) and RGB (SINGLE_COLOR/individualColors).
 */
export function knobRingColor(config: LightingConfig | null | undefined, index: number, total: number, fallback: string): string {
  if (!config) return fallback;
  switch (config.lightingMode) {
    case 'ALL_RAINBOW':
      return rainbowBaseColor(config, index, total);
    case 'ALL_WAVE':
      return waveBaseColor(config, index, total);
    case 'ALL_BREATH':
      return breathBaseColor(config);
    case 'ALL_COLOR':
      return config.allColor || fallback;
    case 'SINGLE_COLOR':
      return config.individualColors?.[index] || fallback;
    case 'CUSTOM': {
      const knob = config.knobConfigs?.[index];
      if (!knob || knob.mode === 'NONE') return '#000000';
      // A volume-gradient knob's colour depends on the live value; emit a token the caller resolves
      // per value via resolveVolGrad (Mini/RGB have no backend-precomputed dialColors).
      if (knob.mode === 'VOLUME_GRADIENT') return volGradToken(knob.color1, knob.color2);
      return knob.color1 || fallback;
    }
    default:
      return fallback;
  }
}

// ── Volume-gradient knob colour (interpolated per live value) ─────────────────
// A volume-gradient knob shows one LED colour: color1 at value 0, color2 at value 100, mixed between.
// The backend (Pro) and knobRingColor (Mini/RGB) emit a `$VOLGRAD!<c1>!<c2>` token because the live
// value isn't known where colours are resolved; the device view interpolates it against the knob value.

export const TOKEN_VOLGRAD = '$VOLGRAD!';

function parseHex(c: string): [number, number, number] | null {
  const m = /^#?([0-9a-f]{6})$/i.exec((c || '').trim());
  if (!m) return null;
  const n = parseInt(m[1], 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

/** Linear RGB interpolation between two hex colours; t is clamped to 0–1. */
export function lerpHex(a: string, b: string, t: number): string {
  const ca = parseHex(a);
  const cb = parseHex(b);
  if (!ca || !cb) return ca ? a : (cb ? b : '#000000');
  const k = clamp01(t);
  return `#${toHex(ca[0] + (cb[0] - ca[0]) * k)}${toHex(ca[1] + (cb[1] - ca[1]) * k)}${toHex(ca[2] + (cb[2] - ca[2]) * k)}`;
}

/** Build a volume-gradient token (Mini/RGB path, where colours aren't precomputed by the backend). */
export function volGradToken(c1: string | null | undefined, c2: string | null | undefined): string {
  return `${TOKEN_VOLGRAD}${c1 || '#000000'}!${c2 || '#000000'}`;
}

/** Resolve a `$VOLGRAD!<c1>!<c2>` token to its interpolated colour at value `pct` (0–100), or null
 *  when `color` is a plain colour (pass-through). */
export function resolveVolGrad(color: string | null | undefined, pct: number): string | null {
  if (!color || !color.startsWith(TOKEN_VOLGRAD)) return null;
  const parts = color.slice(TOKEN_VOLGRAD.length).split('!');
  return lerpHex(parts[0] || '#000000', parts[1] || '#000000', pct / 100);
}

// ── CSS animation helpers (drives class + custom-property bindings) ───────────

function speedFactor(speed: number | null | undefined): number {
  return 0.08 + unitByte(speed) * 0.92;
}

/** CSS class to apply for global ALL_* animation modes. */
export function lightingAnimClass(config: LightingConfig): string {
  switch (config.lightingMode) {
    case 'ALL_RAINBOW':
      return config.rainbowReverse === 1 ? 'anim-rainbow-reverse' : 'anim-rainbow';
    case 'ALL_WAVE':
      return config.waveBounce === 1 ? 'anim-wave-bounce'
        : config.waveReverse === 1 ? 'anim-wave-reverse' : 'anim-wave';
    case 'ALL_BREATH':
      return 'anim-breath';
    default:
      return '';
  }
}

/** Value for the --anim-duration CSS custom property. */
export function lightingAnimDuration(config: LightingConfig): string {
  const speed =
    config.lightingMode === 'ALL_RAINBOW' ? config.rainbowSpeed :
      config.lightingMode === 'ALL_WAVE' ? config.waveSpeed :
        config.lightingMode === 'ALL_BREATH' ? config.breathSpeed : null;
  return speed != null ? `${(1 / speedFactor(speed)).toFixed(2)}s` : '0s';
}

/** Value for the --breath-min CSS custom property (min brightness relative to max). */
export function lightingBreathMin(config: LightingConfig): number {
  const maxB = Math.max(0.001, unitByte(config.breathBrightness));
  return +(0.18 / maxB).toFixed(4);
}

// ── Backend animation tokens ──────────────────────────────────────────────────
// The backend returns these marker strings when a per-element custom mode needs
// a CSS animation rather than a static color.

export const TOKEN_RAINBOW = '$RAINBOW!';
export const TOKEN_BREATH = '$BREATH';

export interface ColorVisual {
  fill: string;
  animClass: string;
  animDuration: string;
  breathMin: number;
}

/**
 * Resolve a backend color value to SVG fill + CSS animation properties.
 * A plain hex color passes through unchanged; tokens map to the right animation.
 */
export function resolveColorVisual(
  color: string,
  fallbackAnimClass: string,
  fallbackAnimDuration: string,
  fallbackBreathMin: number,
): ColorVisual {
  if (color === TOKEN_RAINBOW) {
    // Saturated red (#ff0000) as base so CSS hue-rotate cycles through all hues.
    return {fill: '#ff0000', animClass: 'anim-rainbow', animDuration: '8s', breathMin: 0.18};
  }
  if (color === TOKEN_BREATH) {
    return {fill: '#ffffff', animClass: 'anim-breath', animDuration: '3s', breathMin: 0.18};
  }
  return {fill: color, animClass: fallbackAnimClass, animDuration: fallbackAnimDuration, breathMin: fallbackBreathMin};
}
