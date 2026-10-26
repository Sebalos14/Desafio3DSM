package com.udb.readingtracker

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.udb.readingtracker.databinding.ItemBookBinding

/** Rellena una tarjeta de libro (se usa en la biblioteca y en la pantalla principal). */
fun bindBook(b: ItemBookBinding, book: Book) {
    val ctx = b.root.context
    b.tvTitle.text = book.title
    b.tvAuthor.text = "de ${book.author}"
    b.tvMeta.text = "${book.genre}  •  ${book.status}"

    val (bgColorRes, textColorRes) = when (book.status) {
        Constants.STATUS_FINISHED -> R.color.status_finished_bg to R.color.status_finished_text
        Constants.STATUS_READING -> R.color.status_reading_bg to R.color.status_reading_text
        else -> R.color.status_toread_bg to R.color.status_toread_text
    }
    b.tvMeta.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, bgColorRes))
    b.tvMeta.setTextColor(ContextCompat.getColor(ctx, textColorRes))

    b.progressBook.progress = book.progress
    b.tvProgress.text = "${book.progress}%  (${book.pagesRead}/${book.totalPages} págs.)"
    val rating = book.rating
    if (book.status == Constants.STATUS_FINISHED && rating != null) {
        b.tvRating.visibility = View.VISIBLE
        b.tvRating.text = "★".repeat(rating) + "☆".repeat(5 - rating) + " ($rating/5)"
    } else {
        b.tvRating.visibility = View.GONE
    }
}

class BookAdapter(
    private val onClick: (Book) -> Unit
) : RecyclerView.Adapter<BookAdapter.VH>() {

    private var items: List<Book> = emptyList()

    fun submit(list: List<Book>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val binding: ItemBookBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val book = items[position]
        bindBook(holder.binding, book)
        holder.binding.root.setOnClickListener { onClick(book) }
    }

    override fun getItemCount() = items.size
}
