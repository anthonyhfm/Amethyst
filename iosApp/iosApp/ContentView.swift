//
//  ContentView.swift
//  iosApp
//
//  Created by Anthony Hofmeister on 30.10.25.
//  Copyright © 2025 Anthony Hofmeister. All rights reserved.
//

import UIKit
import SwiftUI
import ComposeApp

// MARK: - Workspace host (KMP Compose)

private struct WorkspaceView: UIViewControllerRepresentable {
    let darkMode: Bool
    let onBack: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.WorkspaceViewController(darkMode: darkMode, onBack: onBack)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - Root content

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.colorScheme)  private var colorScheme

    @State private var viewModel = HomeViewModel()
    @State private var settingsViewModel = SettingsViewModel()
    @State private var showSettingsSheet = false

    private var theme: AmethystTheme {
        AmethystTheme(darkMode: colorScheme == .dark)
    }

    var body: some View {
        Group {
            if viewModel.isWorkspaceOpen {
                WorkspaceView(darkMode: colorScheme == .dark) {
                    viewModel.workspaceClosed()
                }
                .ignoresSafeArea()
                .onAppear {
                    IosWorkspaceBridge.shared.onShowSettings = {
                        showSettingsSheet = true
                    }
                    IosWorkspaceBridge.shared.createLiquidGlassEffect = {
                        if #available(iOS 26.0, *) {
                            let effect = UIGlassEffect(style: .regular)
                            effect.isInteractive = true
                            return effect
                        } else {
                            return UIBlurEffect(style: .systemThinMaterial)
                        }
                    }
                    if #available(iOS 26.0, *) {
                        IosWorkspaceBridge.shared.createLiquidGlassContainerEffect = {
                            let effect = UIGlassContainerEffect()
                            effect.spacing = 10
                            return effect
                        }
                    } else {
                        IosWorkspaceBridge.shared.createLiquidGlassContainerEffect = nil
                    }
                    IosWorkspaceBridge.shared.createLiquidGlassButtonConfiguration = {
                        if #available(iOS 26.0, *) {
                            var configuration = UIButton.Configuration.glass()
                            configuration.cornerStyle = .capsule
                            configuration.indicator = .none
                            return configuration._bridgeToObjectiveC()
                        } else {
                            var configuration = UIButton.Configuration.bordered()
                            configuration.cornerStyle = .capsule
                            return configuration._bridgeToObjectiveC()
                        }
                    }
                }
                .sheet(isPresented: $showSettingsSheet) {
                    SettingsTabView(viewModel: settingsViewModel, showsCloseButton: true)
                }
            } else {
                homeTabView
            }
        }
        .onAppear {
            UIApplication.shared.isIdleTimerDisabled = true
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                UIApplication.shared.isIdleTimerDisabled = true
            }
        }
    }

    // MARK: - Home tab bar

    private var homeTabView: some View {
        TabView {
            // Projects tab
            ProjectsTabView(viewModel: viewModel)
                .tabItem {
                    Label("Projects", systemImage: "folder")
                }

            // Browser tab (work in progress)
            NavigationStack {
                ZStack {
                    theme.background.ignoresSafeArea()
                    VStack(spacing: 12) {
                        Image(systemName: "globe")
                            .font(.largeTitle)
                            .foregroundStyle(.secondary)
                        Text("Work in Progress")
                            .font(.headline)
                        Text("Nothing to see here yet.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .navigationTitle("Browser")
                }
            }
            .tabItem {
                Label("Browser", systemImage: "globe")
            }

            // Settings tab
            SettingsTabView(viewModel: settingsViewModel)
                .tabItem {
                    Label("Settings", systemImage: "gearshape")
                }
        }
        .tint(theme.primary)
        .amethystThemed()
    }
}
