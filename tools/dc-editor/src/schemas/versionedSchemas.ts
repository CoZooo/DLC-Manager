import { DocumentSchema, DocumentType, EditorTargetVersion } from "../models/editor";
import {
  configurableDocumentTypes,
  documentSchemas,
  orderedDocumentTypes
} from "./documentSchemas";
import {
  configurableDocumentTypes40,
  documentSchemas40,
  orderedDocumentTypes40
} from "./documentSchemas40";

export const EDITOR_TARGET_VERSIONS: EditorTargetVersion[] = ["4.0", "5.0"];
export const ALL_DOCUMENT_TYPES: DocumentType[] = Array.from(new Set([
  ...orderedDocumentTypes,
  ...orderedDocumentTypes40
]));

const schemasByVersion: Record<EditorTargetVersion, Record<DocumentType, DocumentSchema>> = {
  "4.0": documentSchemas40,
  "5.0": documentSchemas
};

const orderedTypesByVersion: Record<EditorTargetVersion, DocumentType[]> = {
  "4.0": orderedDocumentTypes40,
  "5.0": orderedDocumentTypes
};

const configurableTypesByVersion: Record<EditorTargetVersion, DocumentType[]> = {
  "4.0": configurableDocumentTypes40,
  "5.0": configurableDocumentTypes
};

export function getDocumentSchemas(version: EditorTargetVersion): Record<DocumentType, DocumentSchema> {
  return schemasByVersion[version];
}

export function getOrderedDocumentTypes(version: EditorTargetVersion): DocumentType[] {
  return orderedTypesByVersion[version];
}

export function getConfigurableDocumentTypes(version: EditorTargetVersion): DocumentType[] {
  return configurableTypesByVersion[version];
}

export function isEditorTargetVersion(value: unknown): value is EditorTargetVersion {
  return value === "4.0" || value === "5.0";
}
