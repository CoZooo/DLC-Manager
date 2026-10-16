export interface DcFieldDefinition {
  name: string;
  type: string;
  required?: boolean;
  description?: string;
  enumValues?: string[];
  constraints?: Record<string, unknown>;
  defaultValue?: unknown;
}

export interface DcDefinition {
  formatVersion?: number;
  profiles: Array<{
    id: string;
    description?: string;
    filePatterns?: string[];
    categories: Array<{
      path: string[];
      required?: boolean;
      description?: string;
      fields: DcFieldDefinition[];
    }>;
  }>;
}

export class DcField {
  readonly name: string;
  readonly type: string;
  readonly description: string;
  readonly enumValues: readonly string[];
  readonly defaultValue: unknown;
  readonly constraints: Readonly<Record<string, unknown>>;

  constructor(definition: DcFieldDefinition) {
    if (!definition.name.trim()) throw new Error("Field name is blank");
    this.name = definition.name;
    this.type = definition.type;
    this.description = definition.description ?? "";
    this.enumValues = Object.freeze([...(definition.enumValues ?? [])]);
    this.defaultValue = definition.defaultValue;
    this.constraints = Object.freeze({ ...definition.constraints });
  }
}

export class DcCategory {
  private readonly rules = new Map<string, { field: DcField; required: boolean }>();

  constructor(readonly path: readonly string[], readonly required: boolean) {}

  get fields(): readonly DcField[] {
    return [...this.rules.values()].map((rule) => rule.field);
  }

  addField(field: DcField, required: boolean): DcField {
    const key = field.name.toLowerCase();
    if (this.rules.has(key)) throw new Error(`Field already registered: ${field.name}`);
    this.rules.set(key, { field, required });
    return field;
  }
}

export class DcLanguage {
  private readonly registered = new Map<string, DcCategory>();
  readonly rootCategory = new DcCategory([], true);

  constructor() {
    this.registered.set(JSON.stringify([]), this.rootCategory);
  }

  category(path: readonly string[]): DcCategory {
    const category = this.registered.get(JSON.stringify(path.map((part) => part.toLowerCase())));
    if (!category) throw new Error(`Category is not registered: ${path.join(".")}`);
    return category;
  }

  registerMainCategory(name: string, required: boolean): DcCategory {
    return this.registerCategory(this.rootCategory, name, required);
  }

  registerCategory(parent: DcCategory, name: string, required: boolean): DcCategory {
    if (this.category(parent.path) !== parent) throw new Error("Category does not belong to this schema");
    if (!name.trim()) throw new Error("Category name is blank");
    const path = [...parent.path, name];
    const key = JSON.stringify(path.map((part) => part.toLowerCase()));
    if (this.registered.has(key) || parent.fields.some((field) => field.name.toLowerCase() === name.toLowerCase())) {
      throw new Error(`Category already registered: ${name}`);
    }
    const category = new DcCategory(path, required);
    this.registered.set(key, category);
    return category;
  }

  addField(category: DcCategory, field: DcField, required: boolean): DcField {
    if (this.category(category.path) !== category) throw new Error("Category does not belong to this schema");
    if (this.registered.has(JSON.stringify([...category.path, field.name].map((part) => part.toLowerCase())))) {
      throw new Error(`Field already registered: ${field.name}`);
    }
    return category.addField(field, required);
  }
}

export const DcLanguages = { create: () => new DcLanguage() };

export function registerDefinition(definition: DcDefinition): ReadonlyMap<string, DcLanguage> {
  if (definition.formatVersion !== undefined && definition.formatVersion !== 1) throw new Error("Unsupported DC definition format");
  const languages = new Map<string, DcLanguage>();
  for (const profile of definition.profiles) {
    if (!profile.id.trim() || languages.has(profile.id)) throw new Error(`Invalid or duplicate profile: ${profile.id}`);
    const language = DcLanguages.create();
    const explicit = new Set<string>();
    for (const category of [...profile.categories].sort((left, right) => left.path.length - right.path.length)) {
      const categoryKey = JSON.stringify(category.path.map((part) => part.toLowerCase()));
      if (explicit.has(categoryKey)) throw new Error(`Duplicate category: ${category.path.join(".")}`);
      explicit.add(categoryKey);
      let parent = language.rootCategory;
      for (let index = 0; index < category.path.length; index++) {
        const path = category.path.slice(0, index + 1);
        let registered: DcCategory | undefined;
        try { registered = language.category(path); } catch { }
        parent = registered ?? language.registerCategory(parent, category.path[index], index === category.path.length - 1 && Boolean(category.required));
      }
      for (const field of category.fields) language.addField(parent, new DcField(field), Boolean(field.required));
    }
    languages.set(profile.id, language);
  }
  return languages;
}
