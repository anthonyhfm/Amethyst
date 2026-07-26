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

    @Environment(\.amethystTheme) private var theme

    private var folderPath: String {
        abbreviatePath(project.path)
    }

    var body: some View {
        Button(action: onOpen) {
            HStack(alignment: .center, spacing: 14) {
                // Styled Icon Badge conforming strictly to AmethystTheme tokens
                ZStack {
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(theme.secondary)
                        .frame(width: 36, height: 36)

                    Image(systemName: fileIcon(for: project.path))
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(theme.secondaryForeground)
                }

                // Title + path
                VStack(alignment: .leading, spacing: 3) {
                    Text(project.title)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(theme.foreground)
                        .lineLimit(1)

                    Text(folderPath)
                        .font(.system(size: 13, weight: .regular))
                        .foregroundStyle(theme.mutedForeground)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }

                Spacer()

                // Subtle interaction chevron indicator
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(theme.mutedForeground.opacity(0.5))
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .listRowBackground(theme.muted)
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            Button(role: .destructive, action: onRemove) {
                Label("Remove", systemImage: "trash")
            }
            Button(action: onEdit) {
                Label("Edit", systemImage: "pencil")
            }
            .tint(theme.primary)
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
