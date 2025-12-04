// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "Navi",
    platforms: [
        .iOS(.v16)
    ],
    products: [
        .library(
            name: "Navi",
            targets: ["Navi"])
    ],
    dependencies: [
        // Mapbox SDK for navigation and maps
        .package(url: "https://github.com/mapbox/mapbox-maps-ios.git", from: "11.0.0"),
        
        // Networking
        .package(url: "https://github.com/Alamofire/Alamofire.git", from: "5.8.0"),
        
        // Local database
        .package(url: "https://github.com/realm/realm-swift.git", from: "10.45.0"),
        
        // Image loading and caching
        .package(url: "https://github.com/onevcat/Kingfisher.git", from: "7.10.0"),
        
        // Localization
        .package(url: "https://github.com/SwiftGen/SwiftGen.git", from: "6.6.0")
    ],
    targets: [
        .target(
            name: "Navi",
            dependencies: [
                .product(name: "MapboxMaps", package: "mapbox-maps-ios"),
                "Alamofire",
                .product(name: "RealmSwift", package: "realm-swift"),
                "Kingfisher"
            ],
            path: "Navi"
        ),
        .testTarget(
            name: "NaviTests",
            dependencies: ["Navi"],
            path: "NaviTests"
        )
    ]
)
