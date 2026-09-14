/**
 * Caret analysis for the template editor: where the caret is inside a {{ … }} tag, which variable path it is
 * extending, and what text choosing a completion inserts. Pure so it can be tested without Angular.
 */

export interface CompletionItem {
  name: string;
  insert: string;
  kind: string;          // value | object | map | list | function
  label?: string;
  description?: string;
  value?: string;
}

export interface CompletionContext {
  /** Whether the caret sits inside an unclosed {{ … before it. */
  inTag: boolean;
  /** The path whose children are offered: '' for the roots, else e.g. "wl.channel". */
  path: string;
  /** What has been typed of the current segment, used to filter. */
  partial: string;
  /** Start offset of `partial` in the text; the chosen insert replaces text from here to the caret. */
  replaceFrom: number;
}

const SEGMENT = String.raw`(?:[A-Za-z_][A-Za-z0-9_]*|get\('(?:[^'\\]|\\.)*'\)|get\(\d+\))`;
const PATH_AT_END = new RegExp(String.raw`((?:${SEGMENT}\.)*)([A-Za-z0-9_:]*)$`);

export function completionContext(text: string, caret: number): CompletionContext {
  const before = text.slice(0, caret);
  const open = before.lastIndexOf('{{');
  const close = before.lastIndexOf('}}');
  if (open < 0 || close > open) {
    return { inTag: false, path: '', partial: '', replaceFrom: caret };
  }
  const expr = before.slice(open + 2);
  const m = PATH_AT_END.exec(expr);
  if (!m) {
    return { inTag: true, path: '', partial: '', replaceFrom: caret };
  }
  const pathWithDot = m[1];
  const partial = m[2];
  // A path is only a continuation when it follows the start of the expression or an operator/space, not e.g. a
  // string literal's content; everything the regex matched is identifiers and get(...) calls, so that holds.
  return {
    inTag: true,
    path: pathWithDot.endsWith('.') ? pathWithDot.slice(0, -1) : pathWithDot,
    partial,
    replaceFrom: caret - partial.length,
  };
}

/** Items matching what has been typed: prefix matches first, then substring matches (name or friendly label). */
export function filterItems(items: CompletionItem[], partial: string): CompletionItem[] {
  const q = partial.toLowerCase();
  if (!q) {
    return items;
  }
  const prefix = items.filter(i => i.name.toLowerCase().startsWith(q));
  const contains = items.filter(i => !prefix.includes(i)
    && (i.name.toLowerCase().includes(q) || (i.label ?? '').toLowerCase().includes(q)));
  return [...prefix, ...contains];
}

/** Whether choosing this item leads to further completions (its children are offered after a dot). */
export function drillsDown(item: CompletionItem): boolean {
  return item.kind === 'object' || item.kind === 'map' || item.kind === 'list';
}

const OPERATORS = new Set(['*', '/', '+', '-']);

export interface Insertion {
  text: string;
  caret: number;
}

/**
 * The text after choosing `item` at `ctx`. Outside a tag a new {{ … }} is opened around it; an object/map/list
 * gets a trailing dot so its children are offered next; an operator replaces the dot that led to it.
 */
export function applyCompletion(text: string, caret: number, ctx: CompletionContext, item: CompletionItem): Insertion {
  const drill = drillsDown(item);
  if (!ctx.inTag) {
    const inner = item.insert + (drill ? '.' : '');
    const inserted = '{{ ' + inner + ' }}';
    const newCaret = caret + 3 + inner.length;
    return { text: text.slice(0, caret) + inserted + text.slice(caret), caret: drill ? newCaret : caret + inserted.length };
  }
  let from = ctx.replaceFrom;
  let insert = item.insert + (drill ? '.' : '');
  if (OPERATORS.has(item.name) && from > 0 && text[from - 1] === '.') {
    from -= 1;
    insert = ' ' + item.insert;
  }
  return { text: text.slice(0, from) + insert + text.slice(caret), caret: from + insert.length };
}
