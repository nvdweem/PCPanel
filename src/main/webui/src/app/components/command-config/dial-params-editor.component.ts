import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DialCommandParams } from '../../models/models';

@Component({
  selector: 'app-dial-params-editor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="dial-params" *ngIf="params">
      <div class="field-row checkbox-row">
        <label><input type="checkbox" [(ngModel)]="params.invert" /> Invert</label>
      </div>
      <div class="field-row">
        <label>Move start (0–100, blank = default)</label>
        <input type="number" [(ngModel)]="params.moveStart" min="0" max="100" placeholder="0" />
      </div>
      <div class="field-row">
        <label>Move end (0–100, blank = default)</label>
        <input type="number" [(ngModel)]="params.moveEnd" min="0" max="100" placeholder="100" />
      </div>
    </div>
  `,
  styles: [`
    .dial-params { border-top: 1px solid #333; padding-top: 10px; margin-top: 4px; display: flex; flex-direction: column; gap: 8px; }
    .field-row { display: flex; flex-direction: column; gap: 4px; }
    .field-row label { font-size: 12px; color: #aaa; }
    .field-row input { padding: 6px 8px; border: 1px solid #444; border-radius: 4px; background: #2a2a2a; color: #eee; font-size: 13px; }
    .checkbox-row label { display: flex; align-items: center; gap: 6px; color: #ccc; font-size: 13px; cursor: pointer; }
  `]
})
export class DialParamsEditorComponent {
  @Input() params!: DialCommandParams;
}
