import { signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { SettingsDto } from '../../models/generated/backend.types';

/**
 * One backend-rendered overlay preview pane: posts a settings snapshot to the real overlay renderer and
 * exposes the resulting PNG as an object URL. Requests are debounced, and a newer request supersedes an
 * in-flight one so a slow response can't overwrite a fresher image.
 */
export class OverlayPreviewRenderer {
  /** Object-URL of the rendered overlay PNG, or null while nothing has rendered. */
  readonly url = signal<string | null>(null);
  private objUrl: string | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private request: Subscription | null = null;

  constructor(private readonly http: HttpClient, private readonly debounceMs = 100) {}

  schedule(s: SettingsDto): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.timer = null;
      this.request?.unsubscribe();
      this.request = this.http.post('/api/overlay/preview', s, { responseType: 'blob' }).subscribe({
        next: blob => this.setUrl(blob.size ? URL.createObjectURL(blob) : null),
        error: () => this.setUrl(null),
      });
    }, this.debounceMs);
  }

  dispose(): void {
    if (this.timer) clearTimeout(this.timer);
    this.request?.unsubscribe();
    this.setUrl(null);
  }

  private setUrl(url: string | null): void {
    if (this.objUrl) URL.revokeObjectURL(this.objUrl);
    this.objUrl = url;
    this.url.set(url);
  }
}

/** Position a preview image inside its mock desktop per overlayPosition + (scaled) edge padding. */
export function overlayPreviewStyle(s: SettingsDto | null | undefined): Record<string, string> {
  const pos = s?.overlayPosition ?? 'bottomRight';
  const pad = Math.max(6, Math.min(36, Math.round((s?.overlayPadding ?? 16) * 0.6))) + 'px';
  const st: Record<string, string> = {};
  const tf: string[] = [];
  if (pos.startsWith('top')) st['top'] = pad;
  else if (pos.startsWith('bottom')) st['bottom'] = pad;
  else { st['top'] = '50%'; tf.push('translateY(-50%)'); }
  if (pos.endsWith('Left')) st['left'] = pad;
  else if (pos.endsWith('Right')) st['right'] = pad;
  else { st['left'] = '50%'; tf.push('translateX(-50%)'); }
  if (tf.length) st['transform'] = tf.join(' ');
  return st;
}
