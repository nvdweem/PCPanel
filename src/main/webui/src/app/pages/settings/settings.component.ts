import { ChangeDetectionStrategy, Component, inject, linkedSignal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FieldTree, form, FormField } from '@angular/forms/signals';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogActions, MatDialogClose, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { SettingsService } from '../../services/settings.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SettingsDto } from '../../models/generated/backend.types';
import { ColorPicker } from '../../components/color-picker/color-picker';

@Component({
  selector: 'app-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterModule, FormField,
    MatToolbarModule, MatTabsModule, MatCheckboxModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule,
    MatDialogContent, MatDialogTitle, MatDialogActions, MatDialogClose, ColorPicker,
  ],
  providers: [SettingsService],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent {
  private readonly settingsService = inject(SettingsService);
  private readonly snack = inject(MatSnackBar);
  private readonly dialogRef = inject(MatDialogRef);
  private settings = linkedSignal(() => this.settingsService.settings.value() ?? false);
  protected settingsForm = form(this.settings);

  protected save() {
    const value = this.settingsForm().value();
    if (!value) {
      return;
    }
    this.settingsService.updateSettings(value)
      .subscribe({
        next: () => {
          this.snack.open('Settings saved', 'OK', {duration: 2000});
          this.dialogRef.close();
        },
        error: () => this.snack.open('Failed to save settings', 'OK', {duration: 3000}),
      });
  }

  protected loaded(form: FieldTree<SettingsDto | false>): form is FieldTree<SettingsDto> {
    return form().value() !== false;
  }
}
