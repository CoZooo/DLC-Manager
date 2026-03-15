import { EditorDocument } from "../models/editor";

interface ToolbarProps {
  document: EditorDocument | null;
  activeView: "form" | "raw" | "preview" | "validation";
  allowPreview: boolean;
  sidebarVisible: boolean;
  onGoHome(): void;
  onToggleSidebar(): void;
  onChangeView(view: "form" | "raw" | "preview" | "validation"): void;
  onSave(): void;
}

export function Toolbar(props: ToolbarProps) {
  const disabled = !props.document;
  const views = props.allowPreview
    ? (["form", "raw", "preview", "validation"] as const)
    : (["form", "raw", "validation"] as const);
  return (
    <header className="toolbar">
      <div className="toolbar-left">
        <button
          type="button"
          className="icon-button"
          aria-label={props.sidebarVisible ? "隐藏左栏" : "显示左栏"}
          title={props.sidebarVisible ? "隐藏左栏" : "显示左栏"}
          onClick={props.onToggleSidebar}
        >
          {props.sidebarVisible ? "⟨" : "⟩"}
        </button>
        <button type="button" className="brand-button" onClick={props.onGoHome}>
          <div className="brand">DLC配置生成器</div>
        </button>
        {props.document && (
          <div className="doc-meta">
            <span>{props.document.name}</span>
            <span className="doc-meta-type">{props.document.type}</span>
            {props.document.isDirty && <span className="dirty-dot">已修改</span>}
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
              form: "表单",
              raw: "原文",
              preview: "预览",
              validation: "校验"
            }[view]}
          </button>
        ))}
      </div>

      <div className="toolbar-right">
        <button disabled={disabled} onClick={props.onSave}>导出</button>
      </div>
    </header>
  );
}
