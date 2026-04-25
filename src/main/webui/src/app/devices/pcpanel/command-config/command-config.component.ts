import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatTab, MatTabGroup } from '@angular/material/tabs';
import { form, FormField } from '@angular/forms/signals';
import { ControlAssignmentsUpdateDto } from '../../../models/generated/backend.types';
import { CommandsComponent } from '../../../components/command-config/commands/commands.component';

export interface CommandDialogData extends ControlAssignmentsUpdateDto {
  title: string;
}

@Component({
  selector: 'pcpanel-command-config',
  imports: [FormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule, MatIconModule, MatTabGroup, MatTab, CommandsComponent, FormField],
  templateUrl: './command-config.component.html',
  styleUrl: './command-config.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandConfigComponent {
  private readonly dialogRef = inject(MatDialogRef);
  protected readonly data = inject(MAT_DIALOG_DATA) as CommandDialogData;
  protected readonly form = form(signal(this.data));

  protected saveOptions() {
    this.dialogRef.close(this.form().value());
  }
}
