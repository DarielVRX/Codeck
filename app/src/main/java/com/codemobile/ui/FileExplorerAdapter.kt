package com.codemobile.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.codemobile.R
import java.io.File

class FileExplorerAdapter(
    private var items: List<File>,
    private val onItemClick: (File) -> Unit
) : RecyclerView.Adapter<FileExplorerAdapter.ViewHolder>() {

    fun update(newItems: List<File>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = items[position]
        holder.icon.text = if (file.isDirectory) "📁" else "📄"
        holder.name.text = file.name
        holder.itemView.setOnClickListener { onItemClick(file) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.fileIcon)
        val name: TextView = view.findViewById(R.id.fileName)
    }
}
