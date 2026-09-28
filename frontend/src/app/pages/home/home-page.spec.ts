import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { HomePage } from './home-page';

describe('HomePage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HomePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function renderWith(respond: (req: ReturnType<HttpTestingController['expectOne']>) => void) {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    respond(http.expectOne('/api/actuator/health'));
    await fixture.whenStable();
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('[data-testid="api-status"]')?.textContent ?? '';
  }

  it('shows that the API is up', async () => {
    const text = await renderWith((req) => req.flush({ status: 'UP' }));
    expect(text).toContain('работает');
  });

  it('shows that the API is unreachable on network error', async () => {
    const text = await renderWith((req) => req.error(new ProgressEvent('error')));
    expect(text).toContain('недоступен');
  });
});
