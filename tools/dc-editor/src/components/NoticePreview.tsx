import { useEffect, useMemo, useState } from "react";
import ReactMarkdown from "react-markdown";
import { StructuredModel } from "../models/editor";

interface NoticePreviewProps {
  model: StructuredModel | null;
}

function readLocalized(model: StructuredModel, section: string, lang: string): string {
  const value = model[section]?.[lang];
  return typeof value === "string" ? value : "";
}

function collectLanguages(model: StructuredModel): string[] {
  const ordered = ["zh_cn", "en_us"];
  const seen = new Set<string>();
  const result: string[] = [];
  for (const section of ["Title", "Subhead", "ContentUrl", "Contents"]) {
    for (const key of Object.keys(model[section] || {})) {
      if (!seen.has(key)) {
        seen.add(key);
        result.push(key);
      }
    }
  }
  result.sort((a, b) => {
    const ai = ordered.indexOf(a);
    const bi = ordered.indexOf(b);
    if (ai >= 0 && bi >= 0) {
      return ai - bi;
    }
    if (ai >= 0) {
      return -1;
    }
    if (bi >= 0) {
      return 1;
    }
    return a.localeCompare(b);
  });
  return result.length > 0 ? result : ["zh_cn", "en_us"];
}

function labelForLanguage(lang: string): string {
  if (lang === "zh_cn") {
    return "中文";
  }
  if (lang === "en_us") {
    return "English";
  }
  return lang;
}

export function NoticePreview(props: NoticePreviewProps) {
  const model = props.model || {};
  const languages = useMemo(() => collectLanguages(model), [model]);
  const [activeLang, setActiveLang] = useState<string>(languages[0] || "zh_cn");

  useEffect(() => {
    if (!languages.includes(activeLang)) {
      setActiveLang(languages[0] || "zh_cn");
    }
  }, [activeLang, languages]);

  const title = readLocalized(model, "Title", activeLang) || readLocalized(model, "Title", "en_us");
  const subhead = readLocalized(model, "Subhead", activeLang) || readLocalized(model, "Subhead", "en_us");
  const contentUrl = readLocalized(model, "ContentUrl", activeLang) || readLocalized(model, "ContentUrl", "en_us");
  const contents = readLocalized(model, "Contents", activeLang) || readLocalized(model, "Contents", "en_us");
  const enabled = model.Subhead?.enabled !== false;

  return (
    <section className="panel">
      <div className="panel-title">公告预览</div>
      <div className="panel-note">仅 `notice` 保留预览模式，其余类型统一使用原文模式。</div>
      <div className="preview-toolbar">
        {languages.map((lang) => (
          <button
            key={lang}
            className={lang === activeLang ? "active" : ""}
            onClick={() => setActiveLang(lang)}
          >
            {labelForLanguage(lang)}
          </button>
        ))}
      </div>
      <article className="notice-preview">
        {title && <h1>{title}</h1>}
        {enabled && subhead && <p className="notice-subhead">{subhead}</p>}
        {contentUrl && (
          <p className="notice-url">
            <strong>ContentUrl:</strong> {contentUrl}
          </p>
        )}
        <div className="notice-markdown">
          <ReactMarkdown>{contents || "暂无公告内容。"}</ReactMarkdown>
        </div>
      </article>
    </section>
  );
}
