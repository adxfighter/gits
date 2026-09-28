import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { TelemetryDebug } from '../../../core/telemetry/telemetry-collector.service';

/** Telemetry state for developers, shown only with ?debug=1 in the address. */
@Component({
  selector: 'app-telemetry-debug',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside class="telemetry-debug" aria-label="Отладка телеметрии" data-testid="telemetry-debug">
      <strong>Телеметрия</strong>
      <div>в буфере: {{ state().buffered }} · отброшено: {{ state().dropped }} · ошибок подряд: {{ state().failures }}</div>
      <div>обработчик: {{ state().handlerMs.toFixed(3) }} мс в среднем</div>
      <div>последний seq: {{ seqs() }}</div>
      <div>{{ counts() }}</div>
    </aside>
  `,
  styles: `
    .telemetry-debug {
      position: fixed; right: 12px; bottom: 12px; z-index: 5; max-width: 340px;
      padding: 8px 10px; font: 12px/1.4 ui-monospace, Consolas, monospace;
      color: #e6e6e6; background: rgb(20 20 20 / 90%); border: 1px solid #444; border-radius: 6px;
    }
  `,
})
export class TelemetryDebugPanel {
  readonly state = input.required<TelemetryDebug>();

  protected readonly seqs = computed(() =>
    Object.entries(this.state().lastSeq)
      .map(([task, seq]) => `${task.slice(0, 8)}: ${seq}`)
      .join(', ') || '—',
  );

  protected readonly counts = computed(() =>
    Object.entries(this.state().counts)
      .map(([type, count]) => `${type} ${count}`)
      .join(' · ') || 'событий пока нет',
  );
}
