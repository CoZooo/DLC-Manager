import { DocumentSchema, DocumentType } from "../models/editor";

interface SidebarProps {
  currentType: DocumentType;
  schemas: Record<DocumentType, DocumentSchema>;
  orderedTypes: DocumentType[];
  showDocumentActions: boolean;
  onTypeSelect(type: DocumentType): void;
  onNew(): void;
  onImport(): void;
}

export function Sidebar(props: SidebarProps) {
  return (
    <aside className="sidebar">
      {props.showDocumentActions && (
        <div className="sidebar-block">
          <div className="sidebar-title">文档</div>
          <div className="sidebar-actions">
            <button onClick={props.onNew}>新建配置</button>
            <button onClick={props.onImport}>导入 .dc</button>
          </div>
        </div>
      )}

      <div className="sidebar-block">
        <div className="sidebar-title">类型</div>
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
                <span className="type-summary">{schema.summary}</span>
              </button>
            );
          })}
        </div>
      </div>
    </aside>
  );
}
