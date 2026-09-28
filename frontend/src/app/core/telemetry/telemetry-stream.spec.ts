import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';

import { BATCH_EVENTS, MAX_BUFFERED, TelemetryStream } from './telemetry-stream';
import { TelemetryAccepted, TelemetryBatch } from './telemetry.models';

/** A server that stores batches by seq like the real one, and can be made to fail. */
class FakeServer {
  readonly stored = new Map<number, TelemetryBatch>();
  readonly requests: TelemetryBatch[] = [];
  failWith: number | null = null;

  send = async (_taskId: string, batch: TelemetryBatch): Promise<TelemetryAccepted> => {
    this.requests.push(structuredClone(batch));
    if (this.failWith !== null) {
      throw new HttpErrorResponse({ status: this.failWith });
    }
    const duplicate = this.stored.has(batch.seq);
    if (!duplicate) {
      this.stored.set(batch.seq, structuredClone(batch));
    }
    return { seq: batch.seq, duplicate, flags: {}, beaconToken: null };
  };
}

function stream(server: FakeServer, clock: { now: number }, nextSeq = 0, lastT = 0): TelemetryStream {
  return new TelemetryStream({
    taskId: 'task',
    nextSeq,
    lastT,
    beaconToken: 'token-1',
    send: server.send,
    now: () => clock.now,
  });
}

describe('TelemetryStream', () => {
  it('stamps events with time from the start of the task and sends them in one batch', async () => {
    const server = new FakeServer();
    const clock = { now: 5000 };
    const s = stream(server, clock);
    clock.now = 5012.34;
    s.record({ type: 'focus' });
    clock.now = 5100;
    s.record({ type: 'blur' });

    await s.flush();

    expect(server.stored.get(0)).toEqual({
      seq: 0,
      clientTsStart: 12.3,
      clientTsEnd: 100,
      events: [
        { type: 'focus', t: 12.3 },
        { type: 'blur', t: 100 },
      ],
    });
    expect(s.buffered()).toBe(0);
    expect(s.lastSeq()).toBe(0);
  });

  it('numbers batches without gaps and continues after a reload where the server left off', async () => {
    const server = new FakeServer();
    const clock = { now: 0 };
    const s = stream(server, clock, 7, 60_000);
    for (let i = 0; i < 3; i++) {
      clock.now += 10;
      s.record({ type: 'focus' });
      await s.flush();
    }
    expect([...server.stored.keys()]).toEqual([7, 8, 9]);
    // the time scale continues after the last stored batch of the earlier page
    expect(server.stored.get(7)!.clientTsStart).toBe(60_010);
  });

  it('splits a long burst into batches of 1 000 events', async () => {
    const server = new FakeServer();
    const clock = { now: 0 };
    const s = stream(server, clock);
    for (let i = 0; i < BATCH_EVENTS * 2 + 5; i++) {
      clock.now += 1;
      s.record({ type: 'kd', keyClass: 'letter', repeat: false });
    }
    expect(s.full()).toBe(true);

    await s.flush();

    expect([...server.stored.values()].map((b) => b.events.length)).toEqual([1000, 1000, 5]);
    const ends = [...server.stored.values()].map((b) => [b.clientTsStart, b.clientTsEnd]);
    // batches follow each other in time
    expect(ends[1][0]).toBeGreaterThan(ends[0][1]);
  });

  it('retries a failed batch unchanged with a growing delay and then goes on', async () => {
    const server = new FakeServer();
    const clock = { now: 0 };
    const s = stream(server, clock);
    s.record({ type: 'focus' });
    server.failWith = 0; // no connection

    await s.flush();
    expect(s.stats.failures).toBe(1);
    // within the delay nothing is sent
    clock.now = 500;
    await s.flush();
    expect(server.requests).toHaveLength(1);

    clock.now = 1000;
    s.record({ type: 'blur' }); // arrives during the outage
    await s.flush();
    expect(server.requests).toHaveLength(2);
    expect(s.stats.failures).toBe(2);

    // the second delay is longer: 2 s
    clock.now = 2500;
    await s.flush();
    expect(server.requests).toHaveLength(2);

    server.failWith = null;
    clock.now = 3100;
    await s.flush();

    // the retried batch is the same as the first attempt; the later event goes in the next batch
    expect(server.requests[2]).toEqual(server.requests[0]);
    expect([...server.stored.keys()]).toEqual([0, 1]);
    expect(server.stored.get(1)!.events.map((e) => e.type)).toEqual(['blur']);
    expect(s.stats.failures).toBe(0);
  });

  it('keeps the batch when the server answers duplicate, e.g. after a beacon landed', async () => {
    const server = new FakeServer();
    const clock = { now: 0 };
    const s = stream(server, clock);
    s.record({ type: 'visibility', state: 'hidden' });
    const beacon = JSON.parse(s.beaconBody()!);
    expect(beacon.beaconToken).toBe('token-1');
    // the beacon arrived and was stored
    server.stored.set(beacon.seq, beacon);

    await s.flush();

    expect(server.requests[0].seq).toBe(0);
    expect(s.lastSeq()).toBe(0);
    expect(s.buffered()).toBe(0);
  });

  it('stops for a closed task', async () => {
    const server = new FakeServer();
    const clock = { now: 0 };
    const s = stream(server, clock);
    s.record({ type: 'focus' });
    server.failWith = 409;

    await s.flush();

    expect(s.closed).toBe(true);
    expect(s.record({ type: 'blur' })).toBeNull();
    expect(s.buffered()).toBe(0);
  });

  it('drops a rejected batch but reuses its seq, so seq has no gaps', async () => {
    const server = new FakeServer();
    const clock = { now: 0 };
    const s = stream(server, clock);
    s.record({ type: 'focus' });
    server.failWith = 400;
    await s.flush();
    expect(s.stats.dropped).toBe(1);

    server.failWith = null;
    s.record({ type: 'blur' });
    await s.flush();
    expect([...server.stored.keys()]).toEqual([0]);
    expect(server.stored.get(0)!.events.map((e) => e.type)).toEqual(['blur']);
  });

  it('keeps at most 20 000 events while offline, dropping the oldest', async () => {
    const server = new FakeServer();
    const clock = { now: 0 };
    const s = stream(server, clock);
    for (let i = 0; i < MAX_BUFFERED + 10; i++) {
      clock.now += 1;
      s.record({ type: 'kd', keyClass: 'letter', repeat: false });
    }
    expect(s.buffered()).toBe(MAX_BUFFERED);
    expect(s.stats.dropped).toBe(10);

    await s.flush();
    expect(server.stored.get(0)!.clientTsStart).toBe(11);
  });

  it('never lets time go backwards within the task', () => {
    const clock = { now: 1000 };
    const s = stream(new FakeServer(), clock);
    clock.now = 1100;
    const first = s.record({ type: 'focus' })!;
    clock.now = 1050; // e.g. a clock adjustment
    const second = s.record({ type: 'blur' })!;
    expect(second.t).toBeGreaterThanOrEqual(first.t);
  });
});
