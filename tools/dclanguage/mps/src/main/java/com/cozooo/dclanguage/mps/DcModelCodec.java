package com.cozooo.dclanguage.mps;

import static com.cozooo.dclanguage.mps.DcConcepts.*;
import com.cozooo.dclanguage.*;
import com.google.gson.*;
import jetbrains.mps.smodel.SNodeId;
import org.jetbrains.mps.openapi.language.SConcept;
import org.jetbrains.mps.openapi.language.SContainmentLink;
import org.jetbrains.mps.openapi.model.SNode;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class DcModelCodec {
    private static final String SOURCE = "com.cozooo.dclanguage.mps.originalText";
    private DcModelCodec() { }
    public static SNode read(String name, String text) throws DcParseException {
        DcDocument document = DcDocuments.readText(text);
        SNode root = node(DOCUMENT, "root");
        root.setProperty(NAME, name);
        root.putUserObject(SOURCE, text);
        members(root, ROOT_MEMBERS, document.root(), "root");
        return root;
    }
    private static void members(SNode parent, SContainmentLink role, JsonObject data, String path) {
        for (var entry : data.entrySet()) {
            String childPath = path + "/" + entry.getKey().length() + ":" + entry.getKey();
            SNode member = node(MEMBER, childPath);
            member.setProperty(NAME, entry.getKey());
            member.addChild(MEMBER_VALUE, value(entry.getValue(), childPath + "/value"));
            parent.addChild(role, member);
        }
    }
    private static SNode value(JsonElement data, String path) {
        if (data.isJsonObject()) {
            SNode result = node(OBJECT, path);
            members(result, OBJECT_MEMBERS, data.getAsJsonObject(), path);
            return result;
        }
        if (data.isJsonArray()) {
            SNode result = node(ARRAY, path);
            for (int i = 0; i < data.getAsJsonArray().size(); i++) result.addChild(ARRAY_VALUES, value(data.getAsJsonArray().get(i), path + "/" + i));
            return result;
        }
        SNode result = node(SCALAR, path);
        result.setProperty(TEXT, DcDocuments.writeValue(data));
        return result;
    }
    private static SNode node(SConcept concept, String path) {
        return new jetbrains.mps.smodel.SNode(concept, SNodeId.Foreign.fromIdNoPrefix("dc:" + UUID.nameUUIDFromBytes(path.getBytes(StandardCharsets.UTF_8))));
    }
    public static DcDocument document(SNode root) {
        if (!root.getConcept().equals(DOCUMENT)) throw new IllegalArgumentException("Expected a DC Document root");
        JsonObject data = readMembers(root, ROOT_MEMBERS);
        try {
            Object source = root.getUserObject(SOURCE);
            DcDocument document = source instanceof String text ? DcDocuments.readText(text) : DcDocuments.create();
            document.root().asMap().clear();
            for (var entry : data.entrySet()) document.root().add(entry.getKey(), entry.getValue());
            return document;
        } catch (DcParseException exception) { throw new IllegalArgumentException("Invalid original DC document", exception); }
    }
    public static String write(SNode root) { return DcDocuments.writeText(document(root)); }
    public static void remember(SNode root, String source) { root.putUserObject(SOURCE, source); }
    private static JsonObject readMembers(SNode parent, SContainmentLink role) {
        JsonObject data = new JsonObject();
        for (SNode member : parent.getChildren(role)) {
            if (!member.getConcept().equals(MEMBER)) throw new IllegalArgumentException("An object contains a non-member node");
            String name = member.getProperty(NAME);
            if (name == null || name.isEmpty()) throw new IllegalArgumentException("A field name must not be empty");
            if (data.has(name)) throw new IllegalArgumentException("Duplicate field: " + name);
            var values = member.getChildren(MEMBER_VALUE).iterator();
            if (!values.hasNext()) throw new IllegalArgumentException("Missing value for field: " + name);
            data.add(name, readValue(values.next()));
            if (values.hasNext()) throw new IllegalArgumentException("Multiple values for field: " + name);
        }
        return data;
    }
    public static JsonElement readValue(SNode node) {
        if (node.getConcept().equals(OBJECT)) return readMembers(node, OBJECT_MEMBERS);
        if (node.getConcept().equals(ARRAY)) {
            JsonArray values = new JsonArray();
            for (SNode child : node.getChildren(ARRAY_VALUES)) values.add(readValue(child));
            return values;
        }
        if (!node.getConcept().equals(SCALAR)) throw new IllegalArgumentException("Unknown DC value concept: " + node.getConcept());
        String text = node.getProperty(TEXT);
        if (text == null || text.isBlank()) throw new IllegalArgumentException("A scalar value is missing; use \"\" for an empty string");
        try {
            JsonObject parsed = DcDocuments.readText("value " + com.cozooo.dclanguage.generated.DcGeneratedSyntax.TOKEN_ASSIGNMENT + " " + text).root();
            if (parsed.size() != 1 || !parsed.has("value")) throw new IllegalArgumentException("A value cannot contain additional fields");
            JsonElement value = parsed.get("value");
            if (value.isJsonObject() || value.isJsonArray()) throw new IllegalArgumentException("Use an object or array node for structured values");
            return value;
        } catch (DcParseException exception) { throw new IllegalArgumentException(exception.getMessage(), exception); }
    }
    public static List<String> validate(SNode root) {
        try { document(root); return List.of(); }
        catch (IllegalArgumentException exception) { return List.of(exception.getMessage()); }
    }
}
