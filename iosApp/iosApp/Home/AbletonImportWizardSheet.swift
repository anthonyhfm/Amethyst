//
//  AbletonImportWizardSheet.swift
//  iosApp
//
//  Created by Copilot
//  Copyright © 2025 Anthony Hofmeister. All rights reserved.
//

import SwiftUI
import ComposeApp
import UniformTypeIdentifiers

/// Presented when the user picks an Ableton `.als` or Ableton-format `.zip`.
/// Lets the user optionally attach a custom palette file and an Apollo `.approj`
/// before starting the conversion.
struct AbletonImportWizardSheet: View {
    let importPath: String
    let viewModel: HomeViewModel

    @Environment(\.dismiss) private var dismiss
    @Environment(\.amethystTheme) private var theme

    private enum ActivePicker: Identifiable {
        case palette
        case apollo

        var id: Self { self }
    }

    @State private var palettePath: String = ""
    @State private var apolloPath: String = ""
    @State private var activePicker: ActivePicker? = nil

    private var importName: String {
        (importPath as NSString).lastPathComponent
    }
    private var paletteName: String {
        palettePath.isEmpty ? "" : (palettePath as NSString).lastPathComponent
    }
    private var apolloName: String {
        apolloPath.isEmpty ? "" : (apolloPath as NSString).lastPathComponent
    }

    var body: some View {
        NavigationStack {
            Form {
                // Import source (read-only)
                Section("Import Source") {
                    LabeledContent("File") {
                        Text(importName)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                            .truncationMode(.middle)
                    }
                }

                // Custom palette (optional)
                Section {
                    if palettePath.isEmpty {
                        Text("No custom palette selected")
                            .foregroundStyle(.secondary)
                        Text("Optional. The default Novation palette will be used.")
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                    } else {
                        LabeledContent("File") {
                            Text(paletteName)
                                .foregroundStyle(.secondary)
                            .lineLimit(1)
                            .truncationMode(.middle)
                        }
                        Button("Change…") {
                            activePicker = .palette
                        }
                        Button("Remove", role: .destructive) {
                            palettePath = ""
                        }
                    }

                    if palettePath.isEmpty {
                        Button("Select Palette…") {
                            activePicker = .palette
                        }
                    }
                } header: {
                    Text("Custom Palette")
                } footer: {
                    Text("Optional. Overrides the default Novation colour palette.")
                }

                // Apollo project (optional)
                Section {
                    if apolloPath.isEmpty {
                        Text("No Apollo project selected")
                            .foregroundStyle(.secondary)
                        Text("Optional. Lights will be sourced from Ableton MIDI tracks.")
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                    } else {
                        LabeledContent("File") {
                            Text(apolloName)
                                .foregroundStyle(.secondary)
                            .lineLimit(1)
                            .truncationMode(.middle)
                        }
                        Button("Change…") {
                            activePicker = .apollo
                        }
                        Button("Remove", role: .destructive) {
                            apolloPath = ""
                        }
                    }

                    if apolloPath.isEmpty {
                        Button("Select Apollo Project…") {
                            activePicker = .apollo
                        }
                    }
                } header: {
                    Text("Apollo Lights Project (.approj)")
                } footer: {
                    Text("Optional. If set, the lights chain will be taken from the Apollo project instead.")
                }
            }
            .scrollContentBackground(.hidden)
            .background(theme.background.ignoresSafeArea())
            .navigationTitle("Ableton Import Wizard")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Convert") {
                        startConversion()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
        .tint(theme.glassForeground)
        .fileImporter(
            isPresented: Binding(
                get: { activePicker != nil },
                set: { if !$0 { activePicker = nil } }
            ),
            allowedContentTypes: activePicker == .apollo
                ? [UTType(filenameExtension: "approj") ?? .data, .data, .item]
                : [UTType(filenameExtension: "palette") ?? .data, .item, .data, .plainText]
        ) { result in
            guard case .success(let url) = result else { return }
            let pickedPath = indexPickedFile(url: url)
            if activePicker == .apollo {
                apolloPath = pickedPath
            } else {
                palettePath = pickedPath
            }
        }
    }

    // MARK: - Actions

    private func startConversion() {
        dismiss()
        viewModel.importAbleton(
            path: importPath,
            palettePath: palettePath.isEmpty ? nil : palettePath,
            apolloPath:  apolloPath.isEmpty  ? nil : apolloPath
        )
    }

    private func indexPickedFile(url: URL) -> String {
        guard url.startAccessingSecurityScopedResource() else { return "" }
        defer { url.stopAccessingSecurityScopedResource() }
        guard let data = try? Data(contentsOf: url) else { return "" }
        return HomeSwiftBridge.shared.indexFile(
            data: data,
            filename: url.lastPathComponent
        )
    }
}
