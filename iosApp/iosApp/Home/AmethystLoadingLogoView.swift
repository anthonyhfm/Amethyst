//
//  AmethystLoadingLogoView.swift
//  iosApp
//
//  Created by Anthony Hofmeister on 25.07.26.
//  Copyright © 2026 Anthony Hofmeister. All rights reserved.
//

import SwiftUI

// MARK: - SVG Path Parser

private struct SVGPathParser {
    static func parse(_ svgString: String) -> Path {
        var path = Path()
        let scanner = Scanner(string: svgString)
        scanner.charactersToBeSkipped = CharacterSet(charactersIn: " ,\t\n\r")

        while !scanner.isAtEnd {
            guard let command = scanner.scanCharacter() else { break }
            switch command {
            case "M":
                if let x = scanner.scanDouble(), let y = scanner.scanDouble() {
                    path.move(to: CGPoint(x: x, y: y))
                }
            case "L":
                if let x = scanner.scanDouble(), let y = scanner.scanDouble() {
                    path.addLine(to: CGPoint(x: x, y: y))
                }
            case "C":
                if let x1 = scanner.scanDouble(), let y1 = scanner.scanDouble(),
                   let x2 = scanner.scanDouble(), let y2 = scanner.scanDouble(),
                   let x = scanner.scanDouble(), let y = scanner.scanDouble() {
                    path.addCurve(to: CGPoint(x: x, y: y),
                                  control1: CGPoint(x: x1, y: y1),
                                  control2: CGPoint(x: x2, y: y2))
                }
            case "z", "Z":
                path.closeSubpath()
            default:
                break
            }
        }
        return path
    }
}

// MARK: - Animated Logo View

struct AmethystLoadingLogoView: View, Animatable {
    var progress: Double
    var width: CGFloat = 150

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    private static let viewportWidth: CGFloat = 623.0
    private static let viewportHeight: CGFloat = 482.0

    private static let logoPath: Path = {
        var p = Path()
        p.addPath(SVGPathParser.parse("M80.47,326.83C121.6,326.83,154.94,360.18,154.94,401.3C154.94,442.43,121.6,475.77,80.47,475.77C39.34,475.77,6,442.43,6,401.3C6,360.18,39.34,326.83,80.47,326.83z"))
        p.addPath(SVGPathParser.parse("M144.41,183.04L246.14,37.76C269.73,4.07,316.16,-4.12,349.85,19.47C383.54,43.06,391.73,89.5,368.14,123.19L266.42,268.47C242.83,302.16,196.39,310.35,162.7,286.76C129.01,263.17,120.82,216.73,144.41,183.04z"))
        p.addPath(SVGPathParser.parse("M501.39,213.16L603.11,358.44C626.7,392.13,618.51,438.56,584.82,462.15C551.13,485.74,504.7,477.56,481.11,443.87L379.38,298.59C355.79,264.9,363.98,218.46,397.67,194.87C431.36,171.28,477.8,179.47,501.39,213.16z"))
        return p
    }()

    var body: some View {
        let height = width / (Self.viewportWidth / Self.viewportHeight)
        let currentProgress = max(0.0, min(1.0, progress))

        TimelineView(.animation) { timeline in
            let time = timeline.date.timeIntervalSinceReferenceDate
            let wavePhase = (time.truncatingRemainder(dividingBy: 2.8) / 2.8) * (2 * .pi)

            Canvas { context, size in
                let scale = min(size.width / Self.viewportWidth, size.height / Self.viewportHeight)
                let dx = (size.width - Self.viewportWidth * scale) / 2.0
                let dy = (size.height - Self.viewportHeight * scale) / 2.0

                var transform = CGAffineTransform.identity
                    .translatedBy(x: dx, y: dy)
                    .scaledBy(x: scale, y: scale)

                guard let scaledCGPath = Self.logoPath.cgPath.copy(using: &transform) else { return }
                let scaledPath = Path(scaledCGPath)

                let scaledWidth = Self.viewportWidth * scale
                let scaledHeight = Self.viewportHeight * scale

                // 1. Base Fill & Stroke
                context.fill(scaledPath, with: .color(Color(red: 24/255, green: 24/255, blue: 27/255)))
                context.stroke(scaledPath, with: .color(Color(red: 39/255, green: 39/255, blue: 42/255)), lineWidth: 1.5 * scale)

                // 2. Liquid Wave Fill
                if currentProgress > 0.001 {
                    let liquidHeight = scaledHeight * currentProgress
                    let liquidTopY = (scaledHeight - liquidHeight) + dy
                    let waveAmp = 1.5 * scale

                    var liquidPath = Path()
                    liquidPath.move(to: CGPoint(x: dx - 10, y: (scaledHeight + 10) + dy))
                    liquidPath.addLine(to: CGPoint(x: dx - 10, y: liquidTopY))

                    var x = dx - 10
                    let step: CGFloat = 4.0
                    while x <= (scaledWidth + 10) + dx {
                        let relX = x - dx
                        let y = liquidTopY + sin((relX * 0.018) + wavePhase) * waveAmp
                        liquidPath.addLine(to: CGPoint(x: x, y: y))
                        x += step
                    }

                    liquidPath.addLine(to: CGPoint(x: (scaledWidth + 10) + dx, y: (scaledHeight + 10) + dy))
                    liquidPath.closeSubpath()

                    context.clip(to: scaledPath)

                    let gradient = Gradient(colors: [
                        Color(red: 192/255, green: 132/255, blue: 252/255),
                        Color(red: 168/255, green: 85/255, blue: 247/255),
                        Color(red: 147/255, green: 51/255, blue: 234/255)
                    ])

                    context.fill(
                        liquidPath,
                        with: .linearGradient(
                            gradient,
                            startPoint: CGPoint(x: size.width / 2, y: liquidTopY - waveAmp),
                            endPoint: CGPoint(x: size.width / 2, y: scaledHeight + dy)
                        )
                    )

                    // Surface Highlight
                    var crestLine = Path()
                    crestLine.move(to: CGPoint(x: dx - 10, y: liquidTopY))
                    x = dx - 10
                    while x <= (scaledWidth + 10) + dx {
                        let relX = x - dx
                        let y = liquidTopY + sin((relX * 0.018) + wavePhase) * waveAmp
                        crestLine.addLine(to: CGPoint(x: x, y: y))
                        x += step
                    }

                    context.stroke(
                        crestLine,
                        with: .color(Color(red: 233/255, green: 213/255, blue: 255/255)),
                        style: StrokeStyle(lineWidth: 1.5 * scale, lineCap: .round)
                    )
                }
            }
            .frame(width: width, height: height)
        }
    }
}
