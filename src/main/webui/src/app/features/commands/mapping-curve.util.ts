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

/**
 * Transfer curve on an input(x) -> output(y) graph, clamped flat outside Start/End.
 * Returns the SVG path and the y of each endpoint (for the dot markers).
 */
export function mappingCurve(p: DialParams | null | undefined, g: MapGeom): { path: string; y0: number; y1: number } {
  const start = clamp(p?.moveStart ?? 0);
  const end = clamp(100 - (p?.moveEnd ?? 0));
  const invert = !!p?.invert;
  const xFor = (pct: number): number => g.x0 + (clamp(pct) / 100) * (g.x1 - g.x0);
  const yFor = (pct: number): number => g.yBottom - (clamp(pct) / 100) * (g.yBottom - g.yTop);
  const lo = invert ? yFor(100) : yFor(0);
  const hi = invert ? yFor(0) : yFor(100);
  const e = Math.max(start + 0.01, end);
  return { path: `M ${g.x0} ${lo} L ${xFor(start)} ${lo} L ${xFor(e)} ${hi} L ${g.x1} ${hi}`, y0: lo, y1: hi };
}
