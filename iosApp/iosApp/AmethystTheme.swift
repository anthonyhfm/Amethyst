//
//  AmethystTheme.swift
//  iosApp
//
//  Created by Copilot
//  Copyright © 2025 Anthony Hofmeister. All rights reserved.
//

import SwiftUI
import ComposeApp

// MARK: - Color palette

struct AmethystTheme {
    let background: Color
    let foreground: Color
    let card: Color
    let cardForeground: Color
    let muted: Color
    let mutedForeground: Color
    let primary: Color
    let primaryForeground: Color
    let secondary: Color
    let secondaryForeground: Color
    let border: Color
    let destructive: Color

    init(darkMode: Bool) {
        let palette = AmethystSwiftThemeBridge.shared.palette(darkMode: darkMode)
        background      = Color(argb: palette.backgroundArgb)
        foreground      = Color(argb: palette.foregroundArgb)
        card            = Color(argb: palette.cardArgb)
        cardForeground  = Color(argb: palette.cardForegroundArgb)
        muted           = Color(argb: palette.mutedArgb)
        mutedForeground = Color(argb: palette.mutedForegroundArgb)
        primary         = Color(argb: palette.primaryArgb)
        primaryForeground = Color(argb: palette.primaryForegroundArgb)
        secondary       = Color(argb: palette.secondaryArgb)
        secondaryForeground = Color(argb: palette.secondaryForegroundArgb)
        border          = Color(argb: palette.borderArgb)
        destructive     = Color(argb: palette.destructiveArgb)
    }
}

// MARK: - Color(argb:) initialiser

extension Color {
    init(argb: Int64) {
        let value = UInt64(bitPattern: argb)
        let alpha = Double((value >> 24) & 0xFF) / 255.0
        let red   = Double((value >> 16) & 0xFF) / 255.0
        let green = Double((value >>  8) & 0xFF) / 255.0
        let blue  = Double( value        & 0xFF) / 255.0
        self.init(.sRGB, red: red, green: green, blue: blue, opacity: alpha)
    }
}

// MARK: - Environment key

private struct AmethystThemeKey: EnvironmentKey {
    static let defaultValue = AmethystTheme(darkMode: true)
}

extension EnvironmentValues {
    var amethystTheme: AmethystTheme {
        get { self[AmethystThemeKey.self] }
        set { self[AmethystThemeKey.self] = newValue }
    }
}

// MARK: - View modifier

struct AmethystThemed: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        content
            .environment(\.amethystTheme, AmethystTheme(darkMode: colorScheme == .dark))
    }
}

extension View {
    func amethystThemed() -> some View {
        modifier(AmethystThemed())
    }
}
