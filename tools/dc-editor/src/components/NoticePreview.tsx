import { useEffect, useMemo, useState } from "react";
import ReactMarkdown from "react-markdown";
import { StructuredModel } from "../models/editor";
import { EditorLocale, t } from "../i18n/editorI18n";
import { EditorBackend } from "../services/backend/EditorBackend";

interface NoticePreviewProps {
  locale: EditorLocale;
  model: StructuredModel | null;
  backend: EditorBackend;
}

function isDraftLocaleKey(key: string): boolean {
  return key.startsWith("__draft_locale__:");
}

function readLocalized(model: StructuredModel, section: string, lang: string): string {
  const value = model[section]?.[lang];
  return typeof value === "string" ? value : "";
}

function readDraftLocalized(model: StructuredModel, section: string): string {
  const sectionValue = model[section] || {};
  for (const [key, value] of Object.entries(sectionValue)) {
    if (isDraftLocaleKey(key) && typeof value === "string" && value.trim()) {
      return value;
    }
  }
  return "";
}

function readLocalizedWithFallback(model: StructuredModel, section: string, lang: string): string {
  return (
    readLocalized(model, section, lang)
    || readLocalized(model, section, "en_us")
    || readDraftLocalized(model, section)
  );
}

function collectLanguages(model: StructuredModel): string[] {
  const ordered = ["zh_cn", "en_us"];
  const seen = new Set<string>();
  const result: string[] = [];
  const reservedKeys = new Set(["enabled"]);
  for (const section of ["Title", "Subhead", "ContentUrl", "Contents"]) {
    for (const key of Object.keys(model[section] || {})) {
      if (reservedKeys.has(key) || isDraftLocaleKey(key)) {
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

const noticeContentCache = new Map<string, string | null>();

export function NoticePreview(props: NoticePreviewProps) {
  const model = props.model || {};
  const languages = useMemo(() => collectLanguages(model), [model]);
  const [activeLang, setActiveLang] = useState<string>(languages[0] || "zh_cn");
  const [resolvedContents, setResolvedContents] = useState("");
  const [loadingRemoteContents, setLoadingRemoteContents] = useState(false);

  useEffect(() => {
    if (!languages.includes(activeLang)) {
      setActiveLang(languages[0] || "zh_cn");
    }
  }, [activeLang, languages]);

  const title = readLocalizedWithFallback(model, "Title", activeLang);
  const subhead = readLocalizedWithFallback(model, "Subhead", activeLang);
  const contentUrl = readLocalizedWithFallback(model, "ContentUrl", activeLang);
  const contents = readLocalizedWithFallback(model, "Contents", activeLang);
  const enabled = model.Subhead?.enabled !== false;

  useEffect(() => {
    const fallback = contents || "";
    const normalizedUrl = contentUrl.trim();
    if (!normalizedUrl) {
      setLoadingRemoteContents(false);
      setResolvedContents(fallback);
      return;
    }

    const cached = noticeContentCache.get(normalizedUrl);
    if (cached !== undefined) {
      setLoadingRemoteContents(false);
      setResolvedContents(cached && cached.trim() ? cached : fallback);
      return;
    }

    let cancelled = false;
    setLoadingRemoteContents(true);
    setResolvedContents(t(props.locale, "noticeLoading"));

    props.backend.fetchNoticeContent(normalizedUrl)
      .then((text) => {
        if (cancelled) {
          return;
        }
        noticeContentCache.set(normalizedUrl, text);
        setResolvedContents(text && text.trim() ? text : fallback);
      })
      .catch(() => {
        if (cancelled) {
          return;
        }
        noticeContentCache.set(normalizedUrl, null);
        setResolvedContents(fallback);
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingRemoteContents(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [contentUrl, contents, activeLang, props.locale, props.backend]);

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
        <div className="notice-markdown">
          <ReactMarkdown>
            {loadingRemoteContents ? resolvedContents : resolvedContents || t(props.locale, "noNoticeContent")}
          </ReactMarkdown>
        </div>
      </article>
    </section>
  );
}
