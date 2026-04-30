//
//  RecentProjectRow.swift
//  iosApp
//
//  Created by Copilot
//  Copyright © 2025 Anthony Hofmeister. All rights reserved.
//

import SwiftUI
import ComposeApp

struct RecentProjectRow: View {
    let project: RecentWorkspace
    let onOpen: () -> Void
    let onEdit: () -> Void
    let onRemove: () -> Void

    private var folderPath: String {
        abbreviatePath(project.path)
    }

    var body: some View {
        Button(action: onOpen) {
            HStack(alignment: .center, spacing: 12) {
                // File-type icon
                Image(systemName: fileIcon(for: project.path))
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .frame(width: 28)

                // Title + path
                VStack(alignment: .leading, spacing: 2) {
                    Text(project.title)
                        .font(.body)
                        .foregroundStyle(.primary)
                        .lineLimit(1)

                    Text(folderPath)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
            }
        }
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            Button(role: .destructive, action: onRemove) {
                Label("Remove", systemImage: "trash")
            }
            Button(action: onEdit) {
                Label("Edit", systemImage: "pencil")
            }
            .tint(.blue)
        }
        .contextMenu {
            Button(action: onOpen) {
                Label("Open", systemImage: "folder.badge.arrow.up")
            }
            Button(action: onEdit) {
                Label("Edit Details", systemImage: "pencil")
            }
            Divider()
            Button(role: .destructive, action: onRemove) {
                Label("Remove from Recent", systemImage: "trash")
            }
        }
    }

    // MARK: - Helpers

    private func fileIcon(for path: String) -> String {
        let ext = (path as NSString).pathExtension.lowercased()
        switch ext {
        case "als":             return "music.note.list"
        case "zip":             return "archivebox"
        case "approj":          return "waveform"
        default:                return "doc"
        }
    }

    private func abbreviatePath(_ path: String) -> String {
        let dir = (path as NSString).deletingLastPathComponent
        guard !dir.isEmpty else { return path }

        let home = NSHomeDirectory()
        if dir.hasPrefix(home) {
            return "~" + dir.dropFirst(home.count)
        }
        return dir
    }
}
