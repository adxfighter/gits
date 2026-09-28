import { describe, expect, it } from 'vitest';

import { classifyKey } from './key-class';

describe('classifyKey', () => {
  it.each([
    ['KeyA', 'a', 'letter'],
    ['KeyA', 'A', 'letter'],
    ['KeyF', 'а', 'letter'], // Cyrillic layout
    ['Digit7', '7', 'digit'],
    ['Numpad3', '3', 'digit'],
    ['Space', ' ', 'space'],
    ['Enter', 'Enter', 'enter'],
    ['NumpadEnter', 'Enter', 'enter'],
    ['Backspace', 'Backspace', 'backspace'],
    ['Delete', 'Delete', 'delete'],
    ['Tab', 'Tab', 'tab'],
    ['ArrowLeft', 'ArrowLeft', 'arrow'],
    ['Home', 'Home', 'arrow'],
    ['PageDown', 'PageDown', 'arrow'],
    ['Digit9', '(', 'bracket'],
    ['BracketRight', '}', 'bracket'],
    ['Comma', '<', 'bracket'],
    ['Semicolon', ';', 'punct'],
    ['Period', '.', 'punct'],
    ['Equal', '=', 'punct'],
    ['Quote', '"', 'punct'],
    ['ShiftLeft', 'Shift', 'modifier'],
    ['ControlRight', 'Control', 'modifier'],
    ['AltLeft', 'Alt', 'modifier'],
    ['MetaLeft', 'Meta', 'modifier'],
    ['CapsLock', 'CapsLock', 'modifier'],
    ['Escape', 'Escape', 'other'],
    ['F5', 'F5', 'other'],
    ['Quote', 'Dead', 'letter'],
  ])('%s / %s is %s', (code, key, expected) => {
    expect(classifyKey(code, key)).toBe(expected);
  });

  it('never returns the key itself', () => {
    for (const key of ['q', 'Q', '1', '%', 'ж']) {
      expect(classifyKey('KeyQ', key)).not.toBe(key);
    }
  });
});
