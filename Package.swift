// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "IonicChromecast",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "IonicChromecast",
            targets: ["IonicChromecastPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .target(
            name: "IonicChromecastPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/IonicChromecastPlugin"),
        .testTarget(
            name: "IonicChromecastPluginTests",
            dependencies: ["IonicChromecastPlugin"],
            path: "ios/Tests/IonicChromecastPluginTests")
    ]
)