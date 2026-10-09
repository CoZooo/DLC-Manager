import AppKit

enum DCTheme {
    static let font = NSFont.monospacedSystemFont(ofSize: 13, weight: .regular)
    static let sectionFont = NSFont.monospacedSystemFont(ofSize: 13, weight: .semibold)
    static let baseColor = NSColor.labelColor

    static func color(for kind: DCTokenKind) -> NSColor {
        switch kind {
        case .category: return .systemBlue
        case .key: return .systemPurple
        case .string: return .systemRed
        case .number: return .systemTeal
        case .boolean: return .systemTeal
        case .nullValue: return .systemOrange
        case .comment: return .secondaryLabelColor
        case .punctuation: return .secondaryLabelColor
        }
    }
}
