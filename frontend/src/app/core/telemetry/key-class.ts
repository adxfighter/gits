import { KeyClass } from './telemetry.models';

const MODIFIER_KEYS = new Set(['Shift', 'Control', 'Alt', 'AltGraph', 'Meta', 'OS', 'CapsLock', 'Fn', 'NumLock']);
const NAVIGATION_KEYS = new Set(['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Home', 'End', 'PageUp', 'PageDown']);
const BRACKETS = new Set(['(', ')', '[', ']', '{', '}', '<', '>']);
const LETTER = /^\p{L}$/u;
const DIGIT = /^\p{Nd}$/u;

/**
 * The class of a pressed key from KeyboardEvent.code and .key. Only the class is ever recorded, never the key or
 * the character (docs/telemetry.md). Navigation keys (arrows, Home, End, PageUp, PageDown) count as "arrow".
 */
export function classifyKey(code: string, key: string): KeyClass {
  if (MODIFIER_KEYS.has(key) || /^(Shift|Control|Alt|Meta|OS)(Left|Right)$/.test(code) || code === 'CapsLock') {
    return 'modifier';
  }
  if (key === 'Enter' || code === 'Enter' || code === 'NumpadEnter') {
    return 'enter';
  }
  if (key === 'Backspace') {
    return 'backspace';
  }
  if (key === 'Delete') {
    return 'delete';
  }
  if (key === 'Tab') {
    return 'tab';
  }
  if (key === ' ' || code === 'Space') {
    return 'space';
  }
  if (NAVIGATION_KEYS.has(key) || NAVIGATION_KEYS.has(code)) {
    return 'arrow';
  }
  if ([...key].length === 1) {
    if (BRACKETS.has(key)) {
      return 'bracket';
    }
    if (LETTER.test(key)) {
      return 'letter';
    }
    if (DIGIT.test(key)) {
      return 'digit';
    }
    return 'punct';
  }
  // dead keys and composition (e.g. an accent before a letter) still type a letter
  if (key === 'Dead' || key === 'Process') {
    return 'letter';
  }
  return 'other';
}
