import { statusOf } from '../http/http-errors';
import { TelemetryAccepted, TelemetryBatch, TelemetryEvent, TelemetryEventBody } from './telemetry.models';

/** A batch is sent every 2 s, or right away once this many events are waiting. */
export const BATCH_EVENTS = 1000;
/** Unsent events kept while the server cannot be reached; the oldest are dropped beyond it. */
export const MAX_BUFFERED = 20_000;
/**
 * Batches stay below the 64 KiB a browser lets sendBeacon (keepalive) carry, so any pending batch can go with the
 * beacon when the page closes; the server itself accepts up to 256 KB.
 */
export const MAX_BATCH_BYTES = 56 * 1024;
const MAX_RETRY_DELAY_MS = 30_000;

export interface StreamStats {
  sent: number;
  dropped: number;
  failures: number;
}

export interface StreamOptions {
  taskId: string;
  /** Where the task's telemetry continues: seq and the end of the time scale of its last stored batch. */
  nextSeq: number;
  lastT: number;
  beaconToken: string | null;
  send: (taskId: string, batch: TelemetryBatch) => Promise<TelemetryAccepted>;
  /** performance.now() */
  now: () => number;
}

/**
 * Telemetry of one task: stamps events with {@code t}, buffers them and uploads batches with increasing seq. A
 * batch, once formed, is resent unchanged until the server stores it (the server is idempotent by seq), so a
 * retry never produces a gap or a duplicate; seq advances only on success. Network and server failures are
 * retried with an exponential delay (1, 2, 4… up to 30 s); a closed or foreign task (409/403) stops the stream.
 */
export class TelemetryStream {
  readonly taskId: string;
  readonly stats: StreamStats = { sent: 0, dropped: 0, failures: 0 };
  closed = false;

  private seq: number;
  private readonly origin: number;
  private readonly offset: number;
  private lastT: number;
  private beaconToken: string | null;
  private events: TelemetryEvent[] = [];
  private pending: TelemetryBatch | null = null;
  private inFlight: Promise<void> | null = null;
  private retryAt = 0;
  private readonly send: StreamOptions['send'];
  private readonly now: () => number;

  constructor(options: StreamOptions) {
    this.taskId = options.taskId;
    this.seq = options.nextSeq;
    this.offset = options.lastT;
    this.lastT = options.lastT;
    this.beaconToken = options.beaconToken;
    this.send = options.send;
    this.now = options.now;
    this.origin = options.now();
  }

  /** Records an event at the current time; returns it for later changes (e.g. its edit source). */
  record(body: TelemetryEventBody): TelemetryEvent | null {
    if (this.closed) {
      return null;
    }
    // continues the task's time scale; never goes back, even if the clock does
    const t = Math.max(this.lastT, Math.round((this.offset + this.now() - this.origin) * 10) / 10);
    this.lastT = t;
    const event = { ...body, t } as TelemetryEvent;
    this.events.push(event);
    const overflow = this.buffered() - MAX_BUFFERED;
    if (overflow > 0) {
      this.events.splice(0, overflow);
      this.stats.dropped += overflow;
    }
    return event;
  }

  /** Events not yet stored by the server. */
  buffered(): number {
    return this.events.length + (this.pending?.events.length ?? 0);
  }

  /** Enough events for a full batch. */
  full(): boolean {
    return this.events.length >= BATCH_EVENTS;
  }

  lastSeq(): number {
    return this.seq - 1;
  }

  /**
   * Uploads what is buffered, batch after batch, unless a retry delay is running; {@code force} ignores the delay
   * (before a submit or the end of the session nothing may wait). Concurrent calls share the upload in flight.
   */
  flush(force = false): Promise<void> {
    if (force && !this.inFlight) {
      this.retryAt = 0;
    }
    this.inFlight ??= this.upload().finally(() => {
      this.inFlight = null;
    });
    return this.inFlight;
  }

  /**
   * The body for navigator.sendBeacon when the page is hidden or closed: the pending batch (or a new one) with the
   * task's one-time token, also while that batch is being sent as JSON (the browser may cancel the request when the
   * page closes). The batch stays pending: if the page lives on, it is resent as JSON and the server answers
   * "duplicate" when the beacon arrived.
   */
  beaconBody(): string | null {
    if (this.closed || !this.beaconToken) {
      return null;
    }
    const batch = this.batch();
    return batch ? JSON.stringify({ ...batch, beaconToken: this.beaconToken }) : null;
  }

  private async upload(): Promise<void> {
    while (!this.closed && this.now() >= this.retryAt) {
      const batch = this.batch();
      if (!batch) {
        return;
      }
      try {
        const accepted = await this.send(this.taskId, batch);
        this.pending = null;
        // the server may keep the batch under a later seq when its seq was taken (docs/telemetry.md)
        this.seq = Math.max(batch.seq, accepted.seq) + 1;
        this.stats.sent += batch.events.length;
        this.retryAt = 0;
        this.stats.failures = 0;
        if (accepted.beaconToken) {
          this.beaconToken = accepted.beaconToken;
        }
      } catch (error) {
        this.fail(error, batch);
        return;
      }
    }
  }

  private fail(error: unknown, batch: TelemetryBatch): void {
    const status = statusOf(error);
    if (status === 409 || status === 403 || status === 401) {
      // 401: the candidate's access ended (session finished or expired)
      // the task is closed or not the candidate's any more: nothing will be accepted
      this.closed = true;
      this.stats.dropped += this.buffered();
      this.events = [];
      this.pending = null;
    } else if (status === 400 || status === 413) {
      // not accepted as it is: dropped, and its seq is reused so that seq stays without gaps
      this.stats.dropped += batch.events.length;
      this.pending = null;
    } else {
      this.stats.failures++;
      this.retryAt = this.now() + Math.min(MAX_RETRY_DELAY_MS, 1000 * 2 ** (this.stats.failures - 1));
    }
  }

  /** The pending batch, or a new one of the oldest events (by count and size). */
  private batch(): TelemetryBatch | null {
    if (this.pending) {
      return this.pending;
    }
    if (this.events.length === 0) {
      return null;
    }
    let bytes = 0;
    let count = 0;
    while (count < this.events.length && count < BATCH_EVENTS) {
      const size = approximateBytes(this.events[count]);
      if (count > 0 && bytes + size > MAX_BATCH_BYTES) {
        break;
      }
      bytes += size;
      count++;
    }
    // copies: a batch, once formed, is resent exactly as it was first sent
    const events = this.events.splice(0, count).map((event) => ({ ...event }));
    this.pending = {
      seq: this.seq,
      clientTsStart: events[0].t,
      clientTsEnd: events[events.length - 1].t,
      events,
    };
    return this.pending;
  }
}

/** Upper bound of the UTF-8 size of an event in JSON: a character of text takes at most 3 bytes. */
function approximateBytes(event: TelemetryEvent): number {
  const text = 'text' in event ? event.text : '';
  return JSON.stringify(event).length + 1 + text.length * 2;
}
