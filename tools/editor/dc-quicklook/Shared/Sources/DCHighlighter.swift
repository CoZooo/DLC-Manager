import AppKit

enum DCHighlighter {
    private static let sectionRegex = try! NSRegularExpression(pattern: #"^\s*\[[^\]]+\]\s*$"#)
    private static let keyValueRegex = try! NSRegularExpression(pattern: #"^\s*("[^"]+"|[A-Za-z_][A-Za-z0-9_.-]*)\s*([=:])"#)
    private static let stringRegex = try! NSRegularExpression(pattern: #""(?:\\.|[^"\\])*""#)
    private static let numberRegex = try! NSRegularExpression(pattern: #"\b-?\d+(\.\d+)?\b"#)
    private static let boolRegex = try! NSRegularExpression(pattern: #"\b(true|false)\b"#)

    static func highlight(_ source: String) -> NSAttributedString {
        let nsSource = source as NSString
        let result = NSMutableAttributedString(
            string: source,
            attributes: [
                .font: DCTheme.font,
                .foregroundColor: DCTheme.baseColor
            ]
        )

        var lineStart = 0
        var lineEnd = 0
        var contentEnd = 0
        var index = 0

        if nsSource.length == 0 {
            return result
        }

        while index < nsSource.length {
            nsSource.getLineStart(&lineStart, end: &lineEnd, contentsEnd: &contentEnd, for: NSRange(location: index, length: 0))
            let range = NSRange(location: lineStart, length: contentEnd - lineStart)
            let line = nsSource.substring(with: range)
            styleLine(line, in: range, target: result)
            index = lineEnd
        }

        return result
    }

    private static func styleLine(_ line: String, in lineRange: NSRange, target: NSMutableAttributedString) {
        let trimmed = line.trimmingCharacters(in: .whitespaces)
        if trimmed.isEmpty {
            return
        }

        if trimmed.hasPrefix("#") || trimmed.hasPrefix("//") {
            target.addAttribute(.foregroundColor, value: DCTheme.commentColor, range: lineRange)
            return
        }

        let nsLine = line as NSString
        let fullRange = NSRange(location: 0, length: nsLine.length)

        if sectionRegex.firstMatch(in: line, options: [], range: fullRange) != nil {
            target.addAttributes([.foregroundColor: DCTheme.sectionColor, .font: DCTheme.sectionFont], range: lineRange)
            return
        }

        if line.contains("\"\"\"") {
            target.addAttribute(.foregroundColor, value: DCTheme.stringColor, range: lineRange)
            return
        }

        if let match = keyValueRegex.firstMatch(in: line, options: [], range: fullRange) {
            let keyRange = match.range(at: 1)
            let opRange = match.range(at: 2)
            if keyRange.location != NSNotFound {
                target.addAttribute(.foregroundColor, value: DCTheme.keyColor, range: shifted(keyRange, by: lineRange.location))
            }
            if opRange.location != NSNotFound {
                target.addAttribute(.foregroundColor, value: DCTheme.operatorColor, range: shifted(opRange, by: lineRange.location))
            }
        }

        let stringRanges = ranges(for: stringRegex, in: line)
        highlightRegex(stringRegex, color: DCTheme.stringColor, line: line, baseOffset: lineRange.location, target: target)
        highlightRegex(
            boolRegex,
            color: DCTheme.boolColor,
            line: line,
            baseOffset: lineRange.location,
            target: target,
            excluding: stringRanges
        )
        highlightRegex(
            numberRegex,
            color: DCTheme.numberColor,
            line: line,
            baseOffset: lineRange.location,
            target: target,
            excluding: stringRanges
        )
    }

    private static func highlightRegex(
        _ regex: NSRegularExpression,
        color: NSColor,
        line: String,
        baseOffset: Int,
        target: NSMutableAttributedString,
        excluding excludedRanges: [NSRange] = []
    ) {
        let nsLine = line as NSString
        let range = NSRange(location: 0, length: nsLine.length)
        regex.enumerateMatches(in: line, options: [], range: range) { match, _, _ in
            guard let m = match else {
                return
            }
            if excludedRanges.contains(where: { rangesOverlap($0, m.range) }) {
                return
            }
            target.addAttribute(.foregroundColor, value: color, range: shifted(m.range, by: baseOffset))
        }
    }

    private static func ranges(for regex: NSRegularExpression, in line: String) -> [NSRange] {
        let nsLine = line as NSString
        let fullRange = NSRange(location: 0, length: nsLine.length)
        return regex.matches(in: line, options: [], range: fullRange).map { $0.range }
    }

    private static func rangesOverlap(_ a: NSRange, _ b: NSRange) -> Bool {
        let aEnd = a.location + a.length
        let bEnd = b.location + b.length
        return a.location < bEnd && b.location < aEnd
    }

    private static func shifted(_ range: NSRange, by offset: Int) -> NSRange {
        NSRange(location: range.location + offset, length: range.length)
    }
}
