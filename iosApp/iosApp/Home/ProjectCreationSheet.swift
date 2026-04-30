//
//  ProjectCreationSheet.swift
//  iosApp
//
//  Created by Copilot
//  Copyright © 2025 Anthony Hofmeister. All rights reserved.
//

import SwiftUI
import ComposeApp

/// Sheet for creating a new project or editing an existing one.
///
/// - Pass `editPath = nil` for creation mode.
/// - Pass `editPath = "/path/to/file.ame"` for edit mode.
struct ProjectCreationSheet: View {
    let editPath: String?
    let viewModel: HomeViewModel

    @Environment(\.dismiss) private var dismiss
    @Environment(\.amethystTheme) private var theme

    @State private var name: String = ""
    @State private var author: String = ""
    @State private var nameErrorVisible = false

    private var isEditing: Bool { editPath != nil }

    var body: some View {
        NavigationStack {
            Form {
                // Project name
                Section {
                    TextField("My next performance", text: $name)
                        .autocorrectionDisabled()
                        .onChange(of: name) { _, _ in
                            if nameErrorVisible && !name.trimmingCharacters(in: .whitespaces).isEmpty {
                                nameErrorVisible = false
                            }
                        }
                } header: {
                    Text("Project Name")
                } footer: {
                    if nameErrorVisible {
                        Label("Please enter a project name.", systemImage: "exclamationmark.circle.fill")
                            .foregroundStyle(.red)
                            .font(.footnote)
                    } else {
                        Text("Shown in your workspace and recent projects.")
                    }
                }

                // Author
                Section {
                    TextField("Your name", text: $author)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.words)
                } header: {
                    Text("Author")
                } footer: {
                    Text("Saved as your default. Leave blank to fall back to \"Unknown Author\".")
                }
            }
            .navigationTitle(isEditing ? "Edit Project" : "New Project")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isEditing ? "Save" : "Create") {
                        submit()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
        .onAppear {
            if isEditing {
                // Pre-fill fields are set via task below
            } else {
                author = viewModel.localAuthor()
            }
        }
        .task(id: editPath) {
            guard let path = editPath else { return }
            if let details = try? await HomeRepository.shared.loadProjectDetails(path: path) {
                name   = details.name
                author = details.author
            }
        }
    }

    // MARK: - Submit

    private func submit() {
        let trimmedName = name.trimmingCharacters(in: .whitespaces)
        guard !trimmedName.isEmpty else {
            nameErrorVisible = true
            return
        }

        dismiss()

        if let path = editPath {
            viewModel.updateProject(path: path, name: trimmedName, author: author)
        } else {
            viewModel.createProject(name: trimmedName, author: author)
        }
    }

}
