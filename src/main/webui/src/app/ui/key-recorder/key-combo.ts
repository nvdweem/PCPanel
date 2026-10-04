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

/** A ready-made combo the keystroke field offers, for keys and actions a keyboard can't record. */
export interface KeySuggestion {
  group: string;
  label: string;
  value: string;
}

/**
 * Combos offered next to the recorder. `lock` locks the PC and the `scroll_*` keys turn the mouse wheel;
 * both are executed by `KeystrokeTokens` on the backend. `KeystrokeSuggestionsParityTest` fails the build
 * if one names a key the backend does not resolve.
 */
export const KEY_SUGGESTIONS: KeySuggestion[] = [
  { group: 'System', label: 'Show desktop', value: 'Win+D' },
  { group: 'System', label: 'Minimise all windows', value: 'Win+M' },
  { group: 'System', label: 'Task view', value: 'Win+Tab' },
  { group: 'System', label: 'Switch window', value: 'Alt+Tab' },
  { group: 'System', label: 'Close window', value: 'Alt+F4' },
  { group: 'System', label: 'File Explorer', value: 'Win+E' },
  { group: 'System', label: 'Settings', value: 'Win+I' },
  { group: 'System', label: 'Run', value: 'Win+R' },
  { group: 'System', label: 'Task Manager', value: 'Ctrl+Shift+Escape' },
  { group: 'System', label: 'Clipboard history', value: 'Win+V' },
  { group: 'System', label: 'Snip', value: 'Shift+Win+S' },
  { group: 'System', label: 'Previous virtual desktop', value: 'Ctrl+Win+ArrowLeft' },
  { group: 'System', label: 'Next virtual desktop', value: 'Ctrl+Win+ArrowRight' },
  { group: 'System', label: 'Lock PC', value: 'lock' },
  { group: 'Editing', label: 'Copy', value: 'Ctrl+C' },
  { group: 'Editing', label: 'Cut', value: 'Ctrl+X' },
  { group: 'Editing', label: 'Paste', value: 'Ctrl+V' },
  { group: 'Editing', label: 'Undo', value: 'Ctrl+Z' },
  { group: 'Editing', label: 'Redo', value: 'Ctrl+Y' },
  { group: 'Editing', label: 'Select all', value: 'Ctrl+A' },
  { group: 'Editing', label: 'Save', value: 'Ctrl+S' },
  { group: 'Editing', label: 'Find', value: 'Ctrl+F' },
  { group: 'Browser', label: 'New tab', value: 'Ctrl+T' },
  { group: 'Browser', label: 'Close tab', value: 'Ctrl+W' },
  { group: 'Browser', label: 'Reopen closed tab', value: 'Ctrl+Shift+T' },
  { group: 'Browser', label: 'Next tab', value: 'Ctrl+Tab' },
  { group: 'Browser', label: 'Previous tab', value: 'Ctrl+Shift+Tab' },
  { group: 'Browser', label: 'Back', value: 'Alt+ArrowLeft' },
  { group: 'Browser', label: 'Forward', value: 'Alt+ArrowRight' },
  { group: 'Navigation', label: 'Page up', value: 'PageUp' },
  { group: 'Navigation', label: 'Page down', value: 'PageDown' },
  { group: 'Navigation', label: 'Home', value: 'Home' },
  { group: 'Navigation', label: 'End', value: 'End' },
  { group: 'Mouse wheel', label: 'Scroll up', value: 'scroll_up' },
  { group: 'Mouse wheel', label: 'Scroll down', value: 'scroll_down' },
  { group: 'Mouse wheel', label: 'Scroll left', value: 'scroll_left' },
  { group: 'Mouse wheel', label: 'Scroll right', value: 'scroll_right' },
  { group: 'Mouse wheel', label: 'Zoom in', value: 'Ctrl+scroll_up' },
  { group: 'Mouse wheel', label: 'Zoom out', value: 'Ctrl+scroll_down' },
  { group: 'Function keys', label: 'F1', value: 'F1' },
  { group: 'Function keys', label: 'F2', value: 'F2' },
  { group: 'Function keys', label: 'F3', value: 'F3' },
  { group: 'Function keys', label: 'F4', value: 'F4' },
  { group: 'Function keys', label: 'F5', value: 'F5' },
  { group: 'Function keys', label: 'F6', value: 'F6' },
  { group: 'Function keys', label: 'F7', value: 'F7' },
  { group: 'Function keys', label: 'F8', value: 'F8' },
  { group: 'Function keys', label: 'F9', value: 'F9' },
  { group: 'Function keys', label: 'F10', value: 'F10' },
  { group: 'Function keys', label: 'F11', value: 'F11' },
  { group: 'Function keys', label: 'F12', value: 'F12' },
  { group: 'Function keys', label: 'F13', value: 'F13' },
  { group: 'Function keys', label: 'F14', value: 'F14' },
  { group: 'Function keys', label: 'F15', value: 'F15' },
  { group: 'Function keys', label: 'F16', value: 'F16' },
  { group: 'Function keys', label: 'F17', value: 'F17' },
  { group: 'Function keys', label: 'F18', value: 'F18' },
  { group: 'Function keys', label: 'F19', value: 'F19' },
  { group: 'Function keys', label: 'F20', value: 'F20' },
  { group: 'Function keys', label: 'F21', value: 'F21' },
  { group: 'Function keys', label: 'F22', value: 'F22' },
  { group: 'Function keys', label: 'F23', value: 'F23' },
  { group: 'Function keys', label: 'F24', value: 'F24' },
];

/** The suggestion a combo is, however its modifiers are spelled or ordered; undefined when it is none. */
export function suggestionFor(value: string): KeySuggestion | undefined {
  const combo = parseCombo(value);
  return KEY_SUGGESTIONS.find(s => {
    const candidate = parseCombo(s.value);
    return candidate.key.toLowerCase() === combo.key.toLowerCase()
      && candidate.mods.join('+') === combo.mods.join('+');
  });
}
