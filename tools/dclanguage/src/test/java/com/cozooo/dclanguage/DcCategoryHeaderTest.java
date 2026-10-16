package com.cozooo.dclanguage;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DcCategoryHeaderTest {
    @Test
    void scansHeadersWithoutImposingDocumentUniqueness() throws Exception {
        String source = "[dependencies.same]\nidentifier = 'first'\n[dependencies.same]\nidentifier = 'second'\n";
        assertThrows(DcParseException.class, () -> DcDocuments.readText(source));
        var headers = DcDocuments.categoryHeaders(source);
        assertEquals(2, headers.size());
        assertEquals(List.of("dependencies", "same"), headers.get(0).names());
        assertEquals("[dependencies.same]", source.substring(headers.get(1).start(), headers.get(1).end()));
    }

    @Test
    void skipsMultilineValuesAndReportsColonAndQuotedHeaders() throws Exception {
        String source = "\uFEFF# first\r\ntext = \"\"\"\r\n[not.a.header]\r\n\"\"\"\r\n"
            + "values = [\n'''\n[not.either]\n'''\n]\n"
            + "[parent.\"name.with.dots\"] # keep\n  child:\n    key = true\n";
        var headers = DcDocuments.categoryHeaders(source);
        assertEquals(2, headers.size());
        assertEquals(List.of("parent", "name.with.dots"), headers.get(0).names());
        assertTrue(headers.get(0).bracketed());
        assertEquals("[parent.\"name.with.dots\"]", source.substring(headers.get(0).start(), headers.get(0).end()));
        assertEquals(List.of("child"), headers.get(1).names());
        assertEquals(2, headers.get(1).indentation());
        assertFalse(headers.get(1).bracketed());
    }
}
