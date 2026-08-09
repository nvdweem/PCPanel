/**
 * Reading and writing the "modifier+modifier+key" combo string the keystroke backend executes.
 *
 * Kept free of Angular so it is plain, testable logic. The modifier vocabulary mirrors
 * `KeystrokeTokens` on the backend; `KeyComboVocabularyParityTest` fails the build if the two drift.
 */

/** Modifier labels in the order a combo spells them. */
export const MOD_ORDER = ['Ctrl', 'Shift', 'Alt', 'Win'];

/** Every spelling of a modifier the backend accepts, mapped to the label shown here. */
export const MOD_ALIASES: Record<string, string> = {
  ctrl: 'Ctrl', control: 'Ctrl', ctl: 'Ctrl',
  shift: 'Shift',
  alt: 'Alt', option: 'Alt', opt: 'Alt',
  cmd: 'Win', command: 'Win', windows: 'Win', win: 'Win', meta: 'Win', super: 'Win', os: 'Win',
};

export interface Combo {
  /** The modifiers the combo carries, in MOD_ORDER. */
  mods: string[];
  /** The key it ends on, empty when only modifiers are set so far. */
  key: string;
}

/** The modifier a token names, or undefined when it names none. */
export function modOf(token: string): string | undefined {
  return MOD_ALIASES[token.trim().toLowerCase()];
}

/**
 * Splits a combo into its modifiers and its key. Accepts any spelling the backend does, so a combo
 * saved as `ctrl+A` reads back with Ctrl set rather than looking like an unmodified key.
 */
export function parseCombo(value: string): Combo {
  const tokens = value ? value.split('+').map(s => s.trim()).filter(Boolean) : [];
  const found = new Set(tokens.map(modOf).filter((m): m is string => !!m));
  return {
    mods: MOD_ORDER.filter(m => found.has(m)),
    key: tokens.filter(t => !modOf(t)).pop() ?? '',
  };
}

/** Writes a combo back out, modifiers first and in a stable order. */
export function formatCombo(mods: string[], key: string): string {
  return [...MOD_ORDER.filter(m => mods.includes(m)), ...(key ? [key] : [])].join('+');
}
