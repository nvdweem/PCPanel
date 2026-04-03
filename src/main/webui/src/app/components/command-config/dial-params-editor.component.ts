import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { DialCommandParams } from '../../models/models';

@Component({
  selector: 'app-dial-params-editor',
  standalone: true,
  imports: [FormsModule, MatCheckboxModule, MatFormFieldModule, MatInputModule],
  templateUrl: './dial-params-editor.component.html',
  styleUrl: './dial-params-editor.component.scss',
})
export class DialParamsEditorComponent {
  @Input() params!: DialCommandParams;
}
