import {
  AfterViewInit, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, input, model, signal, untracked, viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CdkOverlayOrigin, ConnectedPosition, OverlayModule } from '@angular/cdk/overlay';
import { Subject, debounceTime, of, switchMap, catchError } from 'rxjs';
import { IconComponent } from '../icon/icon.component';
import { TemplateContext, TemplateService } from '../../services/template.service';
import { TemplatePreviewDto } from '../../models/generated/backend.types';
import { CompletionContext, CompletionItem, applyCompletion, completionContext, drillsDown, filterItems } from './template-completion';

/**
 * A text field for a {{ }} template: auto-growing, with variable completion (typing {{ or the { } toggle inside the
 * field) that walks the live variable tree, and a preview of what the template renders for this control right now.
 */
@Component({
  selector: 'pc-template-input',
  standalone: true,
  imports: [OverlayModule, IconComponent],
  template: `
    <div class="tpl" cdkOverlayOrigin #origin="cdkOverlayOrigin">
      <textarea #area class="pc-input ta" [class.mono]="mono()" rows="1" spellcheck="false"
                [placeholder]="placeholder()" [value]="value()"
                role="combobox" aria-autocomplete="list" [attr.aria-expanded]="open()"
                (input)="onInput()" (keydown)="onKey($event)" (click)="onCaretMove()" (blur)="onBlur()"></textarea>
      <button type="button" class="toggle" [class.on]="open()" title="Insert a variable"
              (mousedown)="$event.preventDefault()" (click)="toggle()">
        <span class="braces">{{ braces }}</span>
      </button>
    </div>
    @if (showPreview() && preview(); as p) {
      <div class="preview" [class.err]="!!p.error || p.literalTags.length > 0">
        @if (p.error) {
          <span>{{ p.error }}</span>
        } @else if (p.literalTags.length > 0) {
          <span>Not a variable, shown as text: {{ p.literalTags.join(', ') }}</span>
        } @else {
          <span class="plabel">Preview</span><span class="pval">{{ p.output }}</span>
        }
      </div>
    }

    <ng-template cdkConnectedOverlay [cdkConnectedOverlayOrigin]="origin" [cdkConnectedOverlayOpen]="open()"
                 [cdkConnectedOverlayPositions]="positions" [cdkConnectedOverlayOffsetY]="4"
                 [cdkConnectedOverlayMinWidth]="280" (detach)="open.set(false)">
      <div class="panel" role="listbox" (mousedown)="$event.preventDefault()">
        @if (path()) { <div class="crumb">{{ path() }}</div> }
        @for (item of visible(); track item.name; let i = $index) {
          <button type="button" class="opt" role="option" [class.active]="i === active()" [attr.aria-selected]="i === active()"
                  (mouseenter)="active.set(i)" (click)="choose(item)">
            <span class="name">{{ item.name }}</span>
            @if (item.label) { <span class="label">{{ item.label }}</span> }
            @if (item.value != null && item.kind !== 'function') { <span class="val">{{ item.value }}</span> }
            @if (isDrill(item)) { <pc-icon name="chevron-right" [size]="12"></pc-icon> }
            @if (item.description) { <span class="desc">{{ item.description }}</span> }
          </button>
        }
        @if (visible().length === 0) { <div class="empty">{{ loading() ? 'Loading…' : 'No matches' }}</div> }
      </div>
    </ng-template>
  `,
  styles: [`
    :host { display: block; }
    .tpl { position: relative; }
    textarea { width: 100%; resize: none; overflow: hidden; padding-right: 38px; min-height: 34px; box-sizing: border-box; }
    .toggle {
      position: absolute; top: 5px; right: 5px; height: 24px; min-width: 28px; padding: 0 5px;
      border: 1px solid var(--line); border-radius: var(--r-sm); background: transparent; color: var(--text-3);
      cursor: pointer; font-family: var(--font-mono, monospace); font-size: 11.5px; line-height: 1;
    }
    .toggle:hover, .toggle.on { color: var(--accent, #FFB020); border-color: var(--accent, #FFB020); }
    .preview { margin-top: 4px; font-size: 11.5px; color: var(--text-3); display: flex; gap: 6px; min-width: 0; }
    .preview .plabel { text-transform: uppercase; letter-spacing: .04em; font-size: 10px; flex: none; padding-top: 1px; }
    .preview .pval { color: var(--text-soft); font-family: var(--font-mono, monospace); white-space: pre-wrap; word-break: break-word; }
    .preview.err { color: var(--err-text); }
    .panel {
      background: var(--popover); border: 1px solid var(--raised-line); border-radius: var(--r-md); box-shadow: var(--sh-menu);
      padding: 5px; max-height: 320px; overflow-y: auto; min-width: 280px; max-width: 460px;
    }
    .crumb { font-family: var(--font-mono, monospace); font-size: 11px; color: var(--text-3); padding: 3px 10px 6px; }
    .opt {
      display: grid; grid-template-columns: auto 1fr auto auto; align-items: center; column-gap: 8px; width: 100%;
      text-align: left; border: none; background: transparent; color: var(--text-soft); cursor: pointer;
      font-family: var(--font-ui); font-size: 12.5px; padding: 6px 10px; border-radius: var(--r-sm);
    }
    .opt.active { background: var(--line); color: var(--text-1); }
    .name { font-family: var(--font-mono, monospace); }
    .label { color: var(--text-2); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .val { font-family: var(--font-mono, monospace); color: var(--text-3); max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .opt pc-icon { color: var(--text-3); }
    .desc { grid-column: 1 / -1; font-size: 11px; color: var(--text-3); }
    .empty { padding: 8px 10px; font-size: 12px; color: var(--text-3); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TemplateInputComponent implements AfterViewInit {
  private readonly templates = inject(TemplateService);
  private readonly destroyRef = inject(DestroyRef);

  readonly value = model<string>('');
  readonly placeholder = input<string>('');
  readonly mono = input<boolean>(true);
  readonly context = input<TemplateContext | null>(null);
  readonly showPreview = input<boolean>(true);

  readonly open = signal(false);
  readonly active = signal(0);
  readonly path = signal('');
  readonly partial = signal('');
  readonly loading = signal(false);
  readonly items = signal<CompletionItem[]>([]);
  readonly preview = signal<TemplatePreviewDto | null>(null);
  readonly visible = computed(() => filterItems(this.items(), this.partial()));
  readonly braces = '{ }';
  readonly positions: ConnectedPosition[] = [
    { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top' },
    { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom' },
  ];

  private readonly area = viewChild.required<ElementRef<HTMLTextAreaElement>>('area');
  private readonly catalogRequests = new Subject<string>();
  private readonly previewRequests = new Subject<string>();
  private loadedPath: string | null = null;
  private forced = false;

  constructor() {
    this.catalogRequests.pipe(
      switchMap(path => this.templates.catalog(path, this.context()).pipe(catchError(() => of({ path, items: [] })))),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(result => {
      this.loading.set(false);
      this.items.set(result.items);
      this.active.set(0);
    });
    this.previewRequests.pipe(
      debounceTime(300),
      switchMap(source => source.includes('{{') ? this.templates.preview(source, this.context()).pipe(catchError(() => of(null))) : of(null)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(p => this.preview.set(p));
    // Keep the height and preview in step with values set from outside (profile switch, command change).
    effect(() => {
      const v = this.value();
      this.context();
      untracked(() => {
        queueMicrotask(() => this.autoGrow());
        this.previewRequests.next(v ?? '');
      });
    });
  }

  ngAfterViewInit(): void {
    this.autoGrow();
  }

  isDrill(item: CompletionItem): boolean {
    return drillsDown(item);
  }

  onInput(): void {
    const el = this.area().nativeElement;
    this.value.set(el.value);
    this.autoGrow();
    this.refresh(false);
  }

  onCaretMove(): void {
    if (this.open()) {
      this.refresh(this.forced);
    }
  }

  onBlur(): void {
    this.forced = false;
    this.open.set(false);
  }

  toggle(): void {
    if (this.open()) {
      this.forced = false;
      this.open.set(false);
      return;
    }
    this.forced = true;
    this.area().nativeElement.focus();
    this.refresh(true);
  }

  onKey(event: KeyboardEvent): void {
    if (!this.open()) {
      if (event.key === ' ' && event.ctrlKey) {
        event.preventDefault();
        this.toggle();
      }
      return;
    }
    const count = this.visible().length;
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.active.set(count ? (this.active() + 1) % count : 0);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.active.set(count ? (this.active() - 1 + count) % count : 0);
        break;
      case 'Enter':
      case 'Tab': {
        const item = this.visible()[this.active()];
        if (item) {
          event.preventDefault();
          this.choose(item);
        }
        break;
      }
      case 'Escape':
        event.preventDefault();
        this.forced = false;
        this.open.set(false);
        break;
    }
  }

  choose(item: CompletionItem): void {
    const el = this.area().nativeElement;
    const caret = el.selectionStart ?? el.value.length;
    const ctx = completionContext(el.value, caret);
    const result = applyCompletion(el.value, caret, ctx, item);
    el.value = result.text;
    el.setSelectionRange(result.caret, result.caret);
    this.value.set(result.text);
    this.autoGrow();
    if (drillsDown(item)) {
      this.refresh(true);
    } else {
      this.forced = false;
      this.open.set(false);
    }
  }

  /** Recomputes what the caret is completing; opens the list inside a tag (or when forced by the toggle). */
  private refresh(force: boolean): void {
    const el = this.area().nativeElement;
    const ctx: CompletionContext = completionContext(el.value, el.selectionStart ?? el.value.length);
    if (!ctx.inTag && !force) {
      this.open.set(false);
      return;
    }
    const wasOpen = this.open();
    this.partial.set(ctx.partial);
    this.path.set(ctx.path);
    this.open.set(true);
    if (!wasOpen || this.loadedPath !== ctx.path) {
      this.loadedPath = ctx.path;
      this.loading.set(true);
      this.items.set([]);
      this.catalogRequests.next(ctx.path);
    } else {
      this.active.set(0);
    }
  }

  private autoGrow(): void {
    const el = this.area()?.nativeElement;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = el.scrollHeight + 2 + 'px';
  }
}
