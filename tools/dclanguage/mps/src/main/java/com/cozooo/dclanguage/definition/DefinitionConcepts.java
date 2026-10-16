package com.cozooo.dclanguage.definition;

import java.util.*;
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory;
import org.jetbrains.mps.openapi.language.*;

public final class DefinitionConcepts {
    public static final String NAMESPACE = "com.cozooo.dclanguage.definition";
    public static final String STRUCTURE = "r:7c669566-3388-4f44-923a-c8a767fd751a(com.cozooo.dclanguage.definition.structure)";
    public static final long HIGH = 0x4b259ac30fd5481eL;
    public static final long LOW = 0xb3705e307e623184L;
    public static final SLanguage LANGUAGE = MetaAdapterFactory.getLanguage(HIGH, LOW, NAMESPACE);
    public static final SProperty NAME = MetaAdapterFactory.getProperty(0xceab519525ea4f22L, 0x9b92103b95ca8c0cL, 0x110396eaaa4L, 0x110396ec041L, "name");
    public record Property(String name, long id, String type) { }
    public record Child(String name, long id, String target) { }
    public record Concept(String name, long id, boolean root, List<Property> properties, List<Child> children) { }
    public static final Map<String, Concept> CONCEPTS = new LinkedHashMap<>();
    static {
        define("Definition", 2001, true, List.of(new Property("formatVersion", 200101, "integer")), List.of(new Child("tokens", 200151, "SyntaxToken"), new Child("strings", 200152, "StringRule"), new Child("escapes", 200153, "EscapeRule"), new Child("valueTypes", 200154, "ValueType"), new Child("policies", 200155, "Policy"), new Child("profiles", 200156, "SchemaProfile"), new Child("literals", 200157, "LiteralRule"), new Child("numbers", 200158, "NumberRule"), new Child("names", 200159, "NameRule")));
        define("SyntaxToken", 2002, false, List.of(new Property("value", 200201, "string")), List.of());
        define("StringRule", 2003, false, List.of(new Property("delimiter", 200301, "string"), new Property("multiline", 200302, "boolean"), new Property("escaped", 200303, "boolean")), List.of());
        define("EscapeRule", 2004, false, List.of(new Property("value", 200401, "string")), List.of());
        define("ValueType", 2005, false, List.of(new Property("description", 200501, "string")), List.of());
        define("Policy", 2006, false, List.of(new Property("value", 200601, "string")), List.of());
        define("SchemaProfile", 2007, false, List.of(new Property("description", 200701, "string")), List.of(new Child("filePatterns", 200751, "StringValue"), new Child("categories", 200752, "Category")));
        define("Category", 2008, false, List.of(new Property("required", 200801, "boolean"), new Property("description", 200802, "string")), List.of(new Child("fields", 200851, "Field"), new Child("categories", 200852, "Category")));
        define("Field", 2009, false, List.of(new Property("type", 200901, "string"), new Property("required", 200902, "boolean"), new Property("description", 200903, "string"), new Property("defaultValue", 200904, "string")), List.of(new Child("enumValues", 200951, "StringValue"), new Child("constraints", 200952, "Constraint")));
        define("Constraint", 2010, false, List.of(new Property("value", 201001, "string")), List.of());
        define("StringValue", 2011, false, List.of(new Property("value", 201101, "string")), List.of());
        define("LiteralRule", 2012, false, List.of(new Property("value", 201201, "string"), new Property("type", 201202, "string")), List.of());
        define("NumberRule", 2013, false, List.of(new Property("pattern", 201301, "string")), List.of());
        define("NameRule", 2014, false, List.of(new Property("pattern", 201401, "string")), List.of());
    }
    private DefinitionConcepts() { }
    private static void define(String name, long id, boolean root, List<Property> properties, List<Child> children) {
        CONCEPTS.put(name, new Concept(name, id, root, properties, children));
    }
    public static SConcept concept(String name) {
        Concept definition = Objects.requireNonNull(CONCEPTS.get(name), "Unknown definition concept: " + name);
        return MetaAdapterFactory.getConcept(HIGH, LOW, definition.id(), NAMESPACE + ".structure." + name);
    }
    public static SProperty property(String concept, String name) {
        if (name.equals("name")) return NAME;
        Concept definition = CONCEPTS.get(concept);
        Property property = definition.properties().stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
        return MetaAdapterFactory.getProperty(HIGH, LOW, definition.id(), property.id(), name);
    }
    public static SContainmentLink child(String concept, String name) {
        Concept definition = CONCEPTS.get(concept);
        Child child = definition.children().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
        return MetaAdapterFactory.getContainmentLink(HIGH, LOW, definition.id(), child.id(), name);
    }
}
