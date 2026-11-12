package com.codemobile.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.codemobile.R
import com.codemobile.files.OpenFile

private val PALETTE = intArrayOf(
    R.color.tab_01, R.color.tab_02, R.color.tab_03, R.color.tab_04,
    R.color.tab_05, R.color.tab_06, R.color.tab_07, R.color.tab_08,
    R.color.tab_09, R.color.tab_10, R.color.tab_11, R.color.tab_12,
    R.color.tab_13, R.color.tab_14, R.color.tab_15, R.color.tab_16
)

fun paletteColorRes(index: Int): Int = PALETTE[index.coerceIn(0, PALETTE.size - 1)]
fun paletteSize(): Int = PALETTE.size

/**
 * Lista de pestañas abiertas. La activa se ensancha y muestra el nombre;
 * las demás quedan angostas mostrando solo la extensión (sin punto).
 */
class TabBarAdapter(
    private val files: MutableList<OpenFile>,
    private val onTabClick: (Int) -> Unit,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var activeIndex: Int = 0

    private val TYPE_TAB = 0
    private val TYPE_ADD = 1

    fun setActiveIndex(index: Int) {
        activeIndex = index
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = files.size + 1

    override fun getItemViewType(position: Int): Int =
        if (position == files.size) TYPE_ADD else TYPE_TAB

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_ADD) {
            val tv = TextView(parent.context)
            val size = (28 * parent.resources.displayMetrics.density).toInt()
            tv.layoutParams = LinearLayout.LayoutParams(size, size)
            tv.text = "+"
            tv.textSize = 16f
            tv.gravity = android.view.Gravity.CENTER
            tv.setTextColor(parent.resources.getColor(R.color.text_dim, parent.context.theme))
            AddViewHolder(tv)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false)
            TabViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AddViewHolder) {
            holder.view.setOnClickListener { onAddClick() }
            return
        }
        val tabHolder = holder as TabViewHolder
        val file = files[position]
        val context = tabHolder.root.context
        val isActive = position == activeIndex

        val bg = tabHolder.root.background.mutate() as GradientDrawable
        bg.setColor(context.resources.getColor(paletteColorRes(file.colorIndex), context.theme))

        tabHolder.ext.text = file.extension.ifBlank { "txt" }
        tabHolder.name.text = file.displayName
        tabHolder.name.visibility = if (isActive) View.VISIBLE else View.GONE
        tabHolder.root.alpha = if (isActive) 1f else 0.55f

        tabHolder.root.setOnClickListener { onTabClick(position) }
    }

    class TabViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.tabRoot)
        val ext: TextView = view.findViewById(R.id.tabExt)
        val name: TextView = view.findViewById(R.id.tabName)
    }

    class AddViewHolder(val view: View) : RecyclerView.ViewHolder(view)
}
