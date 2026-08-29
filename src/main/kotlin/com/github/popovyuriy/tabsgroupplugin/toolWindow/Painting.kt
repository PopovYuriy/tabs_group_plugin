package com.github.popovyuriy.tabsgroupplugin.toolWindow

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JPanel

/** A panel with rounded corners and an optional fill. */
class RoundedPanel(
    private val cornerRadius: Int,
    private var panelColor: Color?
) : JPanel() {

    init {
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        panelColor?.let { color ->
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = color
                g2.fill(
                    RoundRectangle2D.Float(
                        0f, 0f,
                        width.toFloat(), height.toFloat(),
                        cornerRadius.toFloat(), cornerRadius.toFloat()
                    )
                )
            } finally {
                g2.dispose()
            }
        }
        super.paintComponent(g)
    }

    fun setPanelColor(color: Color?) {
        if (panelColor == color) return
        panelColor = color
        repaint()
    }
}

/** Small colored swatch for the color picker menu. */
class ColorIcon(private val color: Color, private val size: Int) : Icon {

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            g2.fillRoundRect(x, y, size, size, 3, 3)
            g2.color = color.darker()
            g2.drawRoundRect(x, y, size - 1, size - 1, 3, 3)
        } finally {
            g2.dispose()
        }
    }

    override fun getIconWidth(): Int = size

    override fun getIconHeight(): Int = size
}

/** Cross drawn in the current foreground color, so it reads in both light and dark themes. */
class CloseIcon(private val color: Color, private val size: Int) : Icon {

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g2.color = color
            g2.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val padding = 3
            g2.drawLine(x + padding, y + padding, x + size - padding, y + size - padding)
            g2.drawLine(x + size - padding, y + padding, x + padding, y + size - padding)
        } finally {
            g2.dispose()
        }
    }

    override fun getIconWidth(): Int = size

    override fun getIconHeight(): Int = size
}