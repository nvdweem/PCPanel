import { CurveDefinition, CurvePoint } from '../../models/generated/backend.types';

/** Mirrors com.getpcpanel.commands.curve.AmountCurve. */
const LOG_BASE = 1.04723275;
export const LOG_AMOUNT = 50;
const BASE_PER_AMOUNT = (LOG_BASE - 1) / LOG_AMOUNT;

export const LINEAR_ID = 'linear';
export const LOGARITHMIC_ID = 'logarithmic';

/** A curve maps a dial position (0..1) to an output (0..1). */
export type CurveFn = (x: number) => number;

const clamp01 = (x: number) => Math.min(1, Math.max(0, x));

function exponential(x: number, magnitude: number): number {
  const base = 1 + magnitude * BASE_PER_AMOUNT;
  return (Math.pow(base, 100 * x) - 1) / (Math.pow(base, 100) - 1);
}

export function amountCurve(amount: number): CurveFn {
  const magnitude = Math.abs(amount);
  if (magnitude === 0) return clamp01;
  return x => {
    const position = clamp01(x);
    return amount > 0 ? exponential(position, magnitude) : 1 - exponential(1 - position, magnitude);
  };
}

/**
 * Monotone cubic (Fritsch-Carlson) through the control points — the same interpolation the backend uses,
 * so the drawn graph is what the hardware will do. Points are sorted and points sharing an x collapse.
 */
export function pointsCurve(points: CurvePoint[]): CurveFn {
  const sorted = [...points].sort((a, b) => a.x - b.x);
  const xs: number[] = [];
  const ys: number[] = [];
  for (const point of sorted) {
    if (xs.length > 0 && point.x - xs[xs.length - 1] < 1e-9) continue;
    xs.push(point.x);
    ys.push(point.y);
  }
  const n = xs.length;
  if (n < 2) return clamp01;

  const h: number[] = [];
  const delta: number[] = [];
  for (let i = 0; i < n - 1; i++) {
    h[i] = xs[i + 1] - xs[i];
    delta[i] = (ys[i + 1] - ys[i]) / h[i];
  }
  const m = new Array<number>(n).fill(0);
  m[0] = delta[0];
  m[n - 1] = delta[n - 2];
  for (let i = 1; i < n - 1; i++) {
    if (delta[i - 1] * delta[i] <= 0) continue;
    const w1 = 2 * h[i] + h[i - 1];
    const w2 = h[i] + 2 * h[i - 1];
    m[i] = (w1 + w2) / (w1 / delta[i - 1] + w2 / delta[i]);
  }

  return x => {
    const position = Math.min(xs[n - 1], Math.max(xs[0], x));
    let i = 0;
    while (i < n - 2 && position > xs[i + 1]) i++;
    const t = (position - xs[i]) / h[i];
    const t2 = t * t;
    const t3 = t2 * t;
    return (2 * t3 - 3 * t2 + 1) * ys[i]
      + (t3 - 2 * t2 + t) * h[i] * m[i]
      + (-2 * t3 + 3 * t2) * ys[i + 1]
      + (t3 - t2) * h[i] * m[i + 1];
  };
}

export function curveFn(definition: CurveDefinition | undefined): CurveFn {
  if (!definition) return clamp01;
  return definition.mode === 'points' ? pointsCurve(definition.points ?? []) : amountCurve(definition.amount);
}

/** The curve a control's stored id names, straight through when it names nothing or something gone. */
export function resolveCurve(id: string | undefined, library: CurveDefinition[] | undefined): CurveFn {
  if (!id) return clamp01;
  return curveFn((library ?? []).find(curve => curve.id === id));
}

export const isBuiltIn = (id: string | undefined): boolean => id === LINEAR_ID || id === LOGARITHMIC_ID;

/**
 * The shapes the built-ins ship with, mirroring com.getpcpanel.commands.curve.Curves#BUILT_INS. The
 * backend sends them in the library too; these are what "reset to default" restores without waiting for
 * a round trip, and what decides whether a built-in is still untouched.
 */
export const BUILT_IN_DEFAULTS: CurveDefinition[] = [
  { id: LINEAR_ID, name: 'Linear', mode: 'amount', amount: 0, points: [] },
  { id: LOGARITHMIC_ID, name: 'Logarithmic', mode: 'amount', amount: LOG_AMOUNT, points: [] },
];

/** Where a freshly switched-to Custom curve starts: the shape the user is already looking at. */
export function seedPoints(definition: CurveDefinition): CurvePoint[] {
  const fn = amountCurve(definition.amount);
  return [0, 0.25, 0.5, 0.75, 1].map(x => ({ x, y: fn(x) }));
}

/** An SVG path for a curve, drawn into a `size`-square box with `pad` around the plot area. */
export function curvePath(fn: CurveFn, size: number, pad: number, steps = 120): string {
  const px = (x: number) => pad + x * (size - 2 * pad);
  const py = (y: number) => size - pad - clamp01(y) * (size - 2 * pad);
  let d = '';
  for (let i = 0; i <= steps; i++) {
    const x = i / steps;
    d += `${i ? 'L' : 'M'}${px(x).toFixed(2)} ${py(fn(x)).toFixed(2)}`;
  }
  return d;
}
