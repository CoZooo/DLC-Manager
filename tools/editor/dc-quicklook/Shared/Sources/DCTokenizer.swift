import Foundation

enum DCTokenKind: Equatable {
    case category, key, string, number, boolean, nullValue, comment, punctuation
}

struct DCToken: Equatable {
    let kind: DCTokenKind
    let range: NSRange
}

struct DCTokenizer {
    private let definition: DCLanguageDefinition
    private let tokens: [String: [UInt16]]
    private let strings: [(DCLanguageDefinition.StringRule, [UInt16])]
    private let number: NSRegularExpression

    init(definition: DCLanguageDefinition) throws {
        self.definition = definition
        tokens = definition.syntax.tokens.mapValues { Array($0.utf16) }
        strings = definition.syntax.stringRules.map { ($0, Array($0.delimiter.utf16)) }
            .sorted { $0.1.count > $1.1.count }
        number = try NSRegularExpression(pattern: "\\A(?:\(definition.syntax.numberPattern))\\z")
    }

    func tokenize(_ source: String) -> [DCToken] {
        var scanner = Scanner(units: Array(source.utf16), tokenizer: self)
        return scanner.scan()
    }

    private struct Scanner {
        let units: [UInt16]
        let tokenizer: DCTokenizer
        var position = 0
        var result: [DCToken] = []

        mutating func scan() -> [DCToken] {
            while position < units.count {
                skipWhitespace()
                guard position < units.count else { break }
                if commentAt(position) { comment(); continue }
                if matches("categoryOpen") { category(); continue }
                statement()
            }
            return result
        }

        mutating func statement() {
            let start = position
            var separator: Int?
            while position < units.count && !newline(position) {
                if let rule = stringRule() { skipString(rule); continue }
                if commentAt(position) { break }
                if matches("assignment") { separator = position; break }
                position += 1
            }
            if let separator = separator {
                emit(.key, start, trimmedEnd(start, separator))
                punctuation("assignment")
                skipHorizontal()
                value(depth: 0, nested: false)
            } else {
                let end = trimmedEnd(start, position)
                let block = tokenizer.tokens["block"]!
                let blockStart = end - block.count
                if tokenizer.definition.policies.allowColonCategories && blockStart >= start && matches(block, at: blockStart) {
                    emit(.category, start, trimmedEnd(start, blockStart))
                    emit(.punctuation, blockStart, end)
                } else if tokenizer.definition.policies.allowBareEntries {
                    let item = tokenizer.tokens["itemSeparator"]!
                    let itemStart = end - item.count
                    if itemStart >= start && matches(item, at: itemStart) {
                        emit(.key, start, trimmedEnd(start, itemStart))
                        emit(.punctuation, itemStart, end)
                    } else {
                        emit(.key, start, end)
                    }
                }
            }
            while position < units.count && !newline(position) {
                if commentAt(position) { comment(); return }
                position += 1
            }
        }

        mutating func category() {
            let start = position
            position += tokenizer.tokens["categoryOpen"]!.count
            while position < units.count && !newline(position) {
                if let rule = stringRule() { skipString(rule); continue }
                if matches("categoryClose") {
                    position += tokenizer.tokens["categoryClose"]!.count
                    break
                }
                if commentAt(position) { break }
                position += 1
            }
            emit(.category, start, position)
        }

        mutating func value(depth: Int, nested: Bool) {
            guard position < units.count && !newline(position) && !commentAt(position) else { return }
            if let rule = stringRule() {
                let start = position
                skipString(rule)
                emit(.string, start, position)
                return
            }
            if depth < min(tokenizer.definition.policies.maxDepth, 256) {
                if matches("arrayOpen") { container(array: true, depth: depth + 1); return }
                if matches("objectOpen") { container(array: false, depth: depth + 1); return }
            }
            let start = position
            let url = startsURL(at: start)
            while position < units.count && !newline(position) {
                if commentAt(position) && !(url && position > start && !horizontal(position - 1)) { break }
                if nested && (matches("itemSeparator") || matches("arrayClose") || matches("objectClose")) { break }
                position += 1
            }
            let end = trimmedEnd(start, position)
            guard end > start else { return }
            let raw = String(decoding: units[start..<end], as: UTF16.self)
            let syntax = tokenizer.definition.syntax
            let caseInsensitive = tokenizer.definition.policies.caseInsensitiveBooleans
            let compare: (String) -> Bool = { literal in
                if caseInsensitive {
                    return raw.caseInsensitiveCompare(literal) == .orderedSame
                }
                return raw == literal
            }
            let kind: DCTokenKind
            if syntax.valueTypes.contains("boolean") && (compare(syntax.literals["true"]!) || compare(syntax.literals["false"]!)) {
                kind = .boolean
            } else if syntax.valueTypes.contains("null") && raw == syntax.literals["null"]! {
                kind = .nullValue
            } else if syntax.valueTypes.contains("number") && tokenizer.number.firstMatch(in: raw, range: NSRange(location: 0, length: end - start)) != nil {
                kind = .number
            } else {
                kind = .string
            }
            emit(kind, start, end)
        }

        mutating func container(array: Bool, depth: Int) {
            punctuation(array ? "arrayOpen" : "objectOpen")
            let close = array ? "arrayClose" : "objectClose"
            while position < units.count {
                skipTrivia()
                if matches(close) { punctuation(close); return }
                if matches("itemSeparator") { punctuation("itemSeparator"); continue }
                let previous = position
                if !array {
                    let start = position
                    if let rule = stringRule() { skipString(rule) }
                    else {
                        while position < units.count && !horizontal(position) && !newline(position)
                            && !matches("objectSeparator") && !matches("assignment") && !matches(close)
                            && !matches("itemSeparator") && !commentAt(position) { position += 1 }
                    }
                    emit(.key, start, position)
                    skipTrivia()
                    let separators = ["objectSeparator", "assignment"].sorted {
                        tokenizer.tokens[$0]!.count > tokenizer.tokens[$1]!.count
                    }
                    if let separator = separators.first(where: { matches($0) }) { punctuation(separator) }
                    skipTrivia()
                }
                if matches(close) { punctuation(close); return }
                value(depth: depth, nested: true)
                if position == previous { position += 1 }
            }
        }

        mutating func skipString(_ rule: (DCLanguageDefinition.StringRule, [UInt16])) {
            position += rule.1.count
            while position < units.count {
                if matches(rule.1, at: position) { position += rule.1.count; return }
                if !rule.0.multiline && newline(position) { return }
                if rule.0.escaped && matches("escape") {
                    position += tokenizer.tokens["escape"]!.count
                    if position < units.count { position += 1 }
                } else { position += 1 }
            }
        }

        mutating func skipTrivia() {
            while position < units.count {
                skipWhitespace()
                if commentAt(position) { comment() }
                else { return }
            }
        }

        mutating func skipWhitespace() {
            while position < units.count && (horizontal(position) || newline(position) || position == 0 && units[position] == 0xFEFF) { position += 1 }
        }

        mutating func skipHorizontal() {
            while position < units.count && horizontal(position) { position += 1 }
        }

        mutating func comment() {
            let start = position
            while position < units.count && !newline(position) { position += 1 }
            emit(.comment, start, position)
        }

        mutating func punctuation(_ name: String) {
            let start = position
            position += tokenizer.tokens[name]!.count
            emit(.punctuation, start, position)
        }

        mutating func emit(_ kind: DCTokenKind, _ start: Int, _ end: Int) {
            if end > start { result.append(DCToken(kind: kind, range: NSRange(location: start, length: end - start))) }
        }

        func stringRule() -> (DCLanguageDefinition.StringRule, [UInt16])? {
            tokenizer.strings.first { matches($0.1, at: position) }
        }

        func matches(_ name: String) -> Bool {
            matches(tokenizer.tokens[name]!, at: position)
        }

        func matches(_ token: [UInt16], at index: Int) -> Bool {
            guard index >= 0 && index + token.count <= units.count else { return false }
            for offset in token.indices where units[index + offset] != token[offset] { return false }
            return true
        }

        func commentAt(_ index: Int) -> Bool {
            matches(tokenizer.tokens["comment"]!, at: index) || matches(tokenizer.tokens["lineComment"]!, at: index)
        }

        func newline(_ index: Int) -> Bool { units[index] == 10 || units[index] == 13 }
        func horizontal(_ index: Int) -> Bool { units[index] == 32 || units[index] == 9 }

        func trimmedEnd(_ start: Int, _ end: Int) -> Int {
            var end = end
            while end > start && horizontal(end - 1) { end -= 1 }
            return end
        }

        func startsURL(at start: Int) -> Bool {
            var index = start
            while index < units.count {
                let unit = units[index]
                if unit == 58 {
                    return index > start && index + 2 < units.count && units[index + 1] == 47 && units[index + 2] == 47
                }
                if !(unit >= 65 && unit <= 90 || unit >= 97 && unit <= 122 || index > start && (unit >= 48 && unit <= 57 || unit == 43 || unit == 45 || unit == 46)) { return false }
                index += 1
            }
            return false
        }
    }
}
