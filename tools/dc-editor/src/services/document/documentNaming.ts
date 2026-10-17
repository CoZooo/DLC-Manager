import { EditorDocument, StructuredModel } from "../../models/editor";
import { documentSchemas } from "../../schemas/documentSchemas";

function readIdentifier(model: StructuredModel | null): string {
  const identifier = model?.basic?.identifier;
  return typeof identifier === "string" ? identifier.trim() : "";
}

export function effectiveDocumentName(document: Pick<EditorDocument, "type" | "name">): string {
  if (document.type === "folder_metadata") return "_metadata.dc";
  if (document.type === "pack") return "pack.dc";
  if (document.type === "config") return "config.dc";
  return document.name;
}

export function documentSavePath(path: string | undefined, name: string): string | undefined {
  if (!path) return undefined;
  const separator = Math.max(path.lastIndexOf("/"), path.lastIndexOf("\\"));
  return path.slice(0, separator + 1) + name;
}

export function deriveDocumentNaming(
  document: Pick<EditorDocument, "type" | "name" | "structuredModel" | "lastIdentifier">,
  model: StructuredModel | null
): Pick<EditorDocument, "name" | "lastIdentifier"> {
  const name = effectiveDocumentName(document);
  const schema = documentSchemas[document.type];
  const hasIdentifier = schema.sections.some((section) =>
    section.key === "basic" && section.fields.some((field) => field.key === "identifier")
  );
  if (document.type === "pack" || !hasIdentifier) return { name };
  const previousIdentifier = document.lastIdentifier ?? readIdentifier(document.structuredModel);
  const identifier = readIdentifier(model);
  const followsIdentifier = name === schema.defaultFileName
    || (Boolean(previousIdentifier) && name === `${previousIdentifier}.dc`);
  return {
    name: identifier && identifier !== previousIdentifier && followsIdentifier ? `${identifier}.dc` : name,
    lastIdentifier: identifier || previousIdentifier
  };
}
