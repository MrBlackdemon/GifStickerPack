package com.gifsticker

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

data class GifItem(
    val uri: Uri,
    val name: String,
    var selected: Boolean = true
)

class GifAdapter(
    private val items: MutableList<GifItem> = mutableListOf()
) : RecyclerView.Adapter<GifAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.imgGif)
        val name: TextView = view.findViewById(R.id.tvName)
        val check: CheckBox = view.findViewById(R.id.cbSelected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gif, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.check.isChecked = item.selected

        Glide.with(holder.image.context)
            .asGif()
            .load(item.uri)
            .centerCrop()
            .into(holder.image)

        holder.check.setOnCheckedChangeListener { _, isChecked ->
            items[holder.adapterPosition].selected = isChecked
        }

        holder.itemView.setOnClickListener {
            holder.check.isChecked = !holder.check.isChecked
        }
    }

    override fun getItemCount() = items.size

    fun setItems(newItems: List<GifItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<GifItem> = items.filter { it.selected }

    fun selectAll(select: Boolean) {
        items.forEach { it.selected = select }
        notifyDataSetChanged()
    }
}
