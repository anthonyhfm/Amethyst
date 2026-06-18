//
//  SettingsTabView.swift
//  iosApp
//
//  Copyright © 2025 Anthony Hofmeister. All rights reserved.
//

import SwiftUI
import ComposeApp

struct SettingsTabView: View {
    @Bindable var viewModel: SettingsViewModel
    var showsCloseButton = false

    @Environment(\.dismiss) private var dismiss
    @Environment(\.amethystTheme) private var theme

    private var groups: [SettingsGroup] {
        SettingsRepository.shared.settingsGroups as? [SettingsGroup] ?? []
    }

    var body: some View {
        NavigationStack {
            Form {
                ForEach(groups, id: \.title) { group in
                    Section(header: Text(group.title)) {
                        let settingsList = group.settings as? [Setting<AnyObject>] ?? []
                        ForEach(settingsList, id: \.key) { setting in
                            SettingRowView(setting: setting, theme: theme)
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(theme.background.ignoresSafeArea())
            .navigationTitle("Settings")
            .toolbar {
                if showsCloseButton {
                    ToolbarItem(placement: .cancellationAction) {
                        Button {
                            dismiss()
                        } label: {
                            Image(systemName: "xmark")
                        }
                        .accessibilityLabel("Close settings")
                    }
                }
            }
        }
    }
}

// MARK: - Setting Row Router
struct SettingRowView: View {
    let setting: Setting<AnyObject>
    let theme: AmethystTheme

    var body: some View {
        Group {
            if let toggleSetting = setting as? SettingToggle {
                SettingToggleRow(setting: toggleSetting, theme: theme)
            } else if let sliderSetting = setting as? SettingSlider {
                SettingSliderRow(setting: sliderSetting, theme: theme)
            } else if let selectSetting = setting as? SettingSelect {
                SettingSelectRow(setting: selectSetting, theme: theme)
            } else if let textFieldSetting = setting as? SettingTextField {
                SettingTextFieldRow(setting: textFieldSetting, theme: theme)
            } else {
                Text(setting.title)
                    .foregroundStyle(.secondary)
            }
        }
        .listRowBackground(theme.muted)
    }
}

// MARK: - Setting Individual Components

struct SettingToggleRow: View {
    let setting: SettingToggle
    let theme: AmethystTheme
    @State private var isOn: Bool

    init(setting: SettingToggle, theme: AmethystTheme) {
        self.setting = setting
        self.theme = theme
        _isOn = State(initialValue: (setting.value as? KotlinBoolean)?.boolValue ?? false)
    }

    var body: some View {
        Toggle(setting.title, isOn: Binding(
            get: { isOn },
            set: { newValue in
                isOn = newValue
                setting.update(value: KotlinBoolean(bool: newValue))
            }
        ))
        .tint(theme.primary)
    }
}

struct SettingSliderRow: View {
    let setting: SettingSlider
    let theme: AmethystTheme
    @State private var value: Float

    init(setting: SettingSlider, theme: AmethystTheme) {
        self.setting = setting
        self.theme = theme
        _value = State(initialValue: (setting.value as? KotlinFloat)?.floatValue ?? 0.0)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(setting.title)
                .foregroundStyle(theme.foreground)
            HStack(spacing: 12) {
                let rangeStart = (setting.range.start as? KotlinFloat)?.floatValue ?? 0.0
                let rangeEnd = (setting.range.endInclusive as? KotlinFloat)?.floatValue ?? 1.0
                
                Slider(value: Binding(
                    get: { value },
                    set: { newValue in
                        value = newValue
                        setting.update(value: KotlinFloat(value: newValue))
                    }
                ), in: rangeStart...rangeEnd)
                .tint(theme.primary)
                
                Text("\(Int(value * 100))%")
                    .frame(width: 40, alignment: .trailing)
                    .foregroundStyle(theme.mutedForeground)
                    .monospacedDigit()
            }
        }
    }
}

struct SettingSelectRow: View {
    let setting: SettingSelect<AnyObject>
    let theme: AmethystTheme
    @State private var selectedIndex: Int

    init(setting: SettingSelect<AnyObject>, theme: AmethystTheme) {
        self.setting = setting
        self.theme = theme
        
        let currentValue = setting.value
        let options = setting.options
        var index = 0
        for i in 0..<options.count {
            if (options[i] as? NSObject)?.isEqual(currentValue) == true {
                index = i
                break
            }
        }
        _selectedIndex = State(initialValue: index)
    }

    var body: some View {
        Picker(setting.title, selection: Binding(
            get: { selectedIndex },
            set: { newIndex in
                selectedIndex = newIndex
                if newIndex >= 0 && newIndex < setting.options.count {
                    let val = setting.options[newIndex] as AnyObject
                    setting.update(value: val)
                }
            }
        )) {
            ForEach(0..<setting.options.count, id: \.self) { idx in
                let opt = setting.options[idx] as AnyObject
                Text(setting.label(opt)).tag(idx)
            }
        }
        .pickerStyle(.menu)
        .tint(theme.foreground)
    }
}

struct SettingTextFieldRow: View {
    let setting: SettingTextField
    let theme: AmethystTheme
    @State private var text: String

    init(setting: SettingTextField, theme: AmethystTheme) {
        self.setting = setting
        self.theme = theme
        _text = State(initialValue: setting.value as? String ?? "")
    }

    var body: some View {
        HStack {
            Text(setting.title)
                .foregroundStyle(theme.foreground)
            Spacer()
            TextField(setting.title, text: Binding(
                get: { text },
                set: { newValue in
                    text = newValue
                    setting.update(value: newValue as NSString)
                }
            ))
            .textFieldStyle(.roundedBorder)
            .multilineTextAlignment(.trailing)
            .tint(theme.primary)
        }
    }
}
