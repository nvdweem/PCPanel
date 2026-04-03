import { Component, OnInit } from '@angular/core';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatSliderModule } from '@angular/material/slider';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { LightingConfig } from '../../models/models';
import { DeviceService } from '../../services/device.service';

@Component({
  selector: 'app-lighting',
  standalone: true,
  imports: [RouterModule, FormsModule, MatToolbarModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatSelectModule, MatSliderModule, MatCardModule, MatProgressSpinnerModule],
  templateUrl: './lighting.component.html',
  styleUrl: './lighting.component.scss'
})
export class LightingComponent implements OnInit {
  serial = '';
  config: LightingConfig | null = null;
  constructor(private route: ActivatedRoute, private deviceService: DeviceService) {}
  ngOnInit(): void {
    this.serial = this.route.snapshot.paramMap.get('serial')!;
    this.deviceService.getLighting(this.serial).subscribe(c => this.config = c);
  }
  save(): void {
    if (!this.config) return;
    this.deviceService.setLighting(this.serial, this.config).subscribe(() => alert('Lighting saved!'));
  }
}
