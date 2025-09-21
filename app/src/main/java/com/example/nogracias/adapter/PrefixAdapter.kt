package com.example.nogracias.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.nogracias.R
import com.example.nogracias.model.PrefixItem

class PrefixAdapter(
    private val prefixes: MutableList<PrefixItem>,
    private val onDeleteClick: (Int) -> Unit,
    private val onToggleClick: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<PrefixAdapter.PrefixViewHolder>() {

    class PrefixViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPrefix: TextView = view.findViewById(R.id.tvPrefix)
        val switchPrefix: Switch = view.findViewById(R.id.switchPrefix)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrefixViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prefix, parent, false)
        return PrefixViewHolder(view)
    }

    override fun onBindViewHolder(holder: PrefixViewHolder, position: Int) {
        val prefix = prefixes[position]

        holder.tvPrefix.text = prefix.value
        holder.switchPrefix.isChecked = prefix.isActive

        // Estilo visual según estado
        if (prefix.isActive) {
            holder.tvPrefix.alpha = 1.0f
            holder.switchPrefix.thumbTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
        } else {
            holder.tvPrefix.alpha = 0.5f
            holder.switchPrefix.thumbTintList = android.content.res.ColorStateList.valueOf(0xFF9E9E9E.toInt())
        }

        // Click listeners
        holder.switchPrefix.setOnCheckedChangeListener { _, isChecked ->
            onToggleClick(position, isChecked)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(position)
        }
    }

    override fun getItemCount(): Int = prefixes.size

    fun updateItem(position: Int, isActive: Boolean) {
        if (position in 0 until prefixes.size) {
            prefixes[position].isActive = isActive
            notifyItemChanged(position)
        }
    }

    fun removeItem(position: Int) {
        if (position in 0 until prefixes.size) {
            prefixes.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun addItem(prefix: PrefixItem) {
        prefixes.add(prefix)
        notifyItemInserted(prefixes.size - 1)
    }
}