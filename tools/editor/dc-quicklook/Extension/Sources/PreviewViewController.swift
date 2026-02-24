import AppKit
import QuickLookUI

final class PreviewViewController: NSViewController, QLPreviewingController {
    private var textView = NSTextView()

    override func loadView() {
        let scrollView = NSTextView.scrollableTextView()
        scrollView.hasVerticalScroller = true
        scrollView.hasHorizontalScroller = true
        scrollView.autohidesScrollers = true
        scrollView.borderType = .noBorder

        if let tv = scrollView.documentView as? NSTextView {
            textView = tv
        } else {
            textView = NSTextView(frame: .zero)
            scrollView.documentView = textView
        }

        textView.isEditable = false
        textView.isSelectable = true
        textView.isRichText = true
        textView.drawsBackground = true
        textView.backgroundColor = NSColor.textBackgroundColor
        textView.textContainerInset = NSSize(width: 14, height: 14)
        textView.isAutomaticLinkDetectionEnabled = true
        textView.string = ""

        self.view = scrollView
    }

    func preparePreviewOfFile(at url: URL, completionHandler handler: @escaping (Error?) -> Void) {
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                let content = try self.loadText(from: url)
                let rendered = DCHighlighter.highlight(content)
                DispatchQueue.main.async {
                    self.textView.textStorage?.setAttributedString(rendered)
                    handler(nil)
                }
            } catch {
                DispatchQueue.main.async {
                    self.renderError("Failed to preview:\n\(error.localizedDescription)")
                    handler(nil)
                }
            }
        }
    }

    private func loadText(from url: URL) throws -> String {
        let data = try Data(contentsOf: url, options: [.mappedIfSafe])
        let encodings: [String.Encoding] = [
            .utf8,
            .utf16,
            .utf16LittleEndian,
            .utf16BigEndian,
            .utf32,
            .unicode,
            .ascii,
            .isoLatin1
        ]
        for encoding in encodings {
            if let text = String(data: data, encoding: encoding) {
                return text
            }
        }
        throw NSError(domain: "DCQuickLook", code: 1001, userInfo: [
            NSLocalizedDescriptionKey: "Unsupported text encoding"
        ])
    }

    private func renderError(_ message: String) {
        let text = NSMutableAttributedString(string: message, attributes: [
            .font: NSFont.monospacedSystemFont(ofSize: 12, weight: .regular),
            .foregroundColor: NSColor.systemRed
        ])
        textView.textStorage?.setAttributedString(text)
    }
}
