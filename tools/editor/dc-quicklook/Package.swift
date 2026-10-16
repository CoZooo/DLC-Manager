// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "DCQuickLook",
    platforms: [.macOS(.v13)],
    targets: [
        .target(name: "DCQuickLookCore", path: "Shared", resources: [.copy("Resources/language-definition.json")]),
        .testTarget(name: "DCQuickLookCoreTests", dependencies: ["DCQuickLookCore"], path: "Tests")
    ]
)
