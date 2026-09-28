import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { InviteRow } from '../../core/employer/employer.models';
import { inviteRow } from './employer-fixtures';
import { INVITES_REFRESH_MS, InvitesPage } from './invites-page';

describe('InvitesPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    // the XSRF cookie is there: changing requests go straight to the API
    document.cookie = 'XSRF-TOKEN=test';
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  async function render(rows: InviteRow[]): Promise<{ fixture: ComponentFixture<InvitesPage>; root: HTMLElement }> {
    const fixture = TestBed.createComponent(InvitesPage);
    fixture.detectChanges();
    http.expectOne('/api/employer/invites').flush(rows);
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  function text(element: Element | null | undefined): string {
    return element?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  it('lists invites with level, status, score and trust as text, not only colour', async () => {
    const { root } = await render([
      inviteRow(),
      inviteRow({
        id: 'invite-2',
        candidateLabel: 'Ольга',
        status: 'CREATED',
        sessionId: null,
        sessionStatus: null,
        finishedAt: null,
        preliminaryScore: null,
        trustLevel: null,
      }),
    ]);
    const rows = root.querySelectorAll('[data-testid="invite-row"]');
    expect(rows).toHaveLength(2);
    expect(text(rows[0])).toContain('Иван П.');
    expect(text(rows[0])).toContain('Middle');
    expect(text(rows[0].querySelector('[data-testid="invite-status"]'))).toBe('Завершено');
    expect(text(rows[0].querySelector('[data-testid="invite-score"]'))).toBe('72,5');
    const trust = rows[0].querySelector('[data-testid="trust"]');
    expect(trust?.getAttribute('data-level')).toBe('YELLOW');
    expect(text(trust)).toBe('Есть замечания');
    expect(rows[0].querySelector('a[data-testid="invite-report"]')?.getAttribute('href')).toBe('/employer/sessions/session-1');
    // an unused invite: no report yet, but it can be revoked
    expect(text(rows[1].querySelector('[data-testid="invite-status"]'))).toBe('Ожидает кандидата');
    expect(rows[1].querySelector('[data-testid="invite-report"]')).toBeNull();
    expect(rows[1].querySelector('[data-testid="invite-revoke"]')).not.toBeNull();
    expect(rows[0].querySelector('[data-testid="invite-revoke"]')).toBeNull();
  });

  it('tells a finished session still being scored and an entered link from a running session', async () => {
    const { root } = await render([
      inviteRow({ preliminaryScore: null, trustLevel: null }),
      inviteRow({ id: 'invite-2', status: 'STARTED', sessionId: null, sessionStatus: null, preliminaryScore: null, trustLevel: null }),
      inviteRow({ id: 'invite-3', status: 'COMPLETED', sessionStatus: 'EXPIRED' }),
    ]);
    const rows = root.querySelectorAll('[data-testid="invite-row"]');
    expect(text(rows[0].querySelector('[data-testid="invite-score"]'))).toBe('считается…');
    expect(text(rows[1].querySelector('[data-testid="invite-status"]'))).toBe('Ссылка открыта');
    expect(text(rows[2].querySelector('[data-testid="invite-status"]'))).toBe('Завершено: время вышло');
  });

  it('filters by status on the server and shows an empty state for the filter', async () => {
    const { fixture, root } = await render([inviteRow()]);
    const select = root.querySelector<HTMLSelectElement>('[data-testid="status-filter"]')!;
    select.value = 'REVOKED';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();
    http.expectOne('/api/employer/invites?status=REVOKED').flush([]);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text(root.querySelector('[data-testid="invites-empty"]'))).toContain('Нет приглашений со статусом «Отозвано»');
  });

  it('refreshes itself while visible and at once when the tab comes back', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(InvitesPage);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="invites-loading"]')).not.toBeNull();
    http.expectOne('/api/employer/invites').flush([inviteRow({ trustLevel: 'GREEN' })]);
    fixture.detectChanges();
    expect(text(root.querySelector('[data-testid="trust"]'))).toBe('Замечаний нет');
    vi.advanceTimersByTime(INVITES_REFRESH_MS);
    http.expectOne('/api/employer/invites').flush([inviteRow({ trustLevel: 'RED' })]);
    fixture.detectChanges();
    expect(text(root.querySelector('[data-testid="trust"]'))).toBe('Серьёзные замечания');
    // a failed refresh keeps the list
    vi.advanceTimersByTime(INVITES_REFRESH_MS);
    http.expectOne('/api/employer/invites').flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(root.querySelectorAll('[data-testid="invite-row"]')).toHaveLength(1);
    expect(root.querySelector('[data-testid="invites-stale"]')).not.toBeNull();
    document.dispatchEvent(new Event('visibilitychange'));
    http.expectOne('/api/employer/invites').flush([inviteRow()]);
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="invites-stale"]')).toBeNull();
    fixture.destroy();
    vi.advanceTimersByTime(INVITES_REFRESH_MS * 3);
    http.expectNone('/api/employer/invites');
  });

  it('invites to create the first invite when there are none', async () => {
    const { root } = await render([]);
    expect(text(root.querySelector('[data-testid="invites-empty"]'))).toContain('Приглашений пока нет');
  });

  it('shows a load error with a retry', async () => {
    const fixture = TestBed.createComponent(InvitesPage);
    fixture.detectChanges();
    http.expectOne('/api/employer/invites').flush({ detail: 'Сервер недоступен' }, { status: 500, statusText: 'Error' });
    await fixture.whenStable();
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(text(root.querySelector('[data-testid="invites-error"]'))).toContain('Сервер недоступен');
    root.querySelector<HTMLButtonElement>('[data-testid="invites-error"] button')!.click();
    http.expectOne('/api/employer/invites').flush([inviteRow()]);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(root.querySelectorAll('[data-testid="invite-row"]')).toHaveLength(1);
  });

  it('creates an invite, shows its link to copy and reloads the list', async () => {
    const { fixture, root } = await render([]);
    root.querySelector<HTMLButtonElement>('[data-testid="invite-open"]')!.click();
    fixture.detectChanges();
    const label = root.querySelector<HTMLInputElement>('[data-testid="invite-label"]')!;
    label.value = 'Пётр, senior';
    label.dispatchEvent(new Event('input'));
    const level = root.querySelector<HTMLSelectElement>('[data-testid="invite-level"]')!;
    level.value = 'SENIOR';
    level.dispatchEvent(new Event('change'));
    root.querySelector<HTMLButtonElement>('[data-testid="invite-create"]')!.click();
    const create = http.expectOne('/api/invites');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ candidateLabel: 'Пётр, senior', targetLevel: 'SENIOR' });
    create.flush({ id: 'invite-9', link: 'http://localhost:8080/c/abc', expiresAt: '2026-10-05T08:00:00Z' });
    fixture.detectChanges();
    expect(root.querySelector<HTMLInputElement>('[data-testid="invite-link"]')?.value).toBe('http://localhost:8080/c/abc');
    expect(root.querySelector('[data-testid="invite-copy"]')).not.toBeNull();
    root.querySelector<HTMLButtonElement>('[data-testid="invite-done"]')!.click();
    fixture.detectChanges();
    http.expectOne('/api/employer/invites').flush([inviteRow({ id: 'invite-9', status: 'CREATED' })]);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(root.querySelectorAll('[data-testid="invite-row"]')).toHaveLength(1);
  });

  it('copies the link, or asks for Ctrl+C when the browser refuses', async () => {
    const { fixture, root } = await render([]);
    root.querySelector<HTMLButtonElement>('[data-testid="invite-open"]')!.click();
    fixture.detectChanges();
    const label = root.querySelector<HTMLInputElement>('[data-testid="invite-label"]')!;
    label.value = 'Пётр';
    label.dispatchEvent(new Event('input'));
    root.querySelector<HTMLButtonElement>('[data-testid="invite-create"]')!.click();
    http.expectOne('/api/invites').flush({ id: 'i', link: 'http://localhost:8080/c/abc', expiresAt: '2026-10-05T08:00:00Z' });
    fixture.detectChanges();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    root.querySelector<HTMLButtonElement>('[data-testid="invite-copy"]')!.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(writeText).toHaveBeenCalledWith('http://localhost:8080/c/abc');
    expect(text(root.querySelector('[data-testid="invite-copy"]'))).toBe('Скопировано');
    writeText.mockRejectedValue(new Error('denied'));
    root.querySelector<HTMLButtonElement>('[data-testid="invite-copy"]')!.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text(root.querySelector('[data-testid="invite-copy-failed"]'))).toContain('Ctrl+C');
    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true });
  });

  it('does not send an invite without a label', async () => {
    const { fixture, root } = await render([]);
    root.querySelector<HTMLButtonElement>('[data-testid="invite-open"]')!.click();
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('[data-testid="invite-create"]')!.click();
    fixture.detectChanges();
    expect(text(root.querySelector('[data-testid="invite-error"]'))).toBe('Укажите метку кандидата.');
  });

  it('revokes an unused invite after a confirmation', async () => {
    const { fixture, root } = await render([inviteRow({ status: 'CREATED', sessionId: null, sessionStatus: null })]);
    root.querySelector<HTMLButtonElement>('[data-testid="invite-revoke"]')!.click();
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('app-confirm-dialog [data-testid="confirm"]')!.click();
    const revoke = http.expectOne('/api/employer/invites/invite-1');
    expect(revoke.request.method).toBe('DELETE');
    revoke.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/employer/invites').flush([inviteRow({ status: 'REVOKED', sessionId: null, sessionStatus: null })]);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text(root.querySelector('[data-testid="invite-status"]'))).toBe('Отозвано');
  });
});
