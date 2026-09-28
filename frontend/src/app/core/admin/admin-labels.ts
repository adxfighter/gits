import { FileKind, VariantStatus } from './admin.models';

export const VARIANT_STATUS_LABELS: Record<VariantStatus, string> = {
  VALIDATED: 'Проверен',
  DISABLED: 'Отключён',
};

export const FILE_KIND_LABELS: Record<FileKind, string> = {
  STARTER: 'заготовка',
  READONLY: 'только чтение',
  VISIBLE_TEST: 'видимый тест',
  SOLUTION: 'решение',
  HIDDEN_TEST: 'скрытый тест',
};

/** Files the candidate never sees. */
export const SECRET_KINDS: readonly FileKind[] = ['SOLUTION', 'HIDDEN_TEST'];

/** Checks of the task bank validator (P05). */
export const CHECK_LABELS: Readonly<Record<string, string>> = {
  schema: 'Схема файлов',
  files: 'Состав файлов',
  forbidden: 'Запрещённые конструкции',
  sizes: 'Размеры',
  no_leak: 'Нет утечки решения',
  starter_compiles: 'Заготовка компилируется',
  reference_passes: 'Решение проходит тесты',
  starter_fails: 'Заготовка не проходит скрытые тесты',
  reference_time: 'Время решения',
  content_hash: 'Хеш содержимого',
};

export function checkLabel(id: string): string {
  return CHECK_LABELS[id] ?? id;
}
