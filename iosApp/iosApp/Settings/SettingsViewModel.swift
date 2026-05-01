//
//  SettingsViewModel.swift
//  iosApp
//
//  Copyright © 2025 Anthony Hofmeister. All rights reserved.
//

import Foundation
import ComposeApp

@Observable
@MainActor
final class SettingsViewModel {

    // MARK: - General

    var performanceFPS: Int32 {
        didSet { GeneralSettings.shared.performanceFPS.update(value: KotlinInt(value: performanceFPS)) }
    }

    var gradientSmoothness: Float {
        didSet { GeneralSettings.shared.gradientSmoothness.update(value: KotlinFloat(value: gradientSmoothness)) }
    }

    // MARK: - Audio

    var masterVolume: Float {
        didSet { AudioSettings.shared.masterVolume.update(value: KotlinFloat(value: masterVolume)) }
    }

    // MARK: - Experimental

    var gemsEnabled: Bool {
        didSet { ExperimentalSettings.shared.extensions.update(value: KotlinBoolean(bool: gemsEnabled)) }
    }

    // MARK: - Static option lists
    // Mirrors GeneralSettings.performanceFPS.options and gradientSmoothness.options.

    let fpsOptions: [Int32] = [60, 90, 120]

    // (value, display label) pairs — mirrors the KMP label lambda: "(it * 100).toInt()%"
    let gradientSmoothnessOptions: [(value: Float, label: String)] = [
        (0.5,  "50%"),
        (0.75, "75%"),
        (1.0,  "100%"),
    ]

    // MARK: - Init

    init() {
        performanceFPS     = (GeneralSettings.shared.performanceFPS.value as! KotlinInt).int32Value
        gradientSmoothness = (GeneralSettings.shared.gradientSmoothness.value as! KotlinFloat).floatValue
        masterVolume       = (AudioSettings.shared.masterVolume.value as! KotlinFloat).floatValue
        gemsEnabled        = (ExperimentalSettings.shared.extensions.value as! KotlinBoolean).boolValue
    }
}
