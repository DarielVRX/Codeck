package com.codemobile.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

/**
 * Dibuja el carácter de tabulación (\t) de forma distinguible de un espacio:
 * una línea vertical tenue al inicio de la celda, para que no se confunda
 * visualmente con varios espacios al editar el mismo archivo en otro editor
 * con una convención distinta.
 */
class TabMarkerSpan(private val lineColor: Int) : ReplacementSpan() {

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        // Ancho visual de un tab: 4 espacios del tipo monoespaciado actual.
        return (paint.measureText("0") * 4).toInt()
    }

    override fun draw(
        canvas: Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val linePaint = Paint(paint)
        linePaint.color = lineColor
        linePaint.strokeWidth = paint.textSize * 0.06f
        val lineX = x + linePaint.strokeWidth
        canvas.drawLine(lineX, top.toFloat() + 2f, lineX, bottom.toFloat() - 2f, linePaint)
    }
}
