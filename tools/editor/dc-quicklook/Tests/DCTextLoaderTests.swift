import Foundation
import XCTest
@testable import DCQuickLookCore

final class DCTextLoaderTests: XCTestCase {
    func testUTF8AndBOM() throws {
        let source = "[basic]\nname = '中文 😀'"
        for prefix in [Data(), Data([0xEF, 0xBB, 0xBF])] {
            XCTAssertEqual(try DCTextLoader.decode(prefix + Data(source.utf8)).text, source)
        }
    }

    func testUTF16AndUTF32WithAndWithoutBOM() throws {
        let source = "[basic]\nname = '中文 😀'"
        let encodings: [(String.Encoding, [UInt8])] = [(.utf16LittleEndian, [0xFF, 0xFE]), (.utf16BigEndian, [0xFE, 0xFF]), (.utf32LittleEndian, [0xFF, 0xFE, 0, 0]), (.utf32BigEndian, [0, 0, 0xFE, 0xFF])]
        for (encoding, bom) in encodings {
            let encoded = try XCTUnwrap(source.data(using: encoding))
            XCTAssertEqual(try DCTextLoader.decode(encoded).text, source, "\(encoding)")
            XCTAssertEqual(try DCTextLoader.decode(Data(bom) + encoded).text, source, "\(encoding)")
        }
    }

    func testTruncatingUTF8DoesNotCorruptMultibyteCharacters() throws {
        let data = Data("abc😀next".utf8)
        for limit in 4...6 {
            let preview = try DCTextLoader.decode(data, limit: limit)
            XCTAssertTrue(preview.truncated)
            XCTAssertEqual(preview.text, "abc")
        }
    }

    func testTruncatingUTF16DoesNotCorruptSurrogatePairs() throws {
        let source = "abc😀next"
        for (encoding, prefix) in [(String.Encoding.utf16LittleEndian, Data([0xFF, 0xFE])), (.utf16BigEndian, Data([0xFE, 0xFF]))] {
            let data = prefix + (try XCTUnwrap(source.data(using: encoding)))
            for limit in 9...11 {
                XCTAssertEqual(try DCTextLoader.decode(data, limit: limit).text, "abc")
            }
        }
    }

    func testLatin1Fallback() throws {
        XCTAssertEqual(try DCTextLoader.decode(Data([0x6E, 0x3D, 0xE9])).text, "n=é")
    }

    func testUnsupportedExplicitUnicodeEncodingIsReported() {
        XCTAssertThrowsError(try DCTextLoader.decode(Data([0xFF, 0xFE, 0x00])))
    }

    func testFileReadIsBoundedAndReportsTruncation() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = directory.appendingPathComponent("large.dc")
        try Data(repeating: 65, count: 1024 * 1024).write(to: file)
        let preview = try DCTextLoader.read(file, limit: 1024)
        XCTAssertTrue(preview.truncated)
        XCTAssertEqual(preview.text.utf8.count, 1024)
    }

    func testEmptyInput() throws {
        let preview = try DCTextLoader.decode(Data())
        XCTAssertEqual(preview.text, "")
        XCTAssertFalse(preview.truncated)
    }
}
