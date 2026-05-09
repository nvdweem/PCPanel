import { booleanAttribute, Component, input, viewChild } from '@angular/core';
import { FieldTree } from '@angular/forms/signals';
import { MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { ColorPickerDirective } from 'ngx-color-picker';

@Component({
  selector: 'app-color-picker',
  imports: [
    MatFormField,
    MatLabel,
    MatInput,
    ColorPickerDirective
  ],
  templateUrl: './color-picker.html',
  styleUrl: './color-picker.scss',
})
export class ColorPicker {
  field = input.required<FieldTree<string>>();
  alwaysShow = input(false, {transform: booleanAttribute});

  protected picker = viewChild(ColorPickerDirective);
}
