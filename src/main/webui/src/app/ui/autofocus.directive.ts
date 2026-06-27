import { Directive, ElementRef, afterNextRender, inject } from '@angular/core';

/**
 * Focuses the host element once it's rendered. Put it ON the filter/search input INSIDE a reusable picker
 * component (not at the call site) so every consumer gets auto-focus for free and it can't be forgotten.
 * Works for inputs created inside CDK overlays (afterNextRender fires once the element is in the DOM).
 */
@Directive({
  selector: '[pcAutofocus]',
  standalone: true,
})
export class AutofocusDirective {
  constructor() {
    const el = inject<ElementRef<HTMLElement>>(ElementRef);
    afterNextRender(() => el.nativeElement.focus());
  }
}
