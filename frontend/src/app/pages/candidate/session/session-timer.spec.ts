import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SessionTimer } from './session-timer';

describe('SessionTimer', () => {
  beforeEach(() => {
    vi.useFakeTimers({ now: new Date('2026-09-01T09:00:00Z') });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function create(remainingSeconds: number): ComponentFixture<SessionTimer> {
    const fixture = TestBed.createComponent(SessionTimer);
    fixture.componentRef.setInput('remainingSeconds', remainingSeconds);
    fixture.componentRef.setInput('syncedAt', Date.now());
    fixture.detectChanges();
    return fixture;
  }

  function timer(fixture: ComponentFixture<SessionTimer>): HTMLElement {
    return (fixture.nativeElement as HTMLElement).querySelector('[data-testid="timer"]')!;
  }

  function tick(fixture: ComponentFixture<SessionTimer>, ms: number): void {
    vi.advanceTimersByTime(ms);
    fixture.detectChanges();
  }

  it('shows minutes and seconds and counts down by the clock', () => {
    const fixture = create(125);
    expect(timer(fixture).textContent?.trim()).toBe('02:05');

    tick(fixture, 5000);
    expect(timer(fixture).textContent?.trim()).toBe('02:00');
  });

  it('shows hours for a long remaining time', () => {
    expect(timer(create(5400)).textContent?.trim()).toBe('1:30:00');
  });

  it('warns in the last five minutes and alarms in the last minute', () => {
    const fixture = create(301);
    expect(timer(fixture).classList).not.toContain('timer--warn');

    tick(fixture, 1000);
    expect(timer(fixture).classList).toContain('timer--warn');

    tick(fixture, 240_000);
    expect(timer(fixture).classList).toContain('timer--danger');
    expect(timer(fixture).classList).not.toContain('timer--warn');
  });

  it('emits expired once when the time is over and never goes below zero', () => {
    const fixture = create(2);
    let expired = 0;
    fixture.componentInstance.expired.subscribe(() => expired++);

    tick(fixture, 1000);
    expect(expired).toBe(0);
    tick(fixture, 2000);
    expect(expired).toBe(1);
    expect(timer(fixture).textContent?.trim()).toBe('00:00');

    tick(fixture, 5000);
    expect(expired).toBe(1);
  });

  it('follows a re-sync with the server time', () => {
    const fixture = create(600);
    tick(fixture, 10_000);
    expect(timer(fixture).textContent?.trim()).toBe('09:50');

    // the server says less time is left than the local clock thinks
    fixture.componentRef.setInput('remainingSeconds', 300);
    fixture.componentRef.setInput('syncedAt', Date.now());
    fixture.detectChanges();
    expect(timer(fixture).textContent?.trim()).toBe('05:00');
  });
});
