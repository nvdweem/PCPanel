import { Component, OnInit } from '@angular/core';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LightingConfig } from '../../models/models';
import { DeviceService } from '../../services/device.service';

@Component({
  selector: 'app-lighting',
  standalone: true,
  imports: [RouterModule, FormsModule],
  templateUrl: './lighting.component.html',
  styleUrl: './lighting.component.scss'
})
export class LightingComponent implements OnInit {
  serial = '';
  config: LightingConfig | null = null;

  constructor(
    private route: ActivatedRoute,
    private deviceService: DeviceService
  ) {}

  ngOnInit(): void {
    this.serial = this.route.snapshot.paramMap.get('serial')!;
    this.deviceService.getLighting(this.serial).subscribe(c => this.config = c);
  }

  save(): void {
    if (!this.config) return;
    this.deviceService.setLighting(this.serial, this.config).subscribe(() => {
      alert('Lighting saved!');
    });
  }
}
