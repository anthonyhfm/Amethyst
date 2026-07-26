//
//  LoadingScreenView.swift
//  iosApp
//
//  Created by Anthony Hofmeister on 25.07.26.
//  Copyright © 2026 Anthony Hofmeister. All rights reserved.
//

import SwiftUI

struct LoadingScreenView: View {
    let progress: Double
    let title: String?
    let statusText: String
    let detailText: String?

    @Environment(\.amethystTheme) private var theme

    @State private var animatedProgress: Double = 0.0

    private var displayTitle: String {
        guard let title, !title.trimmingCharacters(in: .whitespaces).isEmpty else {
            return "AMETHYST"
        }
        return title
    }

    var body: some View {
        ZStack {
            theme.background
                .ignoresSafeArea()

            VStack {
                Spacer()

                Text(displayTitle)
                    .font(.system(size: 24, weight: .bold))
                    .foregroundStyle(theme.foreground)

                Spacer().frame(height: 28)

                AmethystLoadingLogoView(progress: animatedProgress, width: 150)

                Spacer().frame(height: 24)

                AnimatedPercentageText(progress: animatedProgress)

                Spacer().frame(height: 14)

                AnimatedProgressBar(progress: animatedProgress)

                Spacer().frame(height: 20)

                // Dynamic Status & Detail Typography
                VStack(spacing: 4) {
                    Text(statusText)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(theme.foreground)
                        .multilineTextAlignment(.center)
                        .lineLimit(1)
                        .truncationMode(.tail)
                        .frame(maxWidth: .infinity)

                    if let detailText, !detailText.trimmingCharacters(in: .whitespaces).isEmpty {
                        Text(detailText)
                            .font(.system(size: 12, weight: .regular))
                            .foregroundStyle(theme.mutedForeground)
                            .multilineTextAlignment(.center)
                            .lineLimit(1)
                            .truncationMode(.tail)
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.horizontal, 32)

                Spacer()
            }
        }
        .onAppear {
            withAnimation(.spring(response: 0.6, dampingFraction: 0.95)) {
                animatedProgress = max(0.0, min(1.0, progress))
            }
        }
        .onChange(of: progress) { _, newValue in
            withAnimation(.spring(response: 0.6, dampingFraction: 0.95)) {
                animatedProgress = max(0.0, min(1.0, newValue))
            }
        }
    }
}

// MARK: - Animatable Percentage Text

private struct AnimatedPercentageText: View, Animatable {
    var progress: Double

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    @Environment(\.amethystTheme) private var theme

    var body: some View {
        Text("\(Int(max(0.0, min(1.0, progress)) * 100))%")
            .font(.system(size: 32, weight: .semibold))
            .foregroundStyle(theme.foreground)
            .tracking(-0.5)
    }
}

// MARK: - Animatable Progress Bar

private struct AnimatedProgressBar: View, Animatable {
    var progress: Double

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    var body: some View {
        let clamped = max(0.0, min(1.0, progress))
        let width = max(280.0 * max(clamped, 0.02), 5.6)

        ZStack(alignment: .leading) {
            RoundedRectangle(cornerRadius: 2)
                .fill(Color(red: 39/255, green: 39/255, blue: 42/255))
                .frame(width: 280, height: 4)

            RoundedRectangle(cornerRadius: 2)
                .fill(
                    LinearGradient(
                        colors: [
                            Color(red: 139/255, green: 92/255, blue: 246/255),
                            Color(red: 192/255, green: 132/255, blue: 252/255)
                        ],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
                .frame(width: width, height: 4)
        }
    }
}
