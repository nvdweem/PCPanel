import { Directive } from '@angular/core';

@Directive()
export class CommandComponent<T> {
  field = input.required<FieldTree<Required<T>>>();
}
