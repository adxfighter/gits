import type * as Monaco from 'monaco-editor';

/** Run by Monaco right after an item of this provider is inserted; the telemetry records the accepted completion. */
export const COMPLETION_ACCEPTED_COMMAND = 'gits.completionAccepted';

const KEYWORDS = [
  'abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch', 'char', 'class', 'continue', 'default',
  'do', 'double', 'else', 'enum', 'extends', 'final', 'finally', 'float', 'for', 'if', 'implements', 'import',
  'instanceof', 'int', 'interface', 'long', 'new', 'package', 'private', 'protected', 'public', 'record',
  'return', 'short', 'static', 'super', 'switch', 'synchronized', 'this', 'throw', 'throws', 'try', 'var',
  'void', 'volatile', 'while', 'yield', 'true', 'false', 'null',
];

const TYPES = [
  'String', 'Integer', 'Long', 'Double', 'Boolean', 'Character', 'Object', 'List', 'ArrayList', 'LinkedList',
  'Map', 'HashMap', 'TreeMap', 'LinkedHashMap', 'Set', 'HashSet', 'TreeSet', 'Deque', 'ArrayDeque', 'Queue',
  'Optional', 'Stream', 'Collectors', 'Arrays', 'Collections', 'Objects', 'StringBuilder', 'Math',
  'IllegalArgumentException', 'IllegalStateException', 'RuntimeException', 'Exception', 'Comparator',
  'Iterator', 'Iterable', 'Function', 'Supplier', 'Consumer', 'Predicate', 'BiFunction', 'Duration', 'Instant',
  'ConcurrentHashMap', 'ExecutorService', 'Executors', 'CompletableFuture', 'AtomicInteger', 'AtomicLong',
];

interface Snippet {
  label: string;
  detail: string;
  body: string;
}

const SNIPPETS: Snippet[] = [
  { label: 'sout', detail: 'System.out.println', body: 'System.out.println(${1});' },
  { label: 'psvm', detail: 'main method', body: 'public static void main(String[] args) {\n\t${0}\n}' },
  { label: 'fori', detail: 'for with index', body: 'for (int ${1:i} = 0; ${1:i} < ${2:n}; ${1:i}++) {\n\t${0}\n}' },
  { label: 'foreach', detail: 'for-each', body: 'for (${1:var} ${2:item} : ${3:items}) {\n\t${0}\n}' },
  { label: 'if', detail: 'if', body: 'if (${1:condition}) {\n\t${0}\n}' },
  { label: 'ifelse', detail: 'if / else', body: 'if (${1:condition}) {\n\t${2}\n} else {\n\t${0}\n}' },
  { label: 'while', detail: 'while', body: 'while (${1:condition}) {\n\t${0}\n}' },
  { label: 'try', detail: 'try / catch', body: 'try {\n\t${1}\n} catch (${2:Exception} ${3:e}) {\n\t${0}\n}' },
  { label: 'switch', detail: 'switch expression', body: 'switch (${1:value}) {\n\tcase ${2} -> ${0};\n\tdefault -> ;\n}' },
  { label: 'class', detail: 'class', body: 'public class ${1:Name} {\n\t${0}\n}' },
  { label: 'record', detail: 'record', body: 'public record ${1:Name}(${2}) {\n}' },
  { label: 'throwiae', detail: 'throw IllegalArgumentException', body: 'throw new IllegalArgumentException(${1});' },
];

/**
 * Basic Java completion: keywords, common JDK types and snippets. Monaco has no Java language service, so this
 * is word-level help only. Models of tasks in {@code noSuggestions} (the calibration block, which measures plain
 * typing) get no suggestions at all.
 */
export function registerJavaCompletions(monaco: typeof Monaco, noSuggestions: (model: Monaco.editor.ITextModel) => boolean): void {
  monaco.languages.registerCompletionItemProvider('java', {
    provideCompletionItems(model, position) {
      if (noSuggestions(model)) {
        return { suggestions: [] };
      }
      const word = model.getWordUntilPosition(position);
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      };
      const kinds = monaco.languages.CompletionItemKind;
      const command = { id: COMPLETION_ACCEPTED_COMMAND, title: '' };
      return {
        suggestions: [
          ...KEYWORDS.map((keyword) => ({
            label: keyword,
            kind: kinds.Keyword,
            insertText: keyword,
            range,
            command,
          })),
          ...TYPES.map((type) => ({ label: type, kind: kinds.Class, insertText: type, range, command })),
          ...SNIPPETS.map((snippet) => ({
            label: snippet.label,
            kind: kinds.Snippet,
            detail: snippet.detail,
            insertText: snippet.body,
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            range,
            command,
          })),
        ],
      };
    },
  });
}
