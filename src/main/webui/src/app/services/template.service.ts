import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TemplateCatalogDto, TemplatePreviewDto, TemplatePreviewRequestDto } from '../models/generated/backend.types';

/** Where a template belongs, so its variables and preview reflect that control's actions and position. */
export interface TemplateContext {
  serial?: string | null;
  control?: number | null;
  /** overlay | rotate | press | dblpress | release */
  slot?: string | null;
  min?: number | null;
  max?: number | null;
  formula?: string | null;
}

@Injectable({ providedIn: 'root' })
export class TemplateService {
  private readonly http = inject(HttpClient);

  catalog(path: string, ctx: TemplateContext | null): Observable<TemplateCatalogDto> {
    let params = new HttpParams().set('path', path);
    if (ctx?.serial) params = params.set('serial', ctx.serial);
    if (ctx?.control != null) params = params.set('control', ctx.control);
    if (ctx?.slot) params = params.set('slot', ctx.slot);
    return this.http.get<TemplateCatalogDto>('/api/templates/catalog', { params });
  }

  preview(source: string, ctx: TemplateContext | null): Observable<TemplatePreviewDto> {
    const body: TemplatePreviewRequestDto = {
      source,
      serial: ctx?.serial ?? undefined,
      control: ctx?.control ?? 0,
      slot: ctx?.slot ?? undefined,
      min: ctx?.min ?? undefined,
      max: ctx?.max ?? undefined,
      formula: ctx?.formula || undefined,
    };
    return this.http.post<TemplatePreviewDto>('/api/templates/preview', body);
  }
}
