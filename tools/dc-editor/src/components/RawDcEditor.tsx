import { useEffect, useMemo, useRef } from "react";
import { HighlightStyle, StreamLanguage, StringStream, syntaxHighlighting } from "@codemirror/language";
import { EditorState } from "@codemirror/state";
import { EditorView, lineNumbers } from "@codemirror/view";
import { tags } from "@lezer/highlight";

interface RawDcEditorProps {
  value: string;
  onChange(value: string): void;
}

interface DcStreamState {
  inTripleString: boolean;
}

const dcLanguage = StreamLanguage.define<DcStreamState>({
  startState() {
    return { inTripleString: false };
  },
  token(stream: StringStream, state: DcStreamState) {
    if (state.inTripleString) {
      while (!stream.eol()) {
        if (stream.match(`"""`)) {
          state.inTripleString = false;
          break;
        }
        stream.next();
      }
      return "string";
    }

    if (stream.sol()) {
      stream.eatSpace();
      if (stream.match("#") || stream.match("//")) {
        stream.skipToEnd();
        return "comment";
      }
      if (stream.peek() === "[") {
        stream.next();
        while (!stream.eol()) {
          const ch = stream.next();
          if (ch === "]") {
            break;
          }
        }
        return "keyword";
      }
      stream.backUp(stream.pos);
    }

    if (stream.eatSpace()) {
      return null;
    }

    if (stream.match("#") || stream.match("//")) {
      stream.skipToEnd();
      return "comment";
    }

    if (stream.match(`"""`)) {
      while (!stream.eol()) {
        if (stream.match(`"""`)) {
          return "string";
        }
        stream.next();
      }
      state.inTripleString = true;
      return "string";
    }

    if (stream.peek() === "\"") {
      stream.next();
      let escaped = false;
      while (!stream.eol()) {
        const ch = stream.next();
        if (escaped) {
          escaped = false;
          continue;
        }
        if (ch === "\\") {
          escaped = true;
          continue;
        }
        if (ch === "\"") {
          break;
        }
      }
      return "string";
    }

    if (stream.match(/^(true|false)\b/)) {
      return "bool";
    }

    if (stream.match(/^-?\d+(?:\.\d+)?\b/)) {
      return "number";
    }

    if (stream.match(/^[A-Za-z_][A-Za-z0-9_.-]*(?=\s*=)/)) {
      return "propertyName";
    }

    const punct = stream.peek();
    if (punct && "[]{}(),".includes(punct)) {
      stream.next();
      return "punctuation";
    }

    if (punct === "=") {
      stream.next();
      return "operator";
    }

    stream.next();
    return null;
  }
});

const dcHighlightStyle = HighlightStyle.define([
  { tag: tags.keyword, color: "#0033b3" },
  { tag: tags.propertyName, color: "#871094" },
  { tag: tags.string, color: "#067d17" },
  { tag: tags.number, color: "#1750eb" },
  { tag: tags.bool, color: "#0033b3" },
  { tag: tags.comment, color: "#8c8c8c" },
  { tag: tags.operator, color: "#1d2a32" },
  { tag: [tags.bracket, tags.squareBracket, tags.punctuation], color: "#0033b3" }
]);

const dcTheme = EditorView.theme({
  "&": {
    minHeight: "62vh",
    fontFamily: '"SF Mono", "JetBrains Mono", monospace',
    fontSize: "13px",
    backgroundColor: "#fcfbf8",
    color: "#1d2a32"
  },
  ".cm-scroller": {
    overflow: "auto",
    fontFamily: "inherit",
    lineHeight: "1.55"
  },
  ".cm-content, .cm-gutterElement": {
    fontFamily: "inherit",
    fontSize: "inherit",
    lineHeight: "1.55",
    tabSize: "4",
    fontVariantLigatures: "none",
    fontKerning: "none",
    whiteSpace: "pre"
  },
  ".cm-content": {
    caretColor: "#1d2a32"
  },
  ".cm-cursor, .cm-dropCursor": {
    borderLeftColor: "#1d2a32"
  },
  ".cm-selectionBackground, &.cm-focused .cm-selectionBackground, .cm-content ::selection": {
    backgroundColor: "rgba(88, 116, 212, 0.22)"
  },
  ".cm-gutters": {
    backgroundColor: "#f6f1e8",
    color: "#8c8c8c",
    borderRight: "1px solid #d9cfbf"
  },
  ".cm-activeLineGutter": {
    backgroundColor: "#efe5d4"
  },
  ".cm-activeLine": {
    backgroundColor: "rgba(13, 107, 95, 0.06)"
  },
  ".cm-focused": {
    outline: "none"
  }
});

const dcExtensions = [
  lineNumbers(),
  dcLanguage,
  syntaxHighlighting(dcHighlightStyle),
  dcTheme
];

export function RawDcEditor(props: RawDcEditorProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const viewRef = useRef<EditorView | null>(null);
  const onChangeRef = useRef(props.onChange);

  onChangeRef.current = props.onChange;

  const initialState = useMemo(() => EditorState.create({
    doc: props.value,
    extensions: [
      ...dcExtensions,
      EditorView.updateListener.of((update) => {
        if (update.docChanged) {
          onChangeRef.current(update.state.doc.toString());
        }
      })
    ]
  }), []);

  useEffect(() => {
    if (!containerRef.current || viewRef.current) {
      return;
    }
    viewRef.current = new EditorView({
      state: initialState,
      parent: containerRef.current
    });
    return () => {
      viewRef.current?.destroy();
      viewRef.current = null;
    };
  }, [initialState]);

  useEffect(() => {
    const view = viewRef.current;
    if (!view) {
      return;
    }
    const current = view.state.doc.toString();
    if (current === props.value) {
      return;
    }
    view.dispatch({
      changes: { from: 0, to: current.length, insert: props.value }
    });
  }, [props.value]);

  return <div className="raw-editor-shell" ref={containerRef} />;
}
