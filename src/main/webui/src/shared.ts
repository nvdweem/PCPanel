import { Commands } from './app/models/generated/backend.types';
import { computed, Signal } from '@angular/core';

export function mapRange(v: number, inMin: number, inMax: number, outMin: number, outMax: number): number {
  return ((v - inMin) / (inMax - inMin)) * (outMax - outMin) + outMin;
}

export function ensureCommands(sure: boolean, cmds?: Commands): Commands | undefined {
  if (sure) {
    return cmds ?? {commands: [], type: 'allAtOnce'};
  }
  return undefined;
}

export function titleFiltered(keyword: Signal<string>, list: Signal<string[] | undefined>) {
  return filtered(keyword, list, word => {
    const lcWord = word?.toLowerCase() ?? '';
    return val => val?.includes(lcWord) ?? false;
  })
}

export function filtered<K, L>(keyword: Signal<K>, list: Signal<L[] | undefined>, filter: (key: K) => (val: L) => boolean): Signal<L[]> {
  return computed(() => {
    const key = keyword();
    const result = list() ?? [];
    if (!key) {
      return result;
    }
    return result.filter(filter(key));
  });
}

export function uniqueSorted<T>(inp: Signal<T[] | undefined>, keyExtractor = (x: T): unknown => x): Signal<T[]> {
  return computed(() => {
    const map = new Map((inp() ?? [])?.map(val => [keyExtractor(val), val]));
    return [...map.values()];
  });
}
