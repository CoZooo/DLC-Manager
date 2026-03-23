import { useEffect, useRef, useState } from "react";
import { EditorDocument } from "../models/editor";
import { t } from "../i18n/editorI18n";

interface ToolbarProps {
  documents: EditorDocument[];
  document: EditorDocument | null;
  activeDocumentIndex: number;
  activeView: "form" | "raw" | "preview" | "validation";
  allowPreview: boolean;
  sidebarVisible: boolean;
  locale: "zh_cn" | "en_us";
  onGoHome(): void;
  onToggleSidebar(): void;
  onChangeView(view: "form" | "raw" | "preview" | "validation"): void;
  onSelectDocument(id: string): void;
  onSelectPreviousDocument(): void;
  onSelectNextDocument(): void;
  onRemoveDocument(id: string): void;
  onClearDocuments(): void;
  onSaveCurrent(): void;
  onSave(): void;
}

export function Toolbar(props: ToolbarProps) {
  const [documentMenuOpen, setDocumentMenuOpen] = useState(false);
  const documentMenuRef = useRef<HTMLDivElement | null>(null);
  const disabled = !props.document;
  const multiDocument = props.documents.length > 1;
  const views = props.allowPreview
    ? (["form", "raw", "preview", "validation"] as const)
    : (["form", "raw", "validation"] as const);

  useEffect(() => {
    const onPointerDown = (event: MouseEvent) => {
      if (documentMenuRef.current && !documentMenuRef.current.contains(event.target as Node)) {
        setDocumentMenuOpen(false);
      }
    };
    window.addEventListener("mousedown", onPointerDown);
    return () => window.removeEventListener("mousedown", onPointerDown);
  }, []);

  return (
    <header className="toolbar">
      <div className="toolbar-left">
        <button
          type="button"
          className="icon-button"
          aria-label={props.sidebarVisible ? t(props.locale, "toggleSidebarHide") : t(props.locale, "toggleSidebarShow")}
          title={props.sidebarVisible ? t(props.locale, "toggleSidebarHide") : t(props.locale, "toggleSidebarShow")}
          onClick={props.onToggleSidebar}
        >
          {props.sidebarVisible ? "⟨" : "⟩"}
        </button>
        <button type="button" className="brand-button" onClick={props.onGoHome} title={t(props.locale, "backHome")}>
          <div className="brand">{t(props.locale, "appTitle")}</div>
        </button>
        {props.document && (
          <div className="doc-meta" ref={documentMenuRef}>
            {multiDocument ? (
              <button
                type="button"
                className="doc-name-button"
                onClick={() => setDocumentMenuOpen((previous) => !previous)}
                title={t(props.locale, "switchDocuments")}
              >
                {props.document.name}
              </button>
            ) : (
              <span className="doc-name-static">{props.document.name}</span>
            )}
            <span className="doc-meta-type">{props.document.type}</span>
            {multiDocument && (
              <button type="button" className="doc-clear-button" onClick={props.onClearDocuments}>
                {t(props.locale, "clearDocuments")}
              </button>
            )}
            {props.document.isDirty && <span className="dirty-dot">{t(props.locale, "dirty")}</span>}
            {multiDocument && <span className="doc-meta-type">{props.documents.length}</span>}
            {multiDocument && documentMenuOpen && (
              <div className="toolbar-locale-dropdown doc-switch-dropdown" role="menu">
                {props.documents.map((item) => (
                  <div key={item.id} className="doc-switch-row">
                    <button
                      type="button"
                      className={props.document?.id === item.id ? "active doc-switch-name" : "doc-switch-name"}
                      onClick={() => {
                        props.onSelectDocument(item.id);
                        setDocumentMenuOpen(false);
                      }}
                    >
                      {item.name}
                    </button>
                    <button
                      type="button"
                      className="doc-switch-remove"
                      title={t(props.locale, "removeDocument")}
                      onClick={(event) => {
                        event.stopPropagation();
                        props.onRemoveDocument(item.id);
                      }}
                    >
                      {t(props.locale, "removeDocument")}
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      <div className="toolbar-center">
        {views.map((view) => (
          <button
            key={view}
            className={props.activeView === view ? "active" : ""}
            disabled={disabled}
            onClick={() => props.onChangeView(view)}
          >
            {{
              form: t(props.locale, "viewForm"),
              raw: t(props.locale, "viewRaw"),
              preview: t(props.locale, "viewPreview"),
              validation: t(props.locale, "viewValidation")
            }[view]}
          </button>
        ))}
      </div>

      <div className="toolbar-right">
        {props.document && multiDocument && (
          <div className="document-switch-inline">
            <button
              type="button"
              className="toolbar-locale-trigger"
              aria-label={t(props.locale, "switchDocuments")}
              title={t(props.locale, "switchDocuments")}
              onClick={props.onSelectPreviousDocument}
            >
              <span className="toolbar-locale-caret">←</span>
            </button>
            <span className="document-switch-index">{props.activeDocumentIndex + 1}/{props.documents.length}</span>
            <button
              type="button"
              className="toolbar-locale-trigger"
              aria-label={t(props.locale, "switchDocuments")}
              title={t(props.locale, "switchDocuments")}
              onClick={props.onSelectNextDocument}
            >
              <span className="toolbar-locale-caret">→</span>
            </button>
          </div>
        )}
        {multiDocument && <button disabled={disabled} onClick={props.onSaveCurrent}>{t(props.locale, "exportCurrent")}</button>}
        <button disabled={disabled} onClick={props.onSave}>{multiDocument ? t(props.locale, "exportAll") : t(props.locale, "export")}</button>
      </div>
    </header>
  );
}
