package com.codemobile.editor

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.codemobile.R

/**
 * Contenedor del editor: gutter izquierdo (números de línea + zona de pinch para
 * indentar/desindentar) + CodeEditText + margen derecho (long-press + drag para
 * seleccionar línea(s)).
 *
 * Gestos:
 * - Pinch sobre el gutter izquierdo: pinch-out (dedos se alejan) = indentar,
 *   pinch-in (dedos se acercan) = desindentar la línea actual.
 * - Long-press sobre el margen derecho: selecciona la línea a esa altura; sin
 *   soltar, deslizar verticalmente extiende la selección a varias líneas.
 */
class EditorPaneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    val codeEditText = CodeEditText(context)
    private val gutter = FrameLayout(context)
    private val rightMargin = FrameLayout(context)

    private var longPressAnchorIndex: Int = -1
    private var longPressActive = false

    init {
        orientation = HORIZONTAL

        gutter.layoutParams = LayoutParams(dpToPx(34), LayoutParams.MATCH_PARENT)
        gutter.setBackgroundColor(ContextCompat.getColor(context, R.color.bg_gutter))

        codeEditText.layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        codeEditText.setBackgroundColor(Color.TRANSPARENT)
        codeEditText.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        codeEditText.textSize = 13f
        codeEditText.gravity = Gravity.TOP
        codeEditText.setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))
        codeEditText.setHorizontallyScrolling(true)

        rightMargin.layoutParams = LayoutParams(dpToPx(26), LayoutParams.MATCH_PARENT)
        rightMargin.setBackgroundColor(ContextCompat.getColor(context, R.color.bg))

        addView(gutter)
        addView(codeEditText)
        addView(rightMargin)

        setupPinchGesture()
        setupRightMarginGesture()
    }

    private fun setupPinchGesture() {
        val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleEnd(detector: ScaleGestureDetector) {
                val increase = detector.scaleFactor > 1f || detector.currentSpan > detector.previousSpan
                codeEditText.indentCurrentLine(increase)
            }
        })
        gutter.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            true
        }
    }

    private fun setupRightMarginGesture() {
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                val charIndex = charIndexForY(e.y)
                longPressAnchorIndex = charIndex
                longPressActive = true
                codeEditText.selectLineAt(charIndex)
            }
        })
        rightMargin.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    if (longPressActive) {
                        val charIndex = charIndexForY(event.y)
                        codeEditText.extendLineSelectionTo(longPressAnchorIndex, charIndex)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressActive = false
                }
            }
            true
        }
    }

    /** Aproxima el índice de carácter en el texto correspondiente a una coordenada Y de pantalla. */
    private fun charIndexForY(y: Float): Int {
        val layout = codeEditText.layout ?: return codeEditText.text?.length ?: 0
        val line = layout.getLineForVertical(y.toInt() - codeEditText.paddingTop)
            .coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))
        return layout.getLineStart(line)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
