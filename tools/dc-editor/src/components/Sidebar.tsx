import { useEffect, useRef, useState } from "react";
import { DocumentSchema, DocumentType } from "../models/editor";
import { EditorLocale, t, translateStaticText } from "../i18n/editorI18n";

interface SidebarProps {
  currentType: DocumentType;
  schemas: Record<DocumentType, DocumentSchema>;
  orderedTypes: DocumentType[];
  showDocumentActions: boolean;
  locale: EditorLocale;
  onTypeSelect(type: DocumentType): void;
  onChangeLocale(locale: EditorLocale): void;
  onNew(): void;
  onImport(): void;
}

export function Sidebar(props: SidebarProps) {
  const [localeMenuOpen, setLocaleMenuOpen] = useState(false);
  const localeMenuRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const onPointerDown = (event: MouseEvent) => {
      if (localeMenuRef.current && !localeMenuRef.current.contains(event.target as Node)) {
        setLocaleMenuOpen(false);
      }
    };
    window.addEventListener("mousedown", onPointerDown);
    return () => window.removeEventListener("mousedown", onPointerDown);
  }, []);

  return (
    <aside className="sidebar">
      <div className="sidebar-topbar">
        <div className="toolbar-locale-menu sidebar-locale-menu" ref={localeMenuRef}>
          <button
            type="button"
            className="toolbar-locale-trigger sidebar-locale-trigger"
            aria-label={props.locale === "zh_cn" ? "切换界面语言" : "Switch UI language"}
            title={props.locale === "zh_cn" ? "切换界面语言" : "Switch UI language"}
            aria-haspopup="menu"
            aria-expanded={localeMenuOpen}
            onClick={() => setLocaleMenuOpen((previous) => !previous)}
          >
            <span className="toolbar-locale-icon">🌐</span>
            <span className="toolbar-locale-caret">▾</span>
          </button>
          {localeMenuOpen && (
            <div className="toolbar-locale-dropdown sidebar-locale-dropdown" role="menu">
              <button
                type="button"
                className={props.locale === "zh_cn" ? "active" : ""}
                role="menuitemradio"
                aria-checked={props.locale === "zh_cn"}
                onClick={() => {
                  props.onChangeLocale("zh_cn");
                  setLocaleMenuOpen(false);
                }}
              >
                {t(props.locale, "languageZhCn")}
              </button>
              <button
                type="button"
                className={props.locale === "en_us" ? "active" : ""}
                role="menuitemradio"
                aria-checked={props.locale === "en_us"}
                onClick={() => {
                  props.onChangeLocale("en_us");
                  setLocaleMenuOpen(false);
                }}
              >
                {t(props.locale, "languageEnUs")}
              </button>
            </div>
          )}
        </div>
      </div>

      {props.showDocumentActions && (
        <div className="sidebar-block">
          <div className="sidebar-title">{t(props.locale, "action")}</div>
          <div className="sidebar-actions">
            <button onClick={props.onNew}>{t(props.locale, "newConfig")}</button>
            <button onClick={props.onImport}>{t(props.locale, "importDc")}</button>
          </div>
        </div>
      )}

      <div className="sidebar-block">
        <div className="sidebar-title">{t(props.locale, "types")}</div>
        <div className="type-list">
          {props.orderedTypes.map((type) => {
            const schema = props.schemas[type];
            const selected = props.currentType === type;
            return (
              <button
                key={type}
                className={`type-item${selected ? " selected" : ""}`}
                onClick={() => props.onTypeSelect(type)}
              >
                <span className="type-label">{schema.label}</span>
                <span className="type-summary">{translateStaticText(props.locale, schema.summary)}</span>
              </button>
            );
          })}
        </div>
      </div>
    </aside>
  );
}
