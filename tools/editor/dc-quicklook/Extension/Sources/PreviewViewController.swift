import AppKit
import QuickLookUI

final class PreviewViewController: NSViewController, QLPreviewingController {
    private var textView = NSTextView()
    private var request = UUID()

    override func loadView() {
        let scrollView = NSTextView.scrollableTextView()
        scrollView.hasVerticalScroller = true
        scrollView.hasHorizontalScroller = true
        scrollView.autohidesScrollers = true
        scrollView.borderType = .noBorder
        if let textView = scrollView.documentView as? NSTextView {
            self.textView = textView
        } else {
            scrollView.documentView = textView
        }
        textView.isEditable = false
        textView.isSelectable = true
        textView.isRichText = true
        textView.drawsBackground = true
        textView.backgroundColor = .textBackgroundColor
        textView.textContainerInset = NSSize(width: 14, height: 14)
        textView.isAutomaticLinkDetectionEnabled = false
        textView.isAutomaticSpellingCorrectionEnabled = false
        textView.isContinuousSpellCheckingEnabled = false
        textView.isGrammarCheckingEnabled = false
        textView.isHorizontallyResizable = true
        textView.textContainer?.widthTracksTextView = false
        textView.textContainer?.containerSize = NSSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude)
        self.view = scrollView
    }

    func preparePreviewOfFile(at url: URL, completionHandler handler: @escaping (Error?) -> Void) {
        DispatchQueue.main.async {
            _ = self.view
            let request = UUID()
            self.request = request
            DispatchQueue.global(qos: .userInitiated).async {
                do {
                    let content = try DCTextLoader.read(url)
                    let tokenizer = try DCTokenizer(definition: DCLanguageDefinition.load())
                    let tokens = tokenizer.tokenize(content.text)
                    DispatchQueue.main.async {
                        if self.request == request {
                            let rendered = NSMutableAttributedString(attributedString: DCHighlighter.highlight(content.text, tokens: tokens))
                            if content.truncated {
                                rendered.append(NSAttributedString(string: "\n\nPreview limited to the first 2 MiB. Open the file in an editor to view the rest.", attributes: [
                                    .font: DCTheme.font,
                                    .foregroundColor: NSColor.secondaryLabelColor
                                ]))
                            }
                            self.textView.textStorage?.setAttributedString(rendered)
                        }
                        handler(nil)
                    }
                } catch {
                    DispatchQueue.main.async {
                        if self.request == request { self.renderError("Failed to preview:\n\(error.localizedDescription)") }
                        handler(nil)
                    }
                }
            }
        }
    }

    private func renderError(_ message: String) {
        textView.textStorage?.setAttributedString(NSAttributedString(string: message, attributes: [
            .font: DCTheme.font,
            .foregroundColor: NSColor.systemRed
        ]))
    }
}
