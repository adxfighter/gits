import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LoginPage, safeReturnUrl } from './login-page';

describe('LoginPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function render(returnUrl?: string): { fixture: ComponentFixture<LoginPage>; root: HTMLElement } {
    const fixture = TestBed.createComponent(LoginPage);
    if (returnUrl) {
      fixture.componentRef.setInput('returnUrl', returnUrl);
    }
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  function fill(root: HTMLElement, email: string, password: string): void {
    for (const [id, value] of [['login-email', email], ['login-password', password]]) {
      const input = root.querySelector<HTMLInputElement>(`[data-testid="${id}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    root.querySelector<HTMLButtonElement>('[data-testid="login-submit"]')!.click();
  }

  function answerLogin(respond: (request: ReturnType<HttpTestingController['expectOne']>) => void): void {
    http.expectOne('/api/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    respond(http.expectOne('/api/auth/login'));
  }

  it('signs in and goes back to the page the employer came from', async () => {
    const { fixture, root } = render('/employer/sessions/s-1');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fill(root, ' employer@demo.local ', 'secret');
    answerLogin((request) => {
      expect(request.request.body).toEqual({ email: 'employer@demo.local', password: 'secret' });
      request.flush({ userId: 'u', email: 'employer@demo.local', role: 'EMPLOYER', companyId: 'c', companyName: 'Демо' });
    });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith('/employer/sessions/s-1');
  });

  it('shows the server message for wrong credentials', async () => {
    const { fixture, root } = render();
    fill(root, 'employer@demo.local', 'wrong');
    answerLogin((request) =>
      request.flush({ detail: 'Неверный email или пароль' }, { status: 401, statusText: 'Unauthorized' }),
    );
    await fixture.whenStable();
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="login-error"]')?.textContent).toContain('Неверный email или пароль');
  });

  it('explains the rate limit', async () => {
    const { fixture, root } = render();
    fill(root, 'employer@demo.local', 'wrong');
    answerLogin((request) => request.flush({}, { status: 429, statusText: 'Too Many Requests' }));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="login-error"]')?.textContent).toContain('Слишком много попыток');
  });

  it('asks for both fields before sending anything', () => {
    const { fixture, root } = render();
    fill(root, '', '');
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="login-error"]')?.textContent).toContain('Введите email и пароль');
  });

  it('returns only to pages of the own section of the user, in the app itself', () => {
    expect(safeReturnUrl('/employer/sessions/1', 'EMPLOYER')).toBe('/employer/sessions/1');
    expect(safeReturnUrl('https://evil.example/employer', 'EMPLOYER')).toBe('/employer');
    expect(safeReturnUrl('//evil.example', 'EMPLOYER')).toBe('/employer');
    expect(safeReturnUrl('/employerx.evil', 'EMPLOYER')).toBe('/employer');
    expect(safeReturnUrl('/c/session', 'EMPLOYER')).toBe('/employer');
    expect(safeReturnUrl(undefined, 'EMPLOYER')).toBe('/employer');
    expect(safeReturnUrl('/admin/sessions', 'EMPLOYER')).toBe('/employer');
    expect(safeReturnUrl('/admin/sessions', 'ADMIN')).toBe('/admin/sessions');
    expect(safeReturnUrl('/employer', 'ADMIN')).toBe('/admin/tasks');
  });

  it('takes an administrator to the task bank', async () => {
    const { fixture, root } = render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fill(root, 'admin@demo.local', 'secret');
    answerLogin((request) =>
      request.flush({ userId: 'u', email: 'admin@demo.local', role: 'ADMIN', companyId: null, companyName: null }),
    );
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith('/admin/tasks');
  });
});
