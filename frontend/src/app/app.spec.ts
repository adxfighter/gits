import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { App } from './app';
import { AuthService } from './core/auth/auth.service';

@Component({ template: '' })
class Blank {}

describe('App', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    document.cookie = 'XSRF-TOKEN=test';
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([
          { path: 'employer', component: Blank },
          { path: 'login', component: Blank },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renders the product header', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const header = (fixture.nativeElement as HTMLElement).querySelector('.app-header__logo');
    expect(header?.textContent).toBe('GITS');
  });

  async function employerPage() {
    TestBed.inject(AuthService).user.set({
      userId: 'u',
      email: 'employer@demo.local',
      role: 'EMPLOYER',
      companyId: 'c',
      companyName: 'Демо',
    });
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl('/employer');
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  it('shows the employer and signs out to the login page', async () => {
    const { fixture, root } = await employerPage();
    expect(root.querySelector('[data-testid="header-user"]')?.textContent).toContain('Демо · employer@demo.local');
    root.querySelector<HTMLButtonElement>('[data-testid="logout"]')!.click();
    http.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/login');
    expect(TestBed.inject(AuthService).user()).toBeNull();
    expect(root.querySelector('[data-testid="logout"]')).toBeNull();
  });

  it('stays signed in and says so when the sign-out did not reach the server', async () => {
    const { fixture, root } = await employerPage();
    root.querySelector<HTMLButtonElement>('[data-testid="logout"]')!.click();
    http.expectOne('/api/auth/logout').flush({}, { status: 0, statusText: 'Unknown Error' });
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="logout-error"]')?.textContent).toContain('Не удалось выйти');
    expect(TestBed.inject(Router).url).toBe('/employer');
    expect(TestBed.inject(AuthService).user()).not.toBeNull();
  });
});
