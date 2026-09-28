import { Routes } from '@angular/router';

import { employerGuard } from './core/auth/employer.guard';
import { candidateGuard, consentGuard } from './core/candidate/consent.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/home/home-page').then((m) => m.HomePage),
    title: 'GITS',
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login-page').then((m) => m.LoginPage),
    title: 'Вход — GITS',
  },
  {
    path: 'employer',
    canActivate: [employerGuard],
    loadComponent: () => import('./pages/employer/invites-page').then((m) => m.InvitesPage),
    title: 'Приглашения — GITS',
  },
  {
    path: 'employer/sessions/:id',
    canActivate: [employerGuard],
    loadComponent: () => import('./pages/employer/report-page').then((m) => m.ReportPage),
    title: 'Отчёт — GITS',
  },
  {
    path: 'employer/replay/:sessionTaskId',
    canActivate: [employerGuard],
    loadComponent: () => import('./pages/employer/replay/replay-page').then((m) => m.ReplayPage),
    title: 'Воспроизведение — GITS',
  },
  {
    path: 'c/consent',
    canActivate: [candidateGuard],
    loadComponent: () => import('./pages/candidate/consent-page').then((m) => m.ConsentPage),
    title: 'Согласие — GITS',
  },
  {
    path: 'c/intro',
    canActivate: [consentGuard],
    loadComponent: () => import('./pages/candidate/intro-page').then((m) => m.IntroPage),
    title: 'Правила — GITS',
  },
  {
    path: 'c/session',
    canActivate: [consentGuard],
    loadComponent: () => import('./pages/candidate/session/session-page').then((m) => m.SessionPage),
    title: 'Оценка — GITS',
    data: { fullscreen: true },
  },
  {
    path: 'c/done',
    loadComponent: () => import('./pages/candidate/done-page').then((m) => m.DonePage),
    title: 'Оценка завершена — GITS',
  },
  {
    path: 'c/closed',
    loadComponent: () => import('./pages/candidate/done-page').then((m) => m.ClosedPage),
    title: 'Оценка недоступна — GITS',
  },
  {
    path: 'c/offline',
    loadComponent: () => import('./pages/candidate/done-page').then((m) => m.OfflinePage),
    title: 'Нет связи — GITS',
  },
  {
    // must stay after the fixed /c/... pages
    path: 'c/:token',
    loadComponent: () => import('./pages/candidate/enter-page').then((m) => m.EnterPage),
    title: 'Вход — GITS',
  },
  { path: '**', redirectTo: '' },
];
