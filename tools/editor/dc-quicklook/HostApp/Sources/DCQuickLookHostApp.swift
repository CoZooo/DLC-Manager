import SwiftUI
import Foundation

final class HostAppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        DispatchQueue.global(qos: .userInitiated).async {
            self.registerQuickLookExtension()
            DispatchQueue.main.async {
                NSApp.terminate(nil)
            }
        }
    }

    private func registerQuickLookExtension() {
        let fileManager = FileManager.default
        let appURL = Bundle.main.bundleURL

        run("/usr/bin/xattr", ["-dr", "com.apple.quarantine", appURL.path])

        guard
            let pluginsURL = Bundle.main.builtInPlugInsURL,
            let appexURL = try? fileManager.contentsOfDirectory(at: pluginsURL, includingPropertiesForKeys: nil)
                .first(where: { $0.pathExtension == "appex" })
        else {
            return
        }

        run("/usr/bin/pluginkit", ["-a", appexURL.path])

        if let bundleId = extensionBundleIdentifier(at: appexURL) {
            run("/usr/bin/pluginkit", ["-e", "use", "-i", bundleId])
        }

        run("/usr/bin/qlmanage", ["-r"])
        run("/usr/bin/qlmanage", ["-r", "cache"])
    }

    private func extensionBundleIdentifier(at appexURL: URL) -> String? {
        let infoURL = appexURL.appendingPathComponent("Contents/Info.plist")
        guard
            let data = try? Data(contentsOf: infoURL),
            let object = try? PropertyListSerialization.propertyList(from: data, format: nil),
            let dict = object as? [String: Any],
            let bundleId = dict["CFBundleIdentifier"] as? String
        else {
            return nil
        }
        return bundleId
    }

    @discardableResult
    private func run(_ executable: String, _ arguments: [String]) -> Int32? {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: executable)
        process.arguments = arguments
        do {
            try process.run()
            process.waitUntilExit()
            return process.terminationStatus
        } catch {
            return nil
        }
    }
}

@main
struct DCQuickLookHostApp: App {
    @NSApplicationDelegateAdaptor(HostAppDelegate.self) private var appDelegate

    var body: some Scene {
        Settings {
            EmptyView()
        }
    }
}
