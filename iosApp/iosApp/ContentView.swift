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

            // Settings tab (work in progress)
            NavigationStack {
                ZStack {
                    theme.background.ignoresSafeArea()
                    VStack(spacing: 12) {
                        Image(systemName: "gearshape")
                            .font(.largeTitle)
                            .foregroundStyle(.secondary)
                        Text("Settings")
                            .font(.headline)
                        Text("Coming soon.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .navigationTitle("Settings")
                }
            }
            .tabItem {
                Label("Settings", systemImage: "gearshape")
            }
        }
        .tint(theme.primary)
        .amethystThemed()
    }
}

