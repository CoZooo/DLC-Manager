import AppKit
import XCTest
@testable import DCQuickLookCore

final class DCHighlighterTests: XCTestCase {
    func testAttributedTextPreservesUnicodeAndTokenRanges() throws {
        let source = "[目录]\n名字 = '😀 中文'\nactive = true # comment\n"
        let tokens = try DCTokenizer(definition: DCLanguageDefinition.load()).tokenize(source)
        let rendered = DCHighlighter.highlight(source, tokens: tokens)
        XCTAssertEqual(rendered.string, source)
        for token in tokens {
            let attributes = rendered.attributes(at: token.range.location, effectiveRange: nil)
            XCTAssertEqual(attributes[.foregroundColor] as? NSColor, DCTheme.color(for: token.kind))
            XCTAssertNotNil(attributes[.font] as? NSFont)
        }
    }

    func testThemeResolvesForLightAndDarkAppearances() throws {
        let kinds: [DCTokenKind] = [.category, .key, .string, .number, .boolean, .nullValue, .comment, .punctuation]
        for name in [NSAppearance.Name.aqua, .darkAqua] {
            let appearance = try XCTUnwrap(NSAppearance(named: name))
            appearance.performAsCurrentDrawingAppearance {
                for kind in kinds {
                    let color = DCTheme.color(for: kind).usingColorSpace(.sRGB)
                    XCTAssertNotNil(color)
                    XCTAssertGreaterThan(color?.alphaComponent ?? 0, 0.4)
                    XCTAssertNotEqual(color, NSColor.textBackgroundColor.usingColorSpace(.sRGB))
                }
            }
        }
    }
}
