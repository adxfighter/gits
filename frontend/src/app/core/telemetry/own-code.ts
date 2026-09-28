/** A paste of this many non-whitespace characters from outside the task is a suspicion of copying. */
export const EXTERNAL_PASTE_MIN = 10;

const SPACE = /[\s\u00A0\u200B\uFEFF]+/g;

/** Text without any whitespace: pasted code may differ from its source in indentation and line breaks only. */
export function withoutSpace(text: string): string {
  return text.replace(SPACE, '');
}

export interface PasteVerdict {
  /** Non-whitespace characters pasted. */
  meaningful: number;
  /** Found in one of the sources: the candidate's code, the task's other files or its statement. */
  own: boolean;
  /** Long enough and from elsewhere: a suspicion of copying. */
  suspicious: boolean;
}

/**
 * Where a pasted text comes from. It is searched as a substring, whitespace ignored, in the sources as they are at
 * the moment of the paste: a few kilobytes of the task's files and statement, a fraction of a millisecond per paste.
 * No index or cache is needed, and nothing runs on ordinary key presses.
 */
export function judgePaste(pasted: string, sources: readonly string[]): PasteVerdict {
  const needle = withoutSpace(pasted);
  if (needle.length === 0) {
    return { meaningful: 0, own: true, suspicious: false };
  }
  const own = sources.some((source) => withoutSpace(source).includes(needle));
  return { meaningful: needle.length, own, suspicious: !own && needle.length >= EXTERNAL_PASTE_MIN };
}

/** A text with the part [offset, offset + length) cut out: the file as it was before an insertion. */
export function withoutRange(text: string, offset: number, length: number): string {
  return text.slice(0, offset) + text.slice(offset + length);
}
