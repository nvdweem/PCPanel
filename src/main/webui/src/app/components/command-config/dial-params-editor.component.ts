import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { DialCommandParams } from '../../models/generated/backend.types';
import { FieldTree, FormField } from '@angular/forms/signals';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogClose, MatDialogContent } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';

export interface DialParamsEditorComponentData {
  command: FieldTree<Required<DialCommandParams>>;
}

@Component({
  selector: 'app-dial-params-editor',
  imports: [FormsModule, MatCheckboxModule, MatFormFieldModule, MatInputModule, MatDialogContent, FormField, MatDialogActions, MatButton, MatDialogClose],
  templateUrl: './dial-params-editor.component.html',
  styleUrl: './dial-params-editor.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialParamsEditorComponent {
  protected readonly data = inject(MAT_DIALOG_DATA) as DialParamsEditorComponentData;
  protected readonly form = this.data.command;
}
