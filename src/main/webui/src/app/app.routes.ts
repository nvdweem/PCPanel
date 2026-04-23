import { Routes } from '@angular/router';
import { HomeComponent } from './pages/home/home.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'device/:serial', loadComponent: () => import('./pages/device/device.component').then(m => m.DeviceComponent) },
  { path: 'lighting/:serial', loadComponent: () => import('./pages/lighting/lighting.component').then(m => m.LightingComponent) },
  { path: 'settings', loadComponent: () => import('./pages/settings/settings.component').then(m => m.SettingsComponent) },
  { path: '**', redirectTo: '' }
];
