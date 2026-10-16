import Foundation

struct DCPreviewText {
    let text: String
    let truncated: Bool
}

enum DCTextLoader {
    static let maximumBytes = 2 * 1024 * 1024

    static func read(_ url: URL, limit: Int = maximumBytes) throws -> DCPreviewText {
        precondition(limit > 0)
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        let data = try handle.read(upToCount: limit + 4) ?? Data()
        return try decode(data, limit: limit)
    }

    static func decode(_ data: Data, limit: Int = maximumBytes) throws -> DCPreviewText {
        precondition(limit > 0)
        let truncated = data.count > limit
        var bytes = Array(data.prefix(limit))
        let encoding: String.Encoding?
        if bytes.starts(with: [0xFF, 0xFE, 0x00, 0x00]) {
            bytes.removeFirst(4)
            encoding = .utf32LittleEndian
        } else if bytes.starts(with: [0x00, 0x00, 0xFE, 0xFF]) {
            bytes.removeFirst(4)
            encoding = .utf32BigEndian
        } else if bytes.starts(with: [0xFF, 0xFE]) {
            bytes.removeFirst(2)
            encoding = .utf16LittleEndian
        } else if bytes.starts(with: [0xFE, 0xFF]) {
            bytes.removeFirst(2)
            encoding = .utf16BigEndian
        } else if bytes.starts(with: [0xEF, 0xBB, 0xBF]) {
            bytes.removeFirst(3)
            encoding = .utf8
        } else {
            encoding = inferredUnicodeEncoding(bytes)
        }
        if let encoding = encoding {
            guard let text = decode(bytes, encoding: encoding, truncated: truncated) else {
                throw DCPreviewError.unsupportedEncoding
            }
            return DCPreviewText(text: text, truncated: truncated)
        }
        if let text = decode(bytes, encoding: .utf8, truncated: truncated) {
            return DCPreviewText(text: text, truncated: truncated)
        }
        guard let text = String(data: Data(bytes), encoding: .isoLatin1) else {
            throw DCPreviewError.unsupportedEncoding
        }
        return DCPreviewText(text: text, truncated: truncated)
    }

    private static func decode(_ bytes: [UInt8], encoding: String.Encoding, truncated: Bool) -> String? {
        let width = encoding == .utf16LittleEndian || encoding == .utf16BigEndian ? 2
            : encoding == .utf32LittleEndian || encoding == .utf32BigEndian ? 4 : 1
        if bytes.count % width == 0, let text = String(data: Data(bytes), encoding: encoding) { return text }
        guard truncated && !bytes.isEmpty else { return nil }
        for removed in 1...min(4, bytes.count) where (bytes.count - removed) % width == 0 {
            if let text = String(data: Data(bytes.dropLast(removed)), encoding: encoding) { return text }
        }
        return nil
    }

    private static func inferredUnicodeEncoding(_ bytes: [UInt8]) -> String.Encoding? {
        let sample = Array(bytes.prefix(512))
        guard sample.count >= 4 else { return nil }
        if sample.count >= 8 {
            let groups = sample.count / 4
            let le = (0..<groups).filter { sample[$0 * 4 + 2] == 0 && sample[$0 * 4 + 3] == 0 }.count
            let be = (0..<groups).filter { sample[$0 * 4] == 0 && sample[$0 * 4 + 1] == 0 }.count
            if le * 4 >= groups * 3 { return .utf32LittleEndian }
            if be * 4 >= groups * 3 { return .utf32BigEndian }
        }
        let pairs = sample.count / 2
        let even = (0..<pairs).filter { sample[$0 * 2] == 0 }.count
        let odd = (0..<pairs).filter { sample[$0 * 2 + 1] == 0 }.count
        if odd * 4 >= pairs && even * 4 < pairs { return .utf16LittleEndian }
        if even * 4 >= pairs && odd * 4 < pairs { return .utf16BigEndian }
        return nil
    }
}
