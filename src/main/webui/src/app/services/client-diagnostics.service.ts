import { ErrorHandler, Injectable, computed, signal } from '@angular/core';
import { ClientLogEntry, ClientRequestFailure } from '../models/generated/backend.types';

/** Bounded so a long-running session cannot grow the buffers without limit. */
const MAX_CONSOLE = 200;
const MAX_REQUESTS = 50;
const MAX_MESSAGE = 2000;
const MAX_STACK = 4000;
const MAX_BODY_SNIPPET = 500;

/**
 * Rolling record of what went wrong in the browser: console output, uncaught errors and failed HTTP
 * calls. A user reports the symptom they saw on screen, and the backend log cannot show either the
 * frontend's own errors or how a request looked from the browser's side — this fills that in, and is
 * offered as an attachment when they file a report.
 *
 * Everything is kept in memory only, and is gone on reload. Request bodies are deliberately never
 * recorded: settings saves carry credentials.
 */
@Injectable({ providedIn: 'root' })
export class ClientDiagnosticsService {
  readonly consoleEntries = signal<ClientLogEntry[]>([]);
  readonly failedRequests = signal<ClientRequestFailure[]>([]);
  readonly hasAnything = computed(() => this.consoleEntries().length > 0 || this.failedRequests().length > 0);

  private installed = false;
  /** The unwrapped console.error, so re-logging a captured error cannot record it a second time. */
  private passThroughError: (...args: unknown[]) => void = (...args) => console.error(...args);

  /** Starts capturing. Called once from the app root; repeat calls are ignored. */
  install(): void {
    if (this.installed || typeof window === 'undefined') return;
    this.installed = true;

    for (const level of ['error', 'warn'] as const) {
      const original = console[level].bind(console);
      if (level === 'error') this.passThroughError = original;
      console[level] = (...args: unknown[]) => {
        this.recordConsole(level, args.map(describe).join(' '));
        original(...args);
      };
    }

    window.addEventListener('error', event => {
      this.recordConsole('error', event.message, event.error instanceof Error ? event.error.stack : undefined);
    });
    window.addEventListener('unhandledrejection', event => {
      const reason: unknown = event.reason;
      this.recordConsole('error', describe(reason), reason instanceof Error ? reason.stack : undefined);
    });
  }

  recordConsole(level: string, message: string, stack?: string): void {
    const entry: ClientLogEntry = {
      timestamp: Date.now(),
      level,
      message: clamp(message, MAX_MESSAGE),
      stack: stack ? clamp(stack, MAX_STACK) : undefined,
    };
    this.consoleEntries.update(list => [...list, entry].slice(-MAX_CONSOLE));
  }

  recordFailure(failure: ClientRequestFailure): void {
    this.failedRequests.update(list => [...list, {
      ...failure,
      responseSnippet: failure.responseSnippet ? clamp(failure.responseSnippet, MAX_BODY_SNIPPET) : undefined,
    }].slice(-MAX_REQUESTS));
  }

  clear(): void {
    this.consoleEntries.set([]);
    this.failedRequests.set([]);
  }

  /** Logs to the devtools console without recording, for an error this service already captured. */
  logWithoutRecording(error: unknown): void {
    this.passThroughError(error);
  }
}

/** Angular's uncaught-error hook, so a component failure is captured with its stack. */
@Injectable()
export class DiagnosticsErrorHandler implements ErrorHandler {
  constructor(private readonly diagnostics: ClientDiagnosticsService) {}

  handleError(error: unknown): void {
    this.diagnostics.recordConsole('error', describe(error), error instanceof Error ? error.stack : undefined);
    this.diagnostics.logWithoutRecording(error);
  }
}

function describe(value: unknown): string {
  if (typeof value === 'string') return value;
  if (value instanceof Error) return `${value.name}: ${value.message}`;
  try {
    return JSON.stringify(value) ?? String(value);
  } catch {
    return String(value);
  }
}

function clamp(value: string, limit: number): string {
  return value.length <= limit ? value : `${value.slice(0, limit)}…`;
}
