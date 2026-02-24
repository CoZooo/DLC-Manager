import AppKit

enum DCHighlighter {
    private static let sectionRegex = try! NSRegularExpression(pattern: #"^\s*\[[^\]]+\]\s*$"#)
    private static let keyValueRegex = try! NSRegularExpression(pattern: #"^\s*("[^"]+"|[A-Za-z_][A-Za-z0-9_.-]*)\s*([=:])"#)
    private static let stringRegex = try! NSRegularExpression(pattern: #""(?:\\.|[^"\\])*""#)
    private static let tripleQuoteRegex = try! NSRegularExpression(pattern: "\"\"\"")
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
        var tripleQuoteOpen = false

        if nsSource.length == 0 {
            return result
        }

        while index < nsSource.length {
            nsSource.getLineStart(&lineStart, end: &lineEnd, contentsEnd: &contentEnd, for: NSRange(location: index, length: 0))
            let range = NSRange(location: lineStart, length: contentEnd - lineStart)
            let line = nsSource.substring(with: range)
            styleLine(line, in: range, target: result, tripleQuoteOpen: &tripleQuoteOpen)
            index = lineEnd
        }

        return result
    }

    private static func styleLine(
        _ line: String,
        in lineRange: NSRange,
        target: NSMutableAttributedString,
        tripleQuoteOpen: inout Bool
    ) {
        let tripleStringRanges = tripleStringSegments(in: line, isOpen: &tripleQuoteOpen)
        let trimmed = line.trimmingCharacters(in: .whitespaces)
        if trimmed.isEmpty {
            if !tripleStringRanges.isEmpty {
                applyColorRanges(tripleStringRanges, color: DCTheme.stringColor, baseOffset: lineRange.location, target: target)
            }
            return
        }

        let lineCoveredByTriple = tripleStringRanges.contains { $0.location == 0 && $0.length == (line as NSString).length }
        if !lineCoveredByTriple && (trimmed.hasPrefix("#") || trimmed.hasPrefix("//")) {
            target.addAttribute(.foregroundColor, value: DCTheme.commentColor, range: lineRange)
            return
        }

        let nsLine = line as NSString
        let fullRange = NSRange(location: 0, length: nsLine.length)

        if sectionRegex.firstMatch(in: line, options: [], range: fullRange) != nil {
            target.addAttributes([.foregroundColor: DCTheme.sectionColor, .font: DCTheme.sectionFont], range: lineRange)
            return
        }

        if let match = keyValueRegex.firstMatch(in: line, options: [], range: fullRange) {
            let keyRange = match.range(at: 1)
            let opRange = match.range(at: 2)
            if keyRange.location != NSNotFound && !tripleStringRanges.contains(where: { rangesOverlap($0, keyRange) }) {
                target.addAttribute(.foregroundColor, value: DCTheme.keyColor, range: shifted(keyRange, by: lineRange.location))
            }
            if opRange.location != NSNotFound && !tripleStringRanges.contains(where: { rangesOverlap($0, opRange) }) {
                target.addAttribute(.foregroundColor, value: DCTheme.operatorColor, range: shifted(opRange, by: lineRange.location))
            }
        }

        var stringRanges = tripleStringRanges
        let normalStringRanges = ranges(for: stringRegex, in: line).filter { normalRange in
            !tripleStringRanges.contains(where: { rangesOverlap($0, normalRange) })
        }
        stringRanges.append(contentsOf: normalStringRanges)

        applyColorRanges(tripleStringRanges, color: DCTheme.stringColor, baseOffset: lineRange.location, target: target)
        applyColorRanges(normalStringRanges, color: DCTheme.stringColor, baseOffset: lineRange.location, target: target)
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

    private static func tripleStringSegments(in line: String, isOpen: inout Bool) -> [NSRange] {
        let nsLine = line as NSString
        let fullRange = NSRange(location: 0, length: nsLine.length)
        let matches = tripleQuoteRegex.matches(in: line, options: [], range: fullRange)

        if matches.isEmpty {
            if isOpen {
                return [NSRange(location: 0, length: nsLine.length)]
            }
            return []
        }

        var result: [NSRange] = []
        var cursor = 0
        var open = isOpen

        for match in matches {
            let qRange = match.range
            guard qRange.location != NSNotFound else { continue }

            if open {
                let len = max(0, (qRange.location + qRange.length) - cursor)
                result.append(NSRange(location: cursor, length: len))
                open = false
                cursor = qRange.location + qRange.length
            } else {
                open = true
                cursor = qRange.location
            }
        }

        if open {
            let tailLen = max(0, nsLine.length - cursor)
            result.append(NSRange(location: cursor, length: tailLen))
        }

        isOpen = open
        return result
    }

    private static func applyColorRanges(
        _ ranges: [NSRange],
        color: NSColor,
        baseOffset: Int,
        target: NSMutableAttributedString
    ) {
        for range in ranges where range.location != NSNotFound && range.length > 0 {
            target.addAttribute(.foregroundColor, value: color, range: shifted(range, by: baseOffset))
        }
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
