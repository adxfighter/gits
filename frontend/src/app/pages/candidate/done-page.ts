import { ChangeDetectionStrategy, Component } from '@angular/core';

/** /c/done — the end of the assessment. Makes no API calls: the candidate's access has ended. */
@Component({
  selector: 'app-done-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      <h1>Спасибо!</h1>
      <p>Оценка завершена, решения отправлены работодателю.</p>
      <p class="muted">Эту вкладку можно закрыть. Результаты вам сообщит работодатель.</p>
    </section>
  `,
})
export class DonePage {}

/** /c/closed — no valid candidate access: the link was never opened here, was revoked or the assessment is over. */
@Component({
  selector: 'app-closed-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      <h1>Оценка недоступна</h1>
      <p>
        Если вы уже завершили оценку — спасибо, решения отправлены. Иначе откройте ссылку из приглашения ещё раз.
      </p>
      <p class="muted">Если ссылка не открывается, свяжитесь с работодателем, который её прислал.</p>
    </section>
  `,
})
export class ClosedPage {}
