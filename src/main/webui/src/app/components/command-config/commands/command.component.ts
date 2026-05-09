import { Directive, input } from '@angular/core';
import { FieldTree } from '@angular/forms/signals';

@Directive()
export class CommandComponent<T> {
  field = input.required<FieldTree<Required<T>>>();
}
