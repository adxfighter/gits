import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { TRUST_LABELS } from '../../core/employer/employer-labels';
import { TrustLevel } from '../../core/employer/employer.models';

/** Trust level as a coloured mark plus its text: the colour is never the only signal. */
@Component({
  selector: 'app-trust-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (level(); as current) {
      <span [class]="'trust trust--' + current.toLowerCase()" data-testid="trust" [attr.data-level]="current">
        <span class="trust__mark" aria-hidden="true"></span>{{ label() }}
      </span>
    } @else {
      <span class="muted" data-testid="trust">{{ empty() }}</span>
    }
  `,
})
export class TrustBadge {
  readonly level = input<TrustLevel | null>(null);
  /** What to show while there is no level yet. */
  readonly empty = input('—');

  protected readonly label = computed(() => {
    const level = this.level();
    return level ? TRUST_LABELS[level] : '';
  });
}
