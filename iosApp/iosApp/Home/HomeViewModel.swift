//
//  HomeViewModel.swift
//  iosApp
//
//  Created by Copilot
//  Copyright © 2025 Anthony Hofmeister. All rights reserved.
//

import Foundation
import ComposeApp
import UniformTypeIdentifiers

// MARK: - Sheet routing

enum HomeSheet: Identifiable {
    case createProject
    case editProject(path: String)
    case abletonWizard(path: String)

    var id: String {
        switch self {
        case .createProject:              return "create"
        case .editProject(let path):      return "edit-\(path)"
        case .abletonWizard(let path):    return "ableton-\(path)"
        }
    }
}

// MARK: - ViewModel

@Observable
@MainActor
final class HomeViewModel {

    // Published state
    var recentProjects: [RecentWorkspace] = []
    var isLoading = false
    var loadingText = ""
    var errorMessage: String? = nil
    var activeSheet: HomeSheet? = nil
    var isWorkspaceOpen = false
    var showingFilePicker = false

    // Ableton wizard: pending import parameters set by openFile() or getZipFormat callback
    var pendingAbletonPath: String? = nil

    // ── Lifecycle ──────────────────────────────────────────────────────────

    init() {
        loadRecents()
    }

    func loadRecents() {
        let raw = HomeSwiftBridge.shared.recentWorkspaces()
        recentProjects = (raw as? [RecentWorkspace]) ?? []
    }

    // ── Recent projects ────────────────────────────────────────────────────

    func removeRecent(path: String) {
        HomeSwiftBridge.shared.removeRecentWorkspace(path: path)
        loadRecents()
    }

    func openRecent(_ project: RecentWorkspace) {
        startLoading("Opening Project")
        HomeSwiftBridge.shared.openRecentWorkspace(
            project: project,
            onSuccess: { [weak self] in Task { @MainActor [weak self] in self?.handleWorkspaceOpened() } },
            onError:   { [weak self] msg in Task { @MainActor [weak self] in self?.handleError(msg) } }
        )
    }

    // ── Project creation / editing ─────────────────────────────────────────

    func createProject(name: String, author: String) {
        startLoading("Creating Project")
        HomeSwiftBridge.shared.createProject(
            name: name,
            author: author,
            onSuccess: { [weak self] in Task { @MainActor [weak self] in self?.handleWorkspaceOpened() } },
            onError:   { [weak self] msg in Task { @MainActor [weak self] in self?.handleError(msg) } }
        )
    }

    func updateProject(path: String, name: String, author: String) {
        startLoading("Saving Changes")
        HomeSwiftBridge.shared.updateProject(
            path: path,
            name: name,
            author: author,
            onSuccess: { [weak self] in Task { @MainActor [weak self] in self?.handleWorkspaceOpened() } },
            onError:   { [weak self] msg in Task { @MainActor [weak self] in self?.handleError(msg) } }
        )
    }

    // ── File picker ────────────────────────────────────────────────────────

    func openFile(url: URL) {
        guard url.startAccessingSecurityScopedResource() else {
            errorMessage = "Could not access the selected file."
            return
        }
        defer { url.stopAccessingSecurityScopedResource() }

        guard let data = try? Data(contentsOf: url) else {
            errorMessage = "Failed to read the selected file."
            return
        }

        let filename  = url.lastPathComponent
        
        let storedPath = HomeSwiftBridge.shared.indexFile(data: data, filename: filename)

        let ext = url.pathExtension.lowercased()
        switch ext {
        case "ame", "approj":
            openIndexedFile(path: storedPath)

        case "als":
            pendingAbletonPath = storedPath
            activeSheet = .abletonWizard(path: storedPath)

        case "zip":
            // Detect format asynchronously, then route
            detectZipAndRoute(storedPath: storedPath)

        default:
            errorMessage = "Unsupported file format: .\(ext)"
            HomeSwiftBridge.shared.clearIndexedFile(path: storedPath)
        }
    }

    func openIndexedFile(path: String) {
        startLoading("Loading Project")
        HomeSwiftBridge.shared.openWorkspaceFromPath(
            path: path,
            onSuccess: { [weak self] in
                Task { @MainActor [weak self] in
                    self?.handleWorkspaceOpened()
                    self?.loadRecents()
                }
            },
            onError: { [weak self] msg in Task { @MainActor [weak self] in self?.handleError(msg) } }
        )
    }

    // ── Ableton import ─────────────────────────────────────────────────────

    func importAbleton(path: String, palettePath: String?, apolloPath: String?) {
        startLoading("Translating your Ableton Live-Set")
        activeSheet = nil
        HomeSwiftBridge.shared.importAbletonProject(
            path: path,
            palettePath: palettePath,
            apolloPath: apolloPath,
            onSuccess: { [weak self] in
                Task { @MainActor [weak self] in
                    self?.handleWorkspaceOpened()
                    self?.loadRecents()
                }
            },
            onError: { [weak self] msg in Task { @MainActor [weak self] in self?.handleError(msg) } }
        )
    }

    // ── Workspace lifecycle ────────────────────────────────────────────────

    func workspaceClosed() {
        isWorkspaceOpen = false
        loadRecents()
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    func localAuthor() -> String {
        HomeSwiftBridge.shared.localAuthor()
    }

    private func detectZipAndRoute(storedPath: String) {
        HomeSwiftBridge.shared.getZipFormat(path: storedPath) { [weak self] format in
            Task { @MainActor [weak self] in
                guard let self else { return }
                switch format {
                case "ABLETON":
                    self.pendingAbletonPath = storedPath
                    self.activeSheet = .abletonWizard(path: storedPath)
                default:
                    // ABLETON_APOLLO and UNIPAD convert directly
                    self.openIndexedFile(path: storedPath)
                }
            }
        }
    }

    private func startLoading(_ text: String) {
        loadingText = text
        isLoading   = true
    }

    private func handleWorkspaceOpened() {
        isLoading       = false
        isWorkspaceOpen = true
    }

    private func handleError(_ message: String) {
        isLoading     = false
        errorMessage  = message
    }
}
