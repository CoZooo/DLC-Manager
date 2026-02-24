import AppKit

enum DCTheme {
    static let font = NSFont.monospacedSystemFont(ofSize: 13, weight: .regular)
    static let sectionFont = NSFont.monospacedSystemFont(ofSize: 13, weight: .semibold)

    static let baseColor = NSColor.labelColor
    static let sectionColor = NSColor.systemBlue
    static let keyColor = NSColor.systemPurple
    static let operatorColor = NSColor.systemOrange
    static let stringColor = NSColor.systemRed
    static let numberColor = NSColor.systemTeal
    static let boolColor = NSColor.systemTeal
    static let commentColor = NSColor.secondaryLabelColor
}
