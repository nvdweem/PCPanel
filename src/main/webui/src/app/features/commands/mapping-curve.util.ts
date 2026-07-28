import { CurveDefinition } from '../../models/generated/backend.types';
import { curveFn } from '../curves/curve.util';

/** Shared input-mapping transfer-curve math, used by both the full editor graph
 *  (command-fields) and the compact header preview (mapping-preview). */
export interface MapGeom {
  /** x at input 0% and 100% */
  x0: number;
  x1: number;
  /** y at output 0% (bottom) and 100% (top) */
  yBottom: number;
  yTop: number;
}

export interface DialParams {
  invert?: boolean;
  /** output position (0..100) the control starts moving from */
  moveStart?: number;
  /** amount (0..100) trimmed off the top; end position = 100 - moveEnd */
  moveEnd?: number;
}

const clamp = (v: number): number => Math.max(0, Math.min(100, v ?? 0));
/** Enough segments that the shape reads as itself even in the 46px header preview. */
const STEPS = 32;

/**
 * Transfer curve on an input(x) -> output(y) graph: flat below Start and above End, with the control's
 * whole response curve travelled in between. That is the mapping the dial engine applies — it rescales
 * the live position onto the full range before the curve sees it, so Start 25 / End 75 fits the entire
 * curve into that half of the throw.
 *
 * The flats sit at the extremes rather than at the curve's own ends, because a position outside the
 * window is answered with the extreme directly. A curve that does not reach 0 or 1 therefore steps at the
 * window edge, and the path draws that step as the vertical it is.
 *
 * Returns the SVG path and the y of each endpoint (for the dot markers).
 */
export function mappingCurve(
  p: DialParams | null | undefined, g: MapGeom, curve?: CurveDefinition | null,
): { path: string; y0: number; y1: number } {
  const start = clamp(p?.moveStart ?? 0);
  const end = Math.max(start + 0.01, clamp(100 - (p?.moveEnd ?? 0)));
  const invert = !!p?.invert;
  const xFor = (pct: number): number => g.x0 + (clamp(pct) / 100) * (g.x1 - g.x0);
  const yFor = (pct: number): number => g.yBottom - (clamp(pct) / 100) * (g.yBottom - g.yTop);
  /** Where an output percentage lands, with invert flipping the whole response as the engine does last. */
  const out = (pct: number): number => yFor(invert ? 100 - pct : pct);

  const shape = curveFn(curve ?? undefined);
  const lo = out(0);
  const hi = out(100);
  const at = (x: number, y: number): string => `L ${x.toFixed(2)} ${y.toFixed(2)}`;

  let path = `M ${g.x0.toFixed(2)} ${lo.toFixed(2)} ${at(xFor(start), lo)}`;
  for (let i = 0; i <= STEPS; i++) {
    const t = i / STEPS;
    path += ` ${at(xFor(start + t * (end - start)), out(shape(t) * 100))}`;
  }
  return { path: `${path} ${at(xFor(end), hi)} ${at(g.x1, hi)}`, y0: lo, y1: hi };
}
