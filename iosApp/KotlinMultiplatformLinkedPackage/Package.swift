// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "KotlinMultiplatformLinkedPackage",
  platforms: [
    .iOS("15.3")
  ],
  products: [
    .library(
      name: "KotlinMultiplatformLinkedPackage",
      type: .none,
      targets: ["KotlinMultiplatformLinkedPackage"]
    )
  ],
  dependencies: [
    .package(
      url: "https://github.com/HealsCodes/vorbis-swift.git",
      exact: "1.3.7"
    )
  ],
  targets: [
    .target(
      name: "KotlinMultiplatformLinkedPackage",
      dependencies: [
        .product(
          name: "LibVorbis",
          package: "vorbis-swift",
          condition: .when(platforms: [.iOS])
        )
      ]
    )
  ]
)
