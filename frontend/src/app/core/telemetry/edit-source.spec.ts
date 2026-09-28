import { describe, expect, it } from 'vitest';

import { EditSourceTracker } from './edit-source';

describe('EditSourceTracker', () => {
  it('treats a change without a paste before it as typing', () => {
    expect(new EditSourceTracker().sourceOf(1000)).toBe('typing');
  });

  it('marks the change right after a paste event as paste', () => {
    const tracker = new EditSourceTracker();
    tracker.paste(1000);
    expect(tracker.sourceOf(1002)).toBe('paste');
  });

  it('marks only the first change after a paste', () => {
    const tracker = new EditSourceTracker();
    tracker.paste(1000);
    expect(tracker.sourceOf(1001)).toBe('paste');
    expect(tracker.sourceOf(1050)).toBe('typing');
  });

  it('does not attribute a late change to an old paste', () => {
    const tracker = new EditSourceTracker();
    tracker.paste(1000);
    expect(tracker.sourceOf(1500)).toBe('typing');
  });
});
