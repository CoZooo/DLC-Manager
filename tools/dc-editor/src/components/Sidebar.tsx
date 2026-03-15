import { DocumentSchema, DocumentType } from "../models/editor";
import { EditorLocale, t, translateStaticText } from "../i18n/editorI18n";

interface SidebarProps {
  currentType: DocumentType;
  schemas: Record<DocumentType, DocumentSchema>;
  orderedTypes: DocumentType[];
  showDocumentActions: boolean;
  locale: EditorLocale;
  onTypeSelect(type: DocumentType): void;
  onNew(): void;
  onImport(): void;
}

export function Sidebar(props: SidebarProps) {
  return (
    <aside className="sidebar">
      {props.showDocumentActions && (
        <div className="sidebar-block">
          <div className="sidebar-title">{t(props.locale, "documents")}</div>
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
