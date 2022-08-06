package com.bibekjena.mymemory

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bibekjena.mymemory.models.BoardSize

class ImagePickerAdapter(
    private val context: Context,
    private val imageUris: List<Uri>,
    private val boardSize: BoardSize,
    private val imageClickListener : ImageClickListener
) : RecyclerView.Adapter<ImagePickerAdapter.ViewHolder>() {


    interface  ImageClickListener {
        fun onPlaceholderClicked()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        val view = LayoutInflater.from(context).inflate(R.layout.card_image,parent,false)
        val cardWidth = parent.width/boardSize.getWidth()
        val cardHeight = parent.height/boardSize.getHeight()
        val cardSideLength = minOf(cardHeight,cardWidth)
        val layoutParams = view.findViewById<ImageView>(R.id.ivCustomImage).layoutParams

        layoutParams.width = cardSideLength
        layoutParams.height = cardSideLength
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if(position < imageUris.size){
            holder.bind(imageUris[position])
        }
        else{
            holder.bind()
        }
    }

    override fun getItemCount() = boardSize.getNumPairs()


    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        private val ivCustomImage = itemView.findViewById<ImageView>(R.id.ivCustomImage)
        fun bind(uri: Uri) {
            ivCustomImage.setImageURI(uri)
            ivCustomImage.setOnClickListener(null)
        }


        fun bind() {
            ivCustomImage.setOnClickListener{
                // launch the intent for the user to choose photos
                imageClickListener.onPlaceholderClicked()
            }
        }
    }


}
