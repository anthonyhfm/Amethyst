package dev.anthonyhfm.amethyst.desktop

import com.formdev.flatlaf.FlatDarkLaf
import dev.anthonyhfm.amethyst.ui.theme.MATERIAL_AMETHYST_THEME
import javax.swing.UIManager

class FlatAmethystLaf : FlatDarkLaf() {
    override fun initialize() {
        super.initialize()

        UIManager.put(
            "RootPane.background",
            java.awt.Color(
                MATERIAL_AMETHYST_THEME.surfaceVariant.red,
                MATERIAL_AMETHYST_THEME.surfaceVariant.green,
                MATERIAL_AMETHYST_THEME.surfaceVariant.blue,
            )
        )

        UIManager.put(
            "TitlePane.buttonHoverBackground",
            java.awt.Color(
                MATERIAL_AMETHYST_THEME.surfaceBright.red,
                MATERIAL_AMETHYST_THEME.surfaceBright.green,
                MATERIAL_AMETHYST_THEME.surfaceBright.blue,
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
                    MATERIAL_AMETHYST_THEME.onSurfaceVariant.red,
                    MATERIAL_AMETHYST_THEME.onSurfaceVariant.green,
                    MATERIAL_AMETHYST_THEME.onSurfaceVariant.blue,
                )
            )
        }
    }

    override fun isDark(): Boolean {
        return true
    }

    override fun getName(): String? {
        return "Amethyst Theme"
    }

    override fun getDescription(): String? {
        return null
    }
}