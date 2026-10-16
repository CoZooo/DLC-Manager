package com.cozooo.dlc_manager.dc;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DCSyntaxDefinitionTest {
    private static JsonObject definition() {
        try (var input = DCSyntaxDefinition.class.getResourceAsStream("/com/cozooo/dclanguage/language-definition.json")) {
            assertNotNull(input);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    private static Map<String, com.intellij.psi.tree.IElementType> tokens(String text, DCSyntaxDefinition syntax) {
        DCLexer lexer = new DCLexer(syntax); lexer.start(text);
        Map<String, com.intellij.psi.tree.IElementType> result = new LinkedHashMap<>();
        int count = 0;
        while (lexer.getTokenType() != null) {
            assertTrue(lexer.getTokenEnd() > lexer.getTokenStart());
            result.put(text.substring(lexer.getTokenStart(), lexer.getTokenEnd()), lexer.getTokenType());
            lexer.advance(); assertTrue(++count <= text.length());
        }
        return result;
    }
    @Test void lexerUsesChangedModelSymbolsInsteadOfPreviousSyntax() {
        JsonObject model = definition();
        JsonObject syntax = model.getAsJsonObject("syntax"), symbols = syntax.getAsJsonObject("tokens");
        symbols.addProperty("assignment", ":="); symbols.addProperty("comment", "%%");
        symbols.addProperty("categoryOpen", "<"); symbols.addProperty("categoryClose", ">");
        syntax.getAsJsonObject("literals").addProperty("true", "yes");
        Map<String, com.intellij.psi.tree.IElementType> values = tokens("<section>\nvalue := yes %% note\nold = true # old\n", new DCSyntaxDefinition(model));
        assertEquals(DCTokenTypes.SECTION, values.get("<section>")); assertEquals(DCTokenTypes.OPERATOR, values.get(":="));
        assertEquals(DCTokenTypes.BOOLEAN, values.get("yes")); assertEquals(DCTokenTypes.COMMENT, values.get("%% note"));
        assertEquals(DCTokenTypes.IDENTIFIER, values.get("true")); assertEquals(DCTokenTypes.BAD_CHARACTER, values.get("="));
        assertEquals(DCTokenTypes.BAD_CHARACTER, values.get("#"));
    }
    @Test void stringRulesNumbersAndArraysFollowBundledDefinition() {
        JsonObject model = definition();
        JsonObject literal = model.getAsJsonObject("syntax").getAsJsonArray("stringRules").asList().stream()
            .map(JsonElement::getAsJsonObject).filter(value -> value.get("name").getAsString().equals("literal")).findFirst().orElseThrow();
        literal.addProperty("delimiter", "`");
        Map<String, com.intellij.psi.tree.IElementType> values = tokens("text = `literal`\namount = .5e+2\nvalues = [yes]\n", new DCSyntaxDefinition(model));
        assertEquals(DCTokenTypes.STRING, values.get("`literal`")); assertEquals(DCTokenTypes.NUMBER, values.get(".5e+2")); assertEquals(DCTokenTypes.BRACKET, values.get("["));
    }
    @Test void editorDefinitionIsByteIdenticalToBundledServerDefinition() throws Exception {
        byte[] actual;
        try (var input = DCSyntaxDefinition.class.getResourceAsStream("/com/cozooo/dclanguage/language-definition.json")) { actual = input.readAllBytes(); }
        try (ZipFile server = new ZipFile(System.getProperty("dc.language.server"))) {
            assertArrayEquals(server.getInputStream(server.getEntry("com/cozooo/dclanguage/language-definition.json")).readAllBytes(), actual);
        }
        assertEquals(definition().get("definitionHash").getAsString(), DCSyntaxDefinition.get().definitionHash());
    }
}
