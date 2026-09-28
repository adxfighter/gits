import { EditSource } from './telemetry.models';

/** A paste event and its content change arrive within this time. */
const PASTE_WINDOW_MS = 150;

/**
 * Decides the source of an edit (docs/telemetry.md): "paste" when a paste event came just before it, "typing"
 * otherwise. An accepted completion is known only after its text is in the model (the completion item's command
 * runs after the insertion), so the collector re-marks those edits as "completion" afterwards.
 */
export class EditSourceTracker {
  private pasteAt = -Infinity;
  private cutAt = -Infinity;

  /** A paste event on the editor. */
  paste(now: number): void {
    this.pasteAt = now;
  }

  /** A cut on the editor: the deletion that follows is not typing. */
  cut(now: number): void {
    this.cutAt = now;
  }

  /** The source of a content change at {@code now}; a paste or cut marks only the change that follows it. */
  sourceOf(now: number): EditSource {
    if (now - this.pasteAt <= PASTE_WINDOW_MS) {
      this.pasteAt = -Infinity;
      return 'paste';
    }
    if (now - this.cutAt <= PASTE_WINDOW_MS) {
      this.cutAt = -Infinity;
      return 'other';
    }
    return 'typing';
  }
}
