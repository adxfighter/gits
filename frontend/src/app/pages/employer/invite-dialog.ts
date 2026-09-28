import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, input, output, signal, viewChild } from '@angular/core';

import { EmployerApi } from '../../core/employer/employer-api.service';
import { LEVELS, LEVEL_LABELS, formatDateTime } from '../../core/employer/employer-labels';
import { CreatedInvite, Level } from '../../core/employer/employer.models';
import { messageOf } from '../../core/http/http-errors';

/** «Пригласить кандидата»: the candidate's label and level, then the one-time link with a copy button. */
@Component({
  selector: 'app-invite-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog class="dialog invite-dialog" (cancel)="close($event)" (close)="closedByBrowser()">
      @if (created(); as invite) {
        <h2 class="dialog__title">Приглашение создано</h2>
        <p>Отправьте кандидату ссылку. Она одноразовая и действует до {{ date(invite.expiresAt) }}.</p>
        <div class="invite-dialog__link">
          <input
            #link
            class="input"
            type="text"
            readonly
            [value]="invite.link"
            aria-label="Ссылка для кандидата"
            data-testid="invite-link"
            (focus)="link.select()"
          />
          <button class="btn btn--primary" type="button" (click)="copy(link)" data-testid="invite-copy">
            {{ copied() ? 'Скопировано' : 'Копировать' }}
          </button>
        </div>
        @if (copyFailed()) {
          <p class="invite-dialog__hint" role="status" data-testid="invite-copy-failed">
            Браузер не дал скопировать автоматически: ссылка выделена, нажмите Ctrl+C.
          </p>
        }
        <p class="muted invite-dialog__hint">Ссылку больше нигде не показываем: скопируйте её сейчас.</p>
        <div class="dialog__actions">
          <button class="btn" type="button" (click)="finish()" data-testid="invite-done">Готово</button>
        </div>
      } @else {
        <h2 class="dialog__title">Пригласить кандидата</h2>
        <form (submit)="submit($event)" novalidate>
          <label class="field">
            <span class="field__label">Кандидат</span>
            <input
              class="input"
              type="text"
              name="label"
              maxlength="200"
              placeholder="Например, Иван П., backend"
              [value]="label()"
              (input)="label.set(value($event))"
              data-testid="invite-label"
            />
            <span class="field__hint">Метку видите только вы: имя, должность или номер отклика.</span>
          </label>
          <label class="field">
            <span class="field__label">Уровень</span>
            <select class="input" name="level" [value]="level()" (change)="level.set($any(value($event)))" data-testid="invite-level">
              @for (option of levels; track option) {
                <option [value]="option" [selected]="option === level()">{{ levelLabels[option] }}</option>
              }
            </select>
          </label>
          @if (error(); as text) {
            <p class="error" role="alert" data-testid="invite-error">{{ text }}</p>
          }
          <div class="dialog__actions">
            <button class="btn" type="button" (click)="cancel()">Отмена</button>
            <button class="btn btn--primary" type="submit" [disabled]="busy()" data-testid="invite-create">
              {{ busy() ? 'Создаём…' : 'Создать ссылку' }}
            </button>
          </div>
        </form>
      }
    </dialog>
  `,
  styles: `
    .invite-dialog { width: 520px; max-width: calc(100vw - 32px); }
    form { display: flex; flex-direction: column; gap: 14px; }
    .invite-dialog__link { display: flex; gap: 8px; }
    .invite-dialog__link .input { flex: 1; font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace; font-size: 13px; }
    .invite-dialog__hint { font-size: 13px; margin: 8px 0 0; }
  `,
})
export class InviteDialog {
  readonly open = input(false);
  /** The dialog was closed; `created` tells whether an invite was made (the list should be reloaded). */
  readonly closed = output<{ created: boolean }>();

  private readonly api = inject(EmployerApi);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly levels = LEVELS;
  protected readonly levelLabels = LEVEL_LABELS;
  protected readonly label = signal('');
  protected readonly level = signal<Level>('MIDDLE');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly created = signal<CreatedInvite | null>(null);
  protected readonly copied = signal(false);
  protected readonly copyFailed = signal(false);

  constructor() {
    effect(() => {
      const element = this.dialog().nativeElement;
      if (this.open() && !element.open) {
        this.reset();
        element.showModal?.();
      } else if (!this.open() && element.open) {
        element.close();
      }
    });
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  protected date(iso: string): string {
    return formatDateTime(iso);
  }

  protected submit(event: Event): void {
    event.preventDefault();
    const label = this.label().trim();
    if (!label) {
      this.error.set('Укажите метку кандидата.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.createInvite(label, this.level()).subscribe({
      next: (invite) => {
        this.busy.set(false);
        this.created.set(invite);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(messageOf(error, 'Не удалось создать приглашение. Попробуйте ещё раз.'));
      },
    });
  }

  protected copy(input: HTMLInputElement): void {
    input.select();
    // the clipboard API needs a secure context (localhost is one); otherwise the old command may still work
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(input.value).then(
        () => this.copyDone(true),
        () => this.copyByCommand(input),
      );
    } else {
      this.copyByCommand(input);
    }
  }

  protected cancel(): void {
    this.closed.emit({ created: false });
  }

  protected finish(): void {
    this.closed.emit({ created: true });
  }

  /** Esc: after the link was shown it counts as «Готово», so the new invite appears in the list. */
  protected close(event: Event): void {
    event.preventDefault();
    this.closed.emit({ created: this.created() !== null });
  }

  protected closedByBrowser(): void {
    if (this.open()) {
      this.closed.emit({ created: this.created() !== null });
    }
  }

  private copyByCommand(input: HTMLInputElement): void {
    input.select();
    let copied: boolean;
    try {
      copied = document.execCommand?.('copy') ?? false;
    } catch {
      copied = false;
    }
    this.copyDone(copied);
  }

  /** Without a copy the link stays selected, and the employer is told to press Ctrl+C. */
  private copyDone(copied: boolean): void {
    this.copied.set(copied);
    this.copyFailed.set(!copied);
  }

  private reset(): void {
    this.label.set('');
    this.level.set('MIDDLE');
    this.busy.set(false);
    this.error.set(null);
    this.created.set(null);
    this.copied.set(false);
    this.copyFailed.set(false);
  }
}
