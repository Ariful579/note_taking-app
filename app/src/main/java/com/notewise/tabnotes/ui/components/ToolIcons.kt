package com.notewise.tabnotes.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hand-drawn tool icons. Every instrument has its own silhouette so tools are recognisable at a glance:
 * ballpoint, fountain nib, chisel highlighter, block eraser, dotted lasso, magic wand, shapes, text.
 * Drawn on a 24x24 grid with a 1.7 stroke; the colour comes from the Icon() tint, so they follow the theme.
 */
object ToolIcons {
    private const val SW = 1.7f
    private val INK = SolidColor(Color.Black)

    private fun build(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()

    /** Outlined path. */
    private fun ImageVector.Builder.line(width: Float = SW, d: PathBuilder.() -> Unit) {
        path(
            fill = null,
            stroke = INK,
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = d
        )
    }

    /** Filled path (alpha < 1 gives the softer secondary shade). */
    private fun ImageVector.Builder.solid(alpha: Float = 1f, d: PathBuilder.() -> Unit) {
        path(fill = INK, fillAlpha = alpha, stroke = null, pathBuilder = d)
    }

    private fun PathBuilder.rrect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        moveTo(x + r, y)
        lineTo(x + w - r, y); quadTo(x + w, y, x + w, y + r)
        lineTo(x + w, y + h - r); quadTo(x + w, y + h, x + w - r, y + h)
        lineTo(x + r, y + h); quadTo(x, y + h, x, y + h - r)
        lineTo(x, y + r); quadTo(x, y, x + r, y)
        close()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, false, cx + r, cy)
        arcTo(r, r, 0f, true, false, cx - r, cy)
        close()
    }

    /** Ballpoint pen: slim barrel, clip, cone and a small ink tip. */
    val Ballpoint: ImageVector by lazy {
        build("Ballpoint") {
            group(rotate = 45f, pivotX = 12f, pivotY = 12f, scaleX = 0.92f, scaleY = 0.92f) {
                line { rrect(9.4f, -1f, 5.2f, 16.4f, 2.6f) }
                line {
                    moveTo(9.4f, 15.4f); lineTo(14.6f, 15.4f); lineTo(13.7f, 19.5f)
                    lineTo(12f, 22.8f); lineTo(10.3f, 19.5f); close()
                }
                solid { moveTo(10.75f, 20.6f); lineTo(12f, 22.8f); lineTo(13.25f, 20.6f); close() }
                line { moveTo(14.6f, 3.6f); lineTo(14.6f, 7.8f) }
            }
        }
    }

    /** Fountain pen: tapered grip with a split nib and breather hole. */
    val Fountain: ImageVector by lazy {
        build("FountainPen") {
            group(rotate = 45f, pivotX = 12f, pivotY = 12f, scaleX = 0.92f, scaleY = 0.92f) {
                line {
                    moveTo(9.6f, -1f); lineTo(14.4f, -1f); lineTo(15.4f, 10.3f)
                    lineTo(8.6f, 10.3f); close()
                }
                line {
                    moveTo(8.2f, 10.3f); lineTo(15.8f, 10.3f); lineTo(15.3f, 14.4f)
                    lineTo(12f, 22.8f); lineTo(8.7f, 14.4f); close()
                }
                line { moveTo(12f, 16.6f); lineTo(12f, 22.6f) }
                solid { circle(12f, 14.9f, 1.05f) }
            }
        }
    }

    /** Chunky chisel-tip marker with a soft ink stripe underneath. */
    val Highlighter: ImageVector by lazy {
        build("Highlighter") {
            group(rotate = 45f, pivotX = 12f, pivotY = 12f, scaleX = 0.92f, scaleY = 0.92f) {
                line { rrect(8f, -1f, 8f, 11.6f, 2f) }
                line {
                    moveTo(8.5f, 10.6f); lineTo(15.5f, 10.6f); lineTo(16.3f, 14.3f)
                    lineTo(7.7f, 14.3f); close()
                }
                solid(0.45f) {
                    moveTo(8.1f, 14.3f); lineTo(15.9f, 14.3f); lineTo(15f, 19.6f)
                    lineTo(9f, 19.6f); close()
                }
                line {
                    moveTo(8.1f, 14.3f); lineTo(15.9f, 14.3f); lineTo(15f, 19.6f)
                    lineTo(9f, 19.6f); close()
                }
            }
            line(2.4f) { moveTo(2.6f, 22.4f); lineTo(11.2f, 22.4f) }
        }
    }

    /** Rubber block eraser (two-tone) resting on a baseline. */
    val Eraser: ImageVector by lazy {
        build("Eraser") {
            group(rotate = -45f, pivotX = 12f, pivotY = 12f) {
                line { rrect(2.4f, 7.6f, 19.2f, 8.8f, 2.6f) }
                line { moveTo(10.6f, 7.6f); lineTo(10.6f, 16.4f) }
                solid(0.30f) {
                    moveTo(10.6f, 7.6f); lineTo(19f, 7.6f); quadTo(21.6f, 7.6f, 21.6f, 10.2f)
                    lineTo(21.6f, 13.8f); quadTo(21.6f, 16.4f, 19f, 16.4f); lineTo(10.6f, 16.4f); close()
                }
            }
            line { moveTo(12.5f, 21.4f); lineTo(21.1f, 21.4f) }
        }
    }

    /** Lasso: dotted loop with a rope tail. */
    val Lasso: ImageVector by lazy {
        build("Lasso") {
            line {
                var a = 0.0
                while (a < 360.0) {
                    for (k in 0..2) {
                        val r = (a + k * 13.0) * PI / 180.0
                        val x = 12f + 8.7f * cos(r).toFloat()
                        val y = 9f + 5.6f * sin(r).toFloat()
                        if (k == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    a += 40.0
                }
            }
            line {
                moveTo(7.5f, 13.9f)
                curveTo(6.2f, 15.6f, 6.5f, 17.7f, 8.4f, 18.8f)
                curveTo(10.1f, 19.8f, 12f, 19.2f, 12f, 17.7f)
                curveTo(12f, 16.5f, 10.8f, 16f, 9.9f, 16.6f)
            }
        }
    }

    /** Magic pen (fading ink): a wand with a sparkle. */
    val MagicWand: ImageVector by lazy {
        build("MagicWand") {
            line(2.2f) { moveTo(4f, 20f); lineTo(13.2f, 10.8f) }
            solid {
                moveTo(17f, 2.8f); lineTo(18.3f, 5.7f); lineTo(21.2f, 7f); lineTo(18.3f, 8.3f)
                lineTo(17f, 11.2f); lineTo(15.7f, 8.3f); lineTo(12.8f, 7f); lineTo(15.7f, 5.7f); close()
            }
            line(1.5f) { moveTo(6.2f, 4f); lineTo(6.2f, 7.6f); moveTo(4.4f, 5.8f); lineTo(8f, 5.8f) }
            line(1.5f) { moveTo(19.2f, 14.6f); lineTo(19.2f, 18.2f); moveTo(17.4f, 16.4f); lineTo(21f, 16.4f) }
        }
    }

    /** Shapes: a square overlapping a circle. */
    val Shapes: ImageVector by lazy {
        build("Shapes") {
            line { rrect(3.4f, 11f, 9.2f, 9.2f, 2f) }
            line { circle(15.4f, 8.6f, 5.2f) }
        }
    }

    /** Text tool: a clean serif "T". */
    val Text: ImageVector by lazy {
        build("Text") {
            line(2f) { moveTo(5f, 6f); lineTo(19f, 6f) }
            line(2f) { moveTo(12f, 6f); lineTo(12f, 19f) }
            line(2f) { moveTo(8.8f, 19f); lineTo(15.2f, 19f) }
        }
    }
}
