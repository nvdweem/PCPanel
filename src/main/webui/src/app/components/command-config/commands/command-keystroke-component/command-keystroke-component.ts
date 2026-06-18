import { ChangeDetectionStrategy, Component, computed, linkedSignal, signal } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { FormsModule } from '@angular/forms';
import { MatFormField, MatHint, MatInput, MatLabel } from '@angular/material/input';
import { MatRadioButton, MatRadioChange, MatRadioGroup } from '@angular/material/radio';
import { MatButton } from '@angular/material/button';
import { CommandKeystroke, KeystrokeType } from '../../../../models/generated/backend.types';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-keystroke-component',
  imports: [
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatHint,
    MatInput,
    MatRadioButton,
    MatRadioGroup,
    MatButton,
  ],
  templateUrl: './command-keystroke-component.html',
  styleUrl: './command-keystroke-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandKeystrokeComponent extends CommandComponent<CommandKeystroke> {
  /** Pretty labels for the modifier tokens stored in the keystroke string. */
  private static readonly MODIFIER_LABELS: Record<string, string> = {
    ctrl: 'Ctrl', shift: 'Shift', alt: 'Alt', meta: 'Meta',
  };

  /** browser KeyboardEvent.code → backend AWT-VK-style token (letters/digits/F-keys handled by regex). */
  private static readonly CODE_MAP: Record<string, string> = {
    Enter: 'ENTER', NumpadEnter: 'ENTER', Tab: 'TAB', Space: 'SPACE',
    Backspace: 'BACK_SPACE', Escape: 'ESCAPE', Delete: 'DELETE', Insert: 'INSERT',
    Home: 'HOME', End: 'END', PageUp: 'PAGE_UP', PageDown: 'PAGE_DOWN',
    ArrowLeft: 'LEFT', ArrowUp: 'UP', ArrowRight: 'RIGHT', ArrowDown: 'DOWN',
    Minus: 'MINUS', Equal: 'EQUALS', BracketLeft: 'OPEN_BRACKET', BracketRight: 'CLOSE_BRACKET',
    Backslash: 'BACK_SLASH', Semicolon: 'SEMICOLON', Quote: 'QUOTE', Comma: 'COMMA',
    Period: 'PERIOD', Slash: 'SLASH', Backquote: 'BACK_QUOTE',
  };

  protected mode = linkedSignal<KeystrokeType>(() => this.field().type().value() ?? 'KEY');
  protected recording = signal(false);

  /** The stored "ctrl+shift+A" combo rendered as "Ctrl + Shift + A" for display. */
  protected prettyKeystroke = computed(() => {
    const raw = this.field().keystroke().value();
    if (!raw) {
      return '';
    }
    return raw.split('+').map(token => CommandKeystrokeComponent.MODIFIER_LABELS[token] ?? token).join(' + ');
  });

  protected changeMode(event: MatRadioChange) {
    this.field().type().value.set(event.value as KeystrokeType);
  }

  protected clearKeystroke() {
    this.field().keystroke().value.set('');
  }

  /** Records the pressed combination from a real keydown, ignoring modifier-only presses. */
  protected onKeyDown(event: KeyboardEvent) {
    event.preventDefault();
    event.stopPropagation();
    const key = CommandKeystrokeComponent.mapCode(event.code);
    if (!key) {
      return; // a modifier on its own — wait for the actual key
    }
    const combo: string[] = [];
    if (event.ctrlKey) {
      combo.push('ctrl');
    }
    if (event.shiftKey) {
      combo.push('shift');
    }
    if (event.altKey) {
      combo.push('alt');
    }
    if (event.metaKey) {
      combo.push('meta');
    }
    combo.push(key);
    this.field().keystroke().value.set(combo.join('+'));
  }

  private static mapCode(code: string): string {
    const letter = /^Key([A-Z])$/.exec(code);
    if (letter) {
      return letter[1];
    }
    const digit = /^(?:Digit|Numpad)([0-9])$/.exec(code);
    if (digit) {
      return digit[1];
    }
    const fn = /^F([0-9]{1,2})$/.exec(code);
    if (fn) {
      return `F${fn[1]}`;
    }
    return CommandKeystrokeComponent.CODE_MAP[code] ?? '';
  }
}
