import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialCommandParams } from '../../models/models';

@Component({
  selector: 'app-dial-params-editor',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './dial-params-editor.component.html',
  styleUrl: './dial-params-editor.component.scss'
})
export class DialParamsEditorComponent {
  @Input() params!: DialCommandParams;
}
