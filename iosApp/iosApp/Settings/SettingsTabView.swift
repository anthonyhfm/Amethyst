//
//  SettingsTabView.swift
//  iosApp
//
//  Copyright © 2025 Anthony Hofmeister. All rights reserved.
//

import SwiftUI

struct SettingsTabView: View {
    @Bindable var viewModel: SettingsViewModel

    @Environment(\.amethystTheme) private var theme

    var body: some View {
        NavigationStack {
            Form {
                generalSection
                audioSection
                experimentalSection
            }
            .scrollContentBackground(.hidden)
            .background(theme.background.ignoresSafeArea())
            .navigationTitle("Settings")
        }
    }

    // MARK: - General

    private var generalSection: some View {
        Section("General") {
            Picker("Refresh Rate", selection: $viewModel.performanceFPS) {
                ForEach(viewModel.fpsOptions, id: \.self) { fps in
                    Text("\(fps) Hz").tag(fps)
                }
            }
            .pickerStyle(.menu)
            .tint(theme.foreground)
            .listRowBackground(theme.muted)

            Picker("Gradient Smoothness", selection: $viewModel.gradientSmoothness) {
                ForEach(viewModel.gradientSmoothnessOptions, id: \.value) { option in
                    Text(option.label).tag(option.value)
                }
            }
            .pickerStyle(.menu)
            .tint(theme.foreground)
            .listRowBackground(theme.muted)
        }
    }

    // MARK: - Audio

    private var audioSection: some View {
        Section("Audio") {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("Master Volume")
                    Spacer()
                    Text("\(Int(viewModel.masterVolume * 100))%")
                        .foregroundStyle(.secondary)
                        .monospacedDigit()
                }
                Slider(value: $viewModel.masterVolume, in: 0...1)
                    .tint(theme.primary)
            }
            .listRowBackground(theme.muted)
        }
    }

    // MARK: - Experimental

    private var experimentalSection: some View {
        Section {
            Toggle("Amethyst Gems", isOn: $viewModel.gemsEnabled)
                .tint(theme.primary)
                .listRowBackground(theme.muted)
        } header: {
            Text("Experimental")
        } footer: {
            Text("These features are in development and may be unstable.")
        }
    }
}
