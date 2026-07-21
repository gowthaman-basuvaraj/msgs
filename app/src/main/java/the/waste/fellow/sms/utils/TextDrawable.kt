package the.waste.fellow.sms.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

/**
 * Lightweight in-repo replacement for the (now defunct, jcenter-only)
 * com.amulyakhare:textdrawable library. Renders a letter centered on a solid
 * coloured circle. Keeps the original fluent API — `TextDrawable.builder().buildRound(text, color)`
 * — so existing call sites need only an import change.
 */
class TextDrawable private constructor(
    private val text: String,
    private val color: Int,
) : Drawable() {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = this@TextDrawable.color
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = pickTextColor(this@TextDrawable.color)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    override fun draw(canvas: Canvas) {
        val bounds: Rect = bounds
        if (bounds.width() <= 0 || bounds.height() <= 0) return

        val radius = minOf(bounds.width(), bounds.height()) / 2f
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()

        canvas.drawCircle(cx, cy, radius, backgroundPaint)

        textPaint.textSize = radius
        val yOffset = (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, cx, cy - yOffset, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        backgroundPaint.alpha = alpha
        textPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Drawable", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    class Builder {
        fun buildRound(text: String, color: Int): TextDrawable =
            TextDrawable(text.take(1).uppercase(), color)
    }

    companion object {
        fun builder(): Builder = Builder()

        /** Choose black or white text for legibility against [background]. */
        private fun pickTextColor(background: Int): Int {
            val luminance = (0.299 * Color.red(background) +
                    0.587 * Color.green(background) +
                    0.114 * Color.blue(background)) / 255.0
            return if (luminance > 0.6) Color.BLACK else Color.WHITE
        }
    }
}
