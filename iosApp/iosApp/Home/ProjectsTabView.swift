//
//  ProjectsTabView.swift
//  iosApp
//
//  Created by Copilot
//  Copyright © 2025 Anthony Hofmeister. All rights reserved.
//

import SwiftUI
import ComposeApp
import UniformTypeIdentifiers

/// Root view for the "Projects" tab.
///
/// Displays the recent-projects list with header actions and routes to
/// the creation sheet, edit sheet, and Ableton import wizard via the
/// shared `HomeViewModel`.
struct ProjectsTabView: View {
    @Bindable var viewModel: HomeViewModel

    @Environment(\.amethystTheme) private var theme

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.recentProjects.isEmpty {
                    emptyState
                } else {
                    recentList
                }
            }
            // Loading overlay
            .overlay {
                if viewModel.isLoading {
                    loadingOverlay
                }
            }
            .navigationTitle("Recent Projects")
            .navigationBarTitleDisplayMode(.large)
            .background(theme.background)
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    // Open existing project
                    Button {
                        viewModel.showingFilePicker = true
                    } label: {
                        Image(systemName: "folder.badge.plus")
                    }
                    .accessibilityLabel("Open Project")

                    // Create new project
                    Button {
                        viewModel.activeSheet = .createProject
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("New Project")
                }
            }
        }
        // ── File picker ────────────────────────────────────────────────
        .fileImporter(
            isPresented: $viewModel.showingFilePicker,
            allowedContentTypes: [
                UTType(filenameExtension: "ame")    ?? .data,
                UTType(filenameExtension: "als")    ?? .data,
                UTType(filenameExtension: "zip")    ?? .data,
                UTType(filenameExtension: "approj") ?? .data,
                .zip,
                .data,
            ]
        ) { result in
            switch result {
            case .success(let url):
                viewModel.openFile(url: url)
            case .failure(let error):
                viewModel.errorMessage = error.localizedDescription
            }
        }
        // ── Sheet routing ──────────────────────────────────────────────
        .sheet(item: $viewModel.activeSheet) { sheet in
            switch sheet {
            case .createProject:
                ProjectCreationSheet(editPath: nil, viewModel: viewModel)

            case .editProject(let path):
                ProjectCreationSheet(editPath: path, viewModel: viewModel)

            case .abletonWizard(let path):
                AbletonImportWizardSheet(importPath: path, viewModel: viewModel)
            }
        }
        // ── Error alert ────────────────────────────────────────────────
        .alert("Something went wrong", isPresented: errorBinding) {
            Button("OK", role: .cancel) { viewModel.errorMessage = nil }
        } message: {
            if let msg = viewModel.errorMessage {
                Text(msg)
            }
        }
    }

    // MARK: - Subviews

    private var recentList: some View {
        List {
            ForEach(viewModel.recentProjects, id: \.path) { project in
                RecentProjectRow(
                    project: project,
                    onOpen:   { viewModel.openRecent(project) },
                    onEdit:   { viewModel.activeSheet = .editProject(path: project.path) },
                    onRemove: { viewModel.removeRecent(path: project.path) }
                )
            }
            .onDelete { indexSet in
                indexSet.forEach { i in
                    viewModel.removeRecent(path: viewModel.recentProjects[i].path)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(theme.background)
    }

    private var emptyState: some View {
        ContentUnavailableView {
            Label("No Recent Projects", systemImage: "folder")
        } description: {
            Text("Open an existing workspace or create a new project to get started.")
        } actions: {
            Button(action: { viewModel.activeSheet = .createProject }) {
                Label("New Project", systemImage: "plus")
            }
            .buttonStyle(.borderedProminent)

            Button(action: { viewModel.showingFilePicker = true }) {
                Label("Open File", systemImage: "folder.badge.plus")
            }
            .buttonStyle(.bordered)
        }
    }

    private var loadingOverlay: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea()

            VStack(spacing: 16) {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(theme.primary)
                    .scaleEffect(1.2)

                Text(viewModel.loadingText)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(theme.foreground)
            }
            .padding(24)
            .background(theme.card)
            .clipShape(.rect(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(theme.border, lineWidth: 1)
            )
        }
    }

    // MARK: - Helpers

    private var errorBinding: Binding<Bool> {
        Binding(
            get:  { viewModel.errorMessage != nil },
            set:  { if !$0 { viewModel.errorMessage = nil } }
        )
    }
}
