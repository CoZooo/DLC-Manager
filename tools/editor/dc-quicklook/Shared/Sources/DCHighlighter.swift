import AppKit

enum DCHighlighter {
    static func highlight(_ source: String, tokens: [DCToken]) -> NSAttributedString {
        let result = NSMutableAttributedString(string: source, attributes: [
            .font: DCTheme.font,
            .foregroundColor: DCTheme.baseColor
        ])
        result.beginEditing()
        for token in tokens {
            var attributes: [NSAttributedString.Key: Any] = [.foregroundColor: DCTheme.color(for: token.kind)]
            if token.kind == .category { attributes[.font] = DCTheme.sectionFont }
            result.addAttributes(attributes, range: token.range)
        }
        result.endEditing()
        return result
    }
}
