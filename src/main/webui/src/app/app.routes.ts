import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./pages/home/home.component').then(m => m.HomeComponent) },
  { path: 'device/:serial', loadComponent: () => import('./pages/device/device.component').then(m => m.DeviceComponent) },
  { path: 'control/:serial/:index', loadComponent: () => import('./pages/control/control.component').then(m => m.ControlComponent) },
  { path: 'lighting/:serial', loadComponent: () => import('./pages/lighting/lighting.component').then(m => m.LightingComponent) },
  { path: 'settings', loadComponent: () => import('./pages/settings/settings.component').then(m => m.SettingsComponent) },
  { path: '**', redirectTo: '' },
];
