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

/** The control's output range, as percentages — everything the curve produces is squeezed into it. */
export interface TrimRange {
  minTrim?: number;
  maxTrim?: number;
}

/** What the graph draws: the live mapping, the same curve without the window, and the dead throw. */
export interface MappingShape {
  /** The transfer curve actually applied. */
  path: string;
  /** The same curve across the whole throw — what Start/End is cutting down; empty when they cut nothing. */
  ghost: string;
  /** x spans of the throw the action ignores, for shading. */
  dead: { x: number; width: number }[];
  /** y of the path at each end, for the dot markers. */
  y0: number;
  y1: number;
}

const clamp = (v: number): number => Math.max(0, Math.min(100, v ?? 0));
/** Enough segments that the shape reads as itself even in the 46px header preview. */
const STEPS = 32;

/**
 * Transfer curve on an input(x) -> output(y) graph: the extreme below Start and above End, with the
 * control's whole response curve travelled in between. That is the mapping the dial engine applies — it
 * rescales the live position onto the full range before the curve sees it, so Start 25 / End 75 fits the
 * entire curve into that half of the throw. `ghost` is the same curve without that squeeze, so the two
 * together show what the window costs.
 *
 * Trim bounds what the curve produces, but only inside the window: a position outside it is answered with
 * the raw extreme, untrimmed. So trim and a window step against each other at the edges, which the path
 * draws as the verticals they are — as it does for a curve that stops short of 0 or 1.
 */
export function mappingCurve(
  p: DialParams | null | undefined, g: MapGeom, curve?: CurveDefinition | null, trim?: TrimRange | null,
): MappingShape {
  const start = clamp(p?.moveStart ?? 0);
  const end = Math.max(start + 0.01, clamp(100 - (p?.moveEnd ?? 0)));
  const invert = !!p?.invert;
  const minTrim = clamp(trim?.minTrim ?? 0);
  const maxTrim = clamp(trim?.maxTrim ?? 100);

  const xFor = (pct: number): number => g.x0 + (clamp(pct) / 100) * (g.x1 - g.x0);
  const yFor = (pct: number): number => g.yBottom - (clamp(pct) / 100) * (g.yBottom - g.yTop);
  /** Where an output percentage lands, with invert flipping the whole response as the engine does last. */
  const out = (pct: number): number => yFor(invert ? 100 - pct : pct);
  const shape = curveFn(curve ?? undefined);
  /** The curve at travel `t`, squeezed into the trim range the way the engine squeezes it. */
  const trimmed = (t: number): number => minTrim + shape(t) * (maxTrim - minTrim);

  const at = (x: number, y: number): string => `${x.toFixed(2)} ${y.toFixed(2)}`;
  /** The curve travelled across a span of the throw, as path points. */
  const travel = (from: number, to: number): string[] => {
    const points: string[] = [];
    for (let i = 0; i <= STEPS; i++) {
      const t = i / STEPS;
      points.push(at(xFor(from + t * (to - from)), out(trimmed(t))));
    }
    return points;
  };

  // A flat only exists where the window actually cuts: with Start 0 there is no position below it to
  // answer with the extreme, so the curve's own end is where the line begins.
  const lo = out(0);
  const hi = out(100);
  const inside = travel(start, end);
  const head = start > 0 ? [at(g.x0, lo), at(xFor(start), lo)] : [];
  const tail = end < 100 ? [at(xFor(end), hi), at(g.x1, hi)] : [];
  const dead = [
    { x: g.x0, width: xFor(start) - g.x0 },
    { x: xFor(end), width: g.x1 - xFor(end) },
  ].filter(band => band.width > 0.5);

  return {
    path: line([...head, ...inside, ...tail]),
    // Without a window the two coincide, and drawing the ghost would only thicken the line.
    ghost: dead.length ? line(travel(0, 100)) : '',
    dead,
    y0: start > 0 ? lo : out(trimmed(0)),
    y1: end < 100 ? hi : out(trimmed(1)),
  };
}

const line = (points: string[]): string => points.map((p, i) => `${i ? 'L' : 'M'} ${p}`).join(' ');
