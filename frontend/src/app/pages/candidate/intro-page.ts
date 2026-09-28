import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { CandidateApi } from '../../core/candidate/candidate-api.service';
import { messageOf } from '../../core/http/http-errors';

/** /c/intro — the rules, including an honest description of what is recorded. */
@Component({
  selector: 'app-intro-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card card--wide">
      <h1>Правила оценки</h1>
      <ul class="rules">
        <li><strong>90 минут</strong> на всю сессию. Таймер идёт по часам сервера и не останавливается при закрытии вкладки.</li>
        <li><strong>Разминка и 3 задачи.</strong> Разминка — короткий блок набора кода, он помогает учесть вашу обычную скорость печати; в нём нет автодополнения. Затем три задачи на Java.</li>
        <li>Решение можно <strong>запускать на видимых тестах</strong> сколько угодно раз в пределах лимита (60 запусков на задачу).</li>
        <li><strong>«Отправить решение»</strong> проверяет задачу скрытыми тестами и закрывает её для правок. Отправить можно один раз.</li>
        <li>Код <strong>сохраняется автоматически</strong> каждые 5 секунд; по истечении времени несданные задачи отправляются с последней сохранённой версией.</li>
        <li>Можно пользоваться <strong>документацией Java</strong>. Нельзя пользоваться ИИ-ассистентами, чужими решениями и помощью других людей.</li>
      </ul>
      <h2>Что записывается</h2>
      <p>
        Пока открыта страница задачи, мы записываем, как вы работаете с кодом: изменения текста (в том числе набранный
        и вставленный код — чтобы работодатель мог воспроизвести ход решения), вставки и копирование (только длину),
        класс нажатых клавиш без самих символов, положение курсора, уход со вкладки и возврат, запуски тестов.
        По этим данным считаются индикаторы достоверности. Видео, звук и экран не записываются.
      </p>
      @if (error(); as message) {
        <p class="error">{{ message }}</p>
      }
      <button class="btn btn--primary" type="button" [disabled]="starting()" (click)="start()">
        Начать оценку
      </button>
    </section>
  `,
  styles: `
    .rules { padding-left: 20px; }
    .rules li { margin: 6px 0; }
  `,
})
export class IntroPage {
  private readonly api = inject(CandidateApi);
  private readonly router = inject(Router);
  protected readonly starting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async start(): Promise<void> {
    this.starting.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(this.api.startSession());
      await this.router.navigateByUrl('/c/session');
    } catch (error) {
      this.error.set(messageOf(error));
    } finally {
      this.starting.set(false);
    }
  }
}
