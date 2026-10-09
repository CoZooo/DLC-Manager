import Foundation

struct DCLanguageDefinition: Decodable {
    struct StringRule: Decodable {
        let name: String
        let delimiter: String
        let multiline: Bool
        let escaped: Bool
    }

    struct Syntax: Decodable {
        let tokens: [String: String]
        let stringRules: [StringRule]
        let literals: [String: String]
        let escapes: [String: String]
        let numberPattern: String
        let valueTypes: [String]
    }

    struct Policies: Decodable {
        let caseInsensitiveBooleans: Bool
        let allowBareEntries: Bool
        let allowColonCategories: Bool
        let maxDepth: Int
    }

    let formatVersion: Int
    let language: String
    let syntax: Syntax
    let policies: Policies
    let definitionHash: String

    static func read(_ data: Data) throws -> DCLanguageDefinition {
        let definition = try JSONDecoder().decode(Self.self, from: data)
        let required = ["assignment", "categoryOpen", "categoryClose", "categorySeparator", "block", "arrayOpen", "arrayClose", "objectOpen", "objectClose", "itemSeparator", "objectSeparator", "escape", "comment", "lineComment", "unicodeEscape"]
        guard definition.formatVersion == 1, definition.language == "dc",
              required.allSatisfy({ !(definition.syntax.tokens[$0] ?? "").isEmpty }),
              ["true", "false", "null"].allSatisfy({ !(definition.syntax.literals[$0] ?? "").isEmpty }),
              !definition.syntax.stringRules.isEmpty,
              definition.syntax.stringRules.allSatisfy({ !$0.delimiter.isEmpty }),
              definition.policies.maxDepth > 0 else {
            throw DCPreviewError.invalidDefinition
        }
        _ = try NSRegularExpression(pattern: "\\A(?:\(definition.syntax.numberPattern))\\z")
        return definition
    }

    private static let bundled: Result<DCLanguageDefinition, Error> = Result {
        #if SWIFT_PACKAGE
        let bundle = Bundle.module
        #else
        let bundle = Bundle(for: DCLanguageBundle.self)
        #endif
        guard let url = bundle.url(forResource: "language-definition", withExtension: "json") else {
            throw DCPreviewError.missingDefinition
        }
        return try read(Data(contentsOf: url))
    }

    static func load() throws -> DCLanguageDefinition {
        try bundled.get()
    }
}

private final class DCLanguageBundle: NSObject { }

enum DCPreviewError: LocalizedError {
    case missingDefinition
    case invalidDefinition
    case unsupportedEncoding

    var errorDescription: String? {
        switch self {
        case .missingDefinition: return "The DC language definition is missing from the preview extension."
        case .invalidDefinition: return "The DC language definition is invalid or unsupported."
        case .unsupportedEncoding: return "Unsupported text encoding."
        }
    }
}
