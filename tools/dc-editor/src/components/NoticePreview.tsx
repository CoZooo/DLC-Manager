import { useEffect, useMemo, useState } from "react";
import ReactMarkdown from "react-markdown";
import { StructuredModel } from "../models/editor";
import { EditorLocale, t } from "../i18n/editorI18n";

interface NoticePreviewProps {
  locale: EditorLocale;
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
  const reservedKeys = new Set(["enabled"]);
  for (const section of ["Title", "Subhead", "ContentUrl", "Contents"]) {
    for (const key of Object.keys(model[section] || {})) {
      if (reservedKeys.has(key)) {
        continue;
      }
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

function labelForLanguage(locale: EditorLocale, lang: string): string {
  if (lang === "zh_cn") {
    return locale === "zh_cn" ? "中文" : "Chinese";
  }
  if (lang === "en_us") {
    return locale === "zh_cn" ? "英语" : "English";
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
      <div className="panel-title">{t(props.locale, "previewTitle")}</div>
      <div className="preview-toolbar">
        {languages.map((lang) => (
          <button
            key={lang}
            className={lang === activeLang ? "active" : ""}
            onClick={() => setActiveLang(lang)}
          >
            {labelForLanguage(props.locale, lang)}
          </button>
        ))}
      </div>
      <article className="notice-preview">
        {title && <h1>{title}</h1>}
        {enabled && subhead && <p className="notice-subhead">{subhead}</p>}
        {contentUrl && (
          <p className="notice-url">
            <strong>{t(props.locale, "contentUrl")}:</strong> {contentUrl}
          </p>
        )}
        <div className="notice-markdown">
          <ReactMarkdown>{contents || t(props.locale, "noNoticeContent")}</ReactMarkdown>
        </div>
      </article>
    </section>
  );
}
