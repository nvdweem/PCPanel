import { Injectable, OnDestroy } from '@angular/core';
import { Subject } from 'rxjs';
import { WsButtonEvent, WsEvent, WsKnobEvent } from '../models/models';

@Injectable({ providedIn: 'root' })
export class EventService implements OnDestroy {
  private socket: WebSocket | null = null;
  private reconnectTimer: any;

  readonly events$ = new Subject<WsEvent | WsKnobEvent | WsButtonEvent>();

  constructor() {
    this.connect();
  }

  private connect(): void {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = `${protocol}//${window.location.host}/ws/events`;
    this.socket = new WebSocket(url);

    this.socket.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        this.events$.next(data);
      } catch {
        // ignore parse errors
      }
    };

    this.socket.onclose = () => {
      this.reconnectTimer = setTimeout(() => this.connect(), 3000);
    };

    this.socket.onerror = () => {
      this.socket?.close();
    };
  }

  ngOnDestroy(): void {
    clearTimeout(this.reconnectTimer);
    this.socket?.close();
    this.events$.complete();
  }
}
