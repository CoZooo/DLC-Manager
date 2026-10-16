import Foundation
import XCTest
@testable import DCQuickLookCore

final class DCTokenizerTests: XCTestCase {
    private func tokens(_ source: String, definition: DCLanguageDefinition? = nil) throws -> [(DCTokenKind, String)] {
        let tokenizer = try DCTokenizer(definition: definition ?? DCLanguageDefinition.load())
        let tokens = tokenizer.tokenize(source)
        let text = source as NSString
        var end = 0
        for token in tokens {
            XCTAssertGreaterThan(token.range.length, 0)
            XCTAssertGreaterThanOrEqual(token.range.location, end)
            XCTAssertLessThanOrEqual(NSMaxRange(token.range), text.length)
            end = NSMaxRange(token.range)
        }
        return tokens.map { ($0.kind, text.substring(with: $0.range)) }
    }

    private func assertToken(_ tokens: [(DCTokenKind, String)], _ kind: DCTokenKind, _ text: String, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(tokens.contains { $0.0 == kind && $0.1 == text }, "Missing \(kind): \(text); actual \(tokens)", file: file, line: line)
    }

    private func mutated(_ edit: (inout [String: Any]) -> Void) throws -> DCLanguageDefinition {
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../Shared/Resources/language-definition.json")
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        edit(&object)
        return try DCLanguageDefinition.read(JSONSerialization.data(withJSONObject: object))
    }

    func testBundledDefinitionUsesGeneratedSyntax() throws {
        let definition = try DCLanguageDefinition.load()
        XCTAssertEqual(definition.language, "dc")
        XCTAssertEqual(definition.definitionHash.count, 64)
        XCTAssertEqual(definition.syntax.stringRules.count, 4)
    }

    func testNestedAndQuotedCategoryPaths() throws {
        let source = "[basic]\n  [\"name.with.dots\".'url#//'] # note\n[history.\"资源😀\"]\n"
        let result = try tokens(source)
        assertToken(result, .category, "[basic]")
        assertToken(result, .category, "[\"name.with.dots\".'url#//']")
        assertToken(result, .category, "[history.\"资源😀\"]")
        assertToken(result, .comment, "# note")
    }

    func testColonBlocksAndBareEntries() throws {
        let result = try tokens("dependencies:\n  sodium\n  'api core', # keep\n  optional:\n    version = '1.0'\n")
        assertToken(result, .category, "dependencies")
        assertToken(result, .category, "optional")
        assertToken(result, .key, "sodium")
        assertToken(result, .key, "'api core'")
        assertToken(result, .punctuation, ",")
    }

    func testSingleDoubleAndMultilineStrings() throws {
        let source = "a = 'literal \\n # // true'\nb = \"escaped \\\" quote \\u4e2d\"\nc = '''first\n[not.category] # not a comment\n'''\nd = \"\"\"first\nkey = false // string\n\"\"\"\ne = null"
        let result = try tokens(source)
        XCTAssertEqual(result.filter { $0.0 == .string }.count, 4)
        XCTAssertFalse(result.contains { $0.0 == .comment || $0.0 == .category })
        assertToken(result, .nullValue, "null")
    }

    func testTripleDelimiterInsideCommentDoesNotOpenString() throws {
        let result = try tokens("# ''' and \"\"\"\nvalue = true // \"\"\"\nnext = 12\n")
        assertToken(result, .boolean, "true")
        assertToken(result, .number, "12")
        XCTAssertEqual(result.filter { $0.0 == .comment }.count, 2)
    }

    func testTripleQuoteRulesDoNotApplyEscapesUnlessEnabled() throws {
        let result = try tokens("value = \"\"\"path\\\"\"\"\nnext = false\n")
        assertToken(result, .string, "\"\"\"path\\\"\"\"")
        assertToken(result, .boolean, "false")
    }

    func testNestedArraysAndInlineObjectsWithNewlineSeparators() throws {
        let source = "values = [\n { name: 'x', active = TRUE, missing: null },\n [1, -2.5, +.25, 3e-4],\n # note\n false\n]\nother = 9\n"
        let result = try tokens(source)
        for key in ["values", "name", "active", "missing", "other"] { assertToken(result, .key, key) }
        for number in ["1", "-2.5", "+.25", "3e-4", "9"] { assertToken(result, .number, number) }
        assertToken(result, .boolean, "TRUE")
        assertToken(result, .nullValue, "null")
        assertToken(result, .comment, "# note")
    }

    func testCommentsRespectStringsAndURLFragments() throws {
        let source = "quoted = \"https://example.test/file#part\" # tail\nbare = https://example.test/file#part // tail\npath = '/folder/#name'\n"
        let result = try tokens(source)
        assertToken(result, .string, "\"https://example.test/file#part\"")
        assertToken(result, .string, "https://example.test/file#part")
        XCTAssertEqual(result.filter { $0.0 == .comment }.map { $0.1 }, ["# tail", "// tail"])
    }

    func testScalarMatchingRequiresTheWholeValue() throws {
        let result = try tokens("a = true-value\nb = 1.20.1\nc = nullish\nd = 1file\ne = -1.2E+8\n")
        for text in ["true-value", "1.20.1", "nullish", "1file"] { assertToken(result, .string, text) }
        assertToken(result, .number, "-1.2E+8")
    }

    func testUnknownFieldNamesHaveNoHardcodedCatalog() throws {
        let result = try tokens("futureField = true\n未知字段 = false\n\"field with spaces\" = 7\n")
        for key in ["futureField", "未知字段", "\"field with spaces\""] { assertToken(result, .key, key) }
    }

    func testUTF16RangesAfterEmojiAndCRLF() throws {
        let source = "\u{FEFF}[目录]\r\nname = '😀 汉字'\r\nflag = true\r\n"
        let result = try tokens(source)
        assertToken(result, .string, "'😀 汉字'")
        assertToken(result, .key, "flag")
        assertToken(result, .boolean, "true")
    }

    func testIncompleteStringsRecoverAtTheirAllowedBoundary() throws {
        let result = try tokens("a = \"incomplete\nnext = true\nb = '''unfinished\n[still.string]\n")
        assertToken(result, .string, "\"incomplete")
        assertToken(result, .boolean, "true")
        assertToken(result, .string, "'''unfinished\n[still.string]\n")
    }

    func testEmptyAssignmentsAndComments() throws {
        let result = try tokens("empty =\ncomment = # note\nvalid = false\n")
        assertToken(result, .key, "empty")
        assertToken(result, .comment, "# note")
        assertToken(result, .boolean, "false")
    }

    func testDefinitionChangesAssignmentCategoryAndCollectionTokens() throws {
        let definition = try mutated { object in
            var syntax = object["syntax"] as! [String: Any]
            var tokens = syntax["tokens"] as! [String: String]
            tokens.merge(["assignment": ":=", "categoryOpen": "<", "categoryClose": ">", "categorySeparator": "/", "block": "->", "arrayOpen": "(", "arrayClose": ")", "objectOpen": "{!", "objectClose": "!}", "itemSeparator": ";", "objectSeparator": "=>"], uniquingKeysWith: { _, new in new })
            syntax["tokens"] = tokens
            object["syntax"] = syntax
        }
        let result = try tokens("<basic/'nested'>\nvalues := ({! name => 'ok'; enabled := true !}; 7)\nobject ->\n  field := null\n", definition: definition)
        assertToken(result, .category, "<basic/'nested'>")
        assertToken(result, .category, "object")
        assertToken(result, .key, "enabled")
        assertToken(result, .boolean, "true")
        assertToken(result, .number, "7")
        assertToken(result, .nullValue, "null")
        for token in [":=", "{!", "!}", "=>", "(", ")", ";", "->"] { assertToken(result, .punctuation, token) }
    }

    func testDefinitionChangesCommentsStringsEscapesAndScalars() throws {
        let definition = try mutated { object in
            var syntax = object["syntax"] as! [String: Any]
            var tokens = syntax["tokens"] as! [String: String]
            tokens["comment"] = "%"
            tokens["lineComment"] = "--"
            tokens["escape"] = "~"
            syntax["tokens"] = tokens
            syntax["stringRules"] = [["name": "custom", "delimiter": "`", "multiline": false, "escaped": true], ["name": "customMulti", "delimiter": "```", "multiline": true, "escaped": false]]
            syntax["literals"] = ["true": "yes", "false": "no", "null": "nil"]
            syntax["numberPattern"] = "[0-9]+h"
            object["syntax"] = syntax
        }
        let result = try tokens("title = `literal ~` -- stays string` % note\nlong = ```\n% still string\n```\na = YES\nb = no\nc = nil\nd = 42h -- tail\n", definition: definition)
        assertToken(result, .string, "`literal ~` -- stays string`")
        assertToken(result, .string, "```\n% still string\n```")
        assertToken(result, .boolean, "YES")
        assertToken(result, .boolean, "no")
        assertToken(result, .nullValue, "nil")
        assertToken(result, .number, "42h")
        assertToken(result, .comment, "% note")
        assertToken(result, .comment, "-- tail")
    }

    func testDefinitionBooleanCasePolicy() throws {
        let definition = try mutated { object in
            var policies = object["policies"] as! [String: Any]
            policies["caseInsensitiveBooleans"] = false
            object["policies"] = policies
        }
        let result = try tokens("a = TRUE\nb = true", definition: definition)
        assertToken(result, .string, "TRUE")
        assertToken(result, .boolean, "true")
    }

    func testMissingTokenRejectsDefinition() throws {
        XCTAssertThrowsError(try mutated { object in
            var syntax = object["syntax"] as! [String: Any]
            var tokens = syntax["tokens"] as! [String: String]
            tokens.removeValue(forKey: "assignment")
            syntax["tokens"] = tokens
            object["syntax"] = syntax
        })
    }

    func testDepthAndMalformedInputRemainBounded() throws {
        let source = "a = " + String(repeating: "[", count: 10000) + String(repeating: "]", count: 10000) + "\nnext = true\n"
        let result = try tokens(source)
        assertToken(result, .boolean, "true")
        XCTAssertLessThan(result.count, 600)
    }

    func testMaximumPreviewTokenizationIsBounded() throws {
        let line = "field = [true, -2.5, {name: '中文 😀', value: null}] # note\n"
        let source = String(repeating: line, count: DCTextLoader.maximumBytes / line.utf8.count)
        let start = Date()
        let result = try tokens(source)
        XCTAssertGreaterThan(result.count, 100000)
        XCTAssertLessThan(Date().timeIntervalSince(start), 15)
    }

    func testGeneratedModelMutationFixtureWhenProvided() throws {
        guard let path = ProcessInfo.processInfo.environment["DC_MUTATED_DEFINITION"] else { throw XCTSkip("Optional MPS-generated mutation fixture is not set") }
        let definition = try DCLanguageDefinition.read(Data(contentsOf: URL(fileURLWithPath: path)))
        let assignment = try XCTUnwrap(definition.syntax.tokens["assignment"])
        let literal = try XCTUnwrap(definition.syntax.literals["true"])
        XCTAssertNotEqual(assignment, "=")
        let comment = try XCTUnwrap(definition.syntax.tokens["comment"])
        let open = try XCTUnwrap(definition.syntax.tokens["categoryOpen"])
        let close = try XCTUnwrap(definition.syntax.tokens["categoryClose"])
        let category = "\(open)basic\(close)"
        let result = try tokens("\(comment) generated model\n\(category)\nenabled \(assignment) \(literal)", definition: definition)
        assertToken(result, .punctuation, assignment)
        assertToken(result, .boolean, literal)
        assertToken(result, .category, category)
        assertToken(result, .comment, "\(comment) generated model")
    }
}
