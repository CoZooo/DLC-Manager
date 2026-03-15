import { useEffect, useRef, useState } from "react";
import { EditorDocument } from "../models/editor";
import { EditorLocale, t } from "../i18n/editorI18n";

interface ToolbarProps {
  document: EditorDocument | null;
  activeView: "form" | "raw" | "preview" | "validation";
  allowPreview: boolean;
  sidebarVisible: boolean;
  locale: EditorLocale;
  onGoHome(): void;
  onToggleSidebar(): void;
  onChangeLocale(locale: EditorLocale): void;
  onChangeView(view: "form" | "raw" | "preview" | "validation"): void;
  onSave(): void;
}

export function Toolbar(props: ToolbarProps) {
  const [localeMenuOpen, setLocaleMenuOpen] = useState(false);
  const localeMenuRef = useRef<HTMLDivElement | null>(null);
  const disabled = !props.document;
  const views = props.allowPreview
    ? (["form", "raw", "preview", "validation"] as const)
    : (["form", "raw", "validation"] as const);

  useEffect(() => {
    const onPointerDown = (event: MouseEvent) => {
      if (!localeMenuRef.current) {
        return;
      }
      if (!localeMenuRef.current.contains(event.target as Node)) {
        setLocaleMenuOpen(false);
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
          <div className="doc-meta">
            <span>{props.document.name}</span>
            <span className="doc-meta-type">{props.document.type}</span>
            {props.document.isDirty && <span className="dirty-dot">{t(props.locale, "dirty")}</span>}
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
        <div className="toolbar-locale-menu" ref={localeMenuRef}>
          <button
            type="button"
            className="toolbar-locale-trigger"
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
            <div className="toolbar-locale-dropdown" role="menu">
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
        <button disabled={disabled} onClick={props.onSave}>{t(props.locale, "export")}</button>
      </div>
    </header>
  );
}
