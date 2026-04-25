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
import { ControlAssignmentsUpdateDto, SingleKnobLightingConfig, SingleSliderLightingConfig } from '../../../models/generated/backend.types';
import { ControlType } from '../events';
import { CommandsComponent } from '../../../components/command-config/commands/commands.component';

type ControlLighting = SingleKnobLightingConfig | SingleSliderLightingConfig;

export interface CommandDialogData extends ControlAssignmentsUpdateDto {
  title: string;
  controlType: ControlType;
  lighting?: ControlLighting;
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

  protected lightingType() {
    switch (this.data.controlType) {
      case 'dial':
        return 'Knob lighting';
      case 'slider':
        return 'Slider lighting';
      case 'logo':
        return 'Logo lighting';
      default:
        return 'Lighting';
    }
  }

  protected lightingMode() {
    return this.form().value().lighting?.mode;
  }

  protected showKnobSecondColor() {
    return this.data.controlType === 'dial' && this.lightingMode() === 'VOLUME_GRADIENT';
  }

  protected showSliderSecondColor() {
    if (this.data.controlType !== 'slider') {
      return false;
    }
    const mode = this.lightingMode();
    return mode === 'STATIC_GRADIENT' || mode === 'VOLUME_GRADIENT';
  }

  protected saveOptions() {
    this.dialogRef.close(this.form().value());
  }
}
