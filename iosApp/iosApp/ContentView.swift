import UIKit
import SwiftUI
import ComposeApp

private struct SwiftTheme {
    let palette: SwiftAmethystPalette

    init(darkMode: Bool) {
        palette = AmethystSwiftThemeBridge.shared.palette(darkMode: darkMode)
    }

    var background: Color { Color(argb: palette.backgroundArgb) }
    var foreground: Color { Color(argb: palette.foregroundArgb) }
    var card: Color { Color(argb: palette.cardArgb) }
    var primary: Color { Color(argb: palette.primaryArgb) }
}

private extension Color {
    init(argb: Int64) {
        let value = UInt64(argb)
        let alpha = Double((value >> 24) & 0xFF) / 255.0
        let red = Double((value >> 16) & 0xFF) / 255.0
        let green = Double((value >> 8) & 0xFF) / 255.0
        let blue = Double(value & 0xFF) / 255.0

        self.init(.sRGB, red: red, green: green, blue: blue, opacity: alpha)
    }
}

private struct HomeTabScreen: View {
    let title: String
    let theme: SwiftTheme

    var body: some View {
        ZStack {
            theme.background
                .ignoresSafeArea()

            Text(title)
                .font(.title2.weight(.semibold))
                .foregroundStyle(theme.foreground)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .navigationTitle(title)
        .toolbarBackground(theme.background, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarBackground(theme.card, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
    }
}

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.colorScheme) private var colorScheme

    private var theme: SwiftTheme {
        SwiftTheme(darkMode: colorScheme == .dark)
    }

    var body: some View {
        TabView {
            NavigationStack {
                HomeTabScreen(
                    title: "Projects",
                    theme: theme
                )
            }
            .tabItem {
                Label("Projects", systemImage: "folder")
            }

            NavigationStack {
                HomeTabScreen(
                    title: "Browser",
                    theme: theme
                )
            }
            .tabItem {
                Label("Browser", systemImage: "globe")
            }

            NavigationStack {
                HomeTabScreen(
                    title: "Settings",
                    theme: theme
                )
            }
            .tabItem {
                Label("Settings", systemImage: "gearshape")
            }
        }
        .tint(theme.primary)
        .background(theme.background)
        .onAppear {
            UIApplication.shared.isIdleTimerDisabled = true
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                UIApplication.shared.isIdleTimerDisabled = true
            }
        }
    }
}
