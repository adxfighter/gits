import { ChangeDetectionStrategy, Component, ElementRef, effect, input, output, viewChild } from '@angular/core';

/** A modal confirmation on the native dialog element (focus trap and Esc come with it). */
@Component({
  selector: 'app-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog class="dialog" (cancel)="cancelled.emit()" (close)="closed()">
      <h2 class="dialog__title">{{ title() }}</h2>
      <p class="dialog__text">{{ text() }}</p>
      <div class="dialog__actions">
        <button class="btn" type="button" (click)="cancelled.emit()">Отмена</button>
        <button class="btn btn--primary" type="button" (click)="confirmed.emit()" data-testid="confirm">
          {{ confirmLabel() }}
        </button>
      </div>
    </dialog>
  `,
})
export class ConfirmDialog {
  readonly open = input(false);
  readonly title = input('');
  readonly text = input('');
  readonly confirmLabel = input('Подтвердить');
  readonly confirmed = output<void>();
  readonly cancelled = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const element = this.dialog().nativeElement;
      if (this.open() && !element.open) {
        element.showModal?.();
      } else if (!this.open() && element.open) {
        element.close();
      }
    });
  }

  protected closed(): void {
    if (this.open()) {
      this.cancelled.emit();
    }
  }
}
