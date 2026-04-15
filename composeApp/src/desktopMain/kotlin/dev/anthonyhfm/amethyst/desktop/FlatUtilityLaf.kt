package dev.anthonyhfm.amethyst.desktop

import com.formdev.flatlaf.FlatDarkLaf
import dev.anthonyhfm.amethyst.ui.theme.MATERIAL_AMETHYST_THEME
import javax.swing.UIManager

class FlatUtilityLaf : FlatDarkLaf() {
    override fun initialize() {
        super.initialize()

        UIManager.put(
            "RootPane.background",
            java.awt.Color(
                MATERIAL_AMETHYST_THEME.surface.red,
                MATERIAL_AMETHYST_THEME.surface.green,
                MATERIAL_AMETHYST_THEME.surface.blue,
            )
        )

        UIManager.put(
            "TitlePane.buttonHoverBackground",
            java.awt.Color(
                MATERIAL_AMETHYST_THEME.surfaceVariant.red,
                MATERIAL_AMETHYST_THEME.surfaceVariant.green,
                MATERIAL_AMETHYST_THEME.surfaceVariant.blue,
            )
        )

        UIManager.put(
            "TitlePane.closeHoverBackground",
            java.awt.Color(
                MATERIAL_AMETHYST_THEME.errorContainer.red,
                MATERIAL_AMETHYST_THEME.errorContainer.green,
                MATERIAL_AMETHYST_THEME.errorContainer.blue,
            )
        )

        listOf("TitlePane.embeddedForeground", "TitlePane.foreground").forEach {
            UIManager.put(
                it,
                java.awt.Color(
                    MATERIAL_AMETHYST_THEME.onSurface.red,
                    MATERIAL_AMETHYST_THEME.onSurface.green,
                    MATERIAL_AMETHYST_THEME.onSurface.blue,
                )
            )
        }
    }

    override fun isDark(): Boolean {
        return true
    }

    override fun getName(): String? {
        return "Amethyst Utility Theme"
    }

    override fun getDescription(): String? {
        return null
    }
}