//
//  SplashScreenView.swift
//  iosApp
//

import SwiftUI

struct AmethystStrokeLogoShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let scale = min(rect.width / 260.0, rect.height / 260.0)
        let dx = rect.midX - 276.68 * scale
        let dy = rect.midY - 169.2 * scale

        func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: x * scale + dx, y: y * scale + dy)
        }

        // 1. Circle Stroke Path
        path.move(to: pt(187.265, 204.121))
        path.addCurve(to: pt(211.731, 228.587), control1: pt(200.778, 204.121), control2: pt(211.731, 215.075))
        path.addCurve(to: pt(187.265, 253.053), control1: pt(211.731, 242.099), control2: pt(200.778, 253.053))
        path.addCurve(to: pt(162.800, 228.587), control1: pt(173.753, 253.053), control2: pt(162.800, 242.099))
        path.addCurve(to: pt(187.265, 204.121), control1: pt(162.800, 215.075), control2: pt(173.753, 204.121))
        path.closeSubpath()

        // 2. Pill 1 Stroke Path (Top diagonal)
        path.move(to: pt(214.529, 148.090))
        path.addLine(to: pt(253.044, 93.0863))
        path.addCurve(to: pt(287.118, 87.0781), control1: pt(260.794, 82.0181), control2: pt(276.049, 79.3280))
        path.addCurve(to: pt(293.126, 121.152), control1: pt(298.186, 94.8283), control2: pt(300.876, 110.084))
        path.addLine(to: pt(254.612, 176.156))
        path.addCurve(to: pt(220.538, 182.165), control1: pt(246.862, 187.225), control2: pt(231.606, 189.915))
        path.addCurve(to: pt(214.529, 148.090), control1: pt(209.469, 174.414), control2: pt(206.779, 159.159))
        path.closeSubpath()

        // 3. Pill 2 Stroke Path (Bottom diagonal)
        path.move(to: pt(343.573, 159.493))
        path.addLine(to: pt(382.087, 214.497))
        path.addCurve(to: pt(376.079, 248.572), control1: pt(389.837, 225.566), control2: pt(387.147, 240.821))
        path.addCurve(to: pt(342.005, 242.563), control1: pt(365.010, 256.322), control2: pt(349.755, 253.632))
        path.addLine(to: pt(303.490, 187.559))
        path.addCurve(to: pt(309.499, 153.485), control1: pt(295.740, 176.491), control2: pt(298.430, 161.235))
        path.addCurve(to: pt(343.573, 159.493), control1: pt(320.567, 145.735), control2: pt(335.823, 148.425))
        path.closeSubpath()

        return path
    }
}

struct AmethystFilledLogoShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let scale = min(rect.width / 260.0, rect.height / 260.0)
        let dx = rect.midX - 276.68 * scale
        let dy = rect.midY - 169.2 * scale

        func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: x * scale + dx, y: y * scale + dy)
        }

        // Filled Circle
        path.move(to: pt(187.078, 258.909))
        path.addCurve(to: pt(217.593, 228.394), control1: pt(203.931, 258.909), control2: pt(217.593, 245.247))
        path.addCurve(to: pt(187.078, 197.878), control1: pt(217.593, 211.541), control2: pt(203.931, 197.878))
        path.addCurve(to: pt(156.562, 228.394), control1: pt(170.225, 197.878), control2: pt(156.562, 211.541))
        path.addCurve(to: pt(187.078, 258.909), control1: pt(156.562, 245.247), control2: pt(170.225, 258.909))
        path.closeSubpath()

        // Filled Pill 1
        path.move(to: pt(248.039, 89.2269))
        path.addLine(to: pt(209.463, 144.319))
        path.addCurve(to: pt(216.957, 186.819), control1: pt(199.796, 158.125), control2: pt(203.151, 177.152))
        path.addCurve(to: pt(259.456, 179.325), control1: pt(230.762, 196.485), control2: pt(249.790, 193.130))
        path.addLine(to: pt(298.032, 124.232))
        path.addCurve(to: pt(290.538, 81.7331), control1: pt(307.699, 110.427), control2: pt(304.344, 91.3996))
        path.addCurve(to: pt(248.039, 89.2269), control1: pt(276.733, 72.0665), control2: pt(257.706, 75.4216))
        path.closeSubpath()

        // Filled Pill 2
        path.move(to: pt(387.137, 210.833))
        path.addLine(to: pt(348.561, 155.741))
        path.addCurve(to: pt(306.062, 148.247), control1: pt(338.895, 141.935), control2: pt(319.867, 138.580))
        path.addCurve(to: pt(298.568, 190.746), control1: pt(292.257, 157.913), control2: pt(288.901, 176.941))
        path.addLine(to: pt(337.144, 245.839))
        path.addCurve(to: pt(379.644, 253.333), control1: pt(346.811, 259.644), control2: pt(365.838, 262.999))
        path.addCurve(to: pt(387.137, 210.833), control1: pt(393.449, 243.666), control2: pt(396.804, 224.638))
        path.closeSubpath()

        return path
    }
}

struct SplashScreenView: View {
    var onComplete: () -> Void

    @State private var strokeProgress: CGFloat = 0.0
    @State private var strokeOpacity: Double = 1.0
    @State private var fillOpacity: Double = 0.0
    @State private var scale: CGFloat = 1.0
    @State private var splashOpacity: Double = 1.0

    private let logoGradient = LinearGradient(
        gradient: Gradient(colors: [
            Color(red: 0.694, green: 0.0, blue: 0.925),  // #B100EC
            Color(red: 0.941, green: 0.380, blue: 0.961),  // #F061F5
            Color(red: 1.0, green: 0.0, blue: 0.0)        // #FF0000
        ]),
        startPoint: .bottomLeading,
        endPoint: .topTrailing
    )

    var body: some View {
        ZStack {
            Color(red: 14/255.0, green: 11/255.0, blue: 22/255.0)
                .ignoresSafeArea()

            ZStack {
                // Stroke outline animation
                AmethystStrokeLogoShape()
                    .trim(from: 0, to: strokeProgress)
                    .stroke(
                        Color(red: 0.659, green: 0.333, blue: 0.969), // #A855F7
                        style: StrokeStyle(lineWidth: 3.5, lineCap: .round, lineJoin: .round)
                    )
                    .opacity(strokeOpacity)

                // Colored gradient fill fade-in
                AmethystFilledLogoShape()
                    .fill(logoGradient)
                    .opacity(fillOpacity)
            }
            .frame(width: 185, height: 185)
            .scaleEffect(scale)
        }
        .opacity(splashOpacity)
        .onAppear {
            runAnimationSequence()
        }
    }

    private func runAnimationSequence() {
        // Step 1: Wait 180ms for iOS system app icon zoom to complete, then trace strokes (0.18s to 1.03s)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
            withAnimation(.timingCurve(0.2, 0.0, 0.2, 1.0, duration: 0.85)) {
                strokeProgress = 1.0
            }
        }

        // Step 2: Crossfade stroke outline to filled gradient logo (starts at 0.85s, duration 0.30s -> ends at 1.15s)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.85) {
            withAnimation(.timingCurve(0.2, 0.0, 0.2, 1.0, duration: 0.30)) {
                fillOpacity = 1.0
                strokeOpacity = 0.0
            }
        }

        // Step 3: Hold 100ms, then scale up logo and crisp fade out splash into Home UI (starts at 1.25s, duration 0.36s)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.25) {
            withAnimation(.easeOut(duration: 0.36)) {
                scale = 1.75
                splashOpacity = 0.0
            }
        }

        // Step 4: Finish splash screen (at 1.61s)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.61) {
            onComplete()
        }
    }
}
