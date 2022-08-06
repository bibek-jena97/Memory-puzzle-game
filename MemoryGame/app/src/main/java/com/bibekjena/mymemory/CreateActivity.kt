package com.bibekjena.mymemory

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.Image
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bibekjena.mymemory.models.BoardSize
import com.bibekjena.mymemory.utils.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream

class CreateActivity : AppCompatActivity() {

    companion object{
        private const val MIN_GAME_LENGTH = 3
        private const val MAX_GAME_LENGTH = 16
        private const val PICK_PHOTOS_CODE = 657
        private const val TAG = "CreateActivity"
        private const val READ_EXTERNAL_PHOTOS_CODE = 234
        private const val READ_PHOTOS_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private lateinit var boardSize : BoardSize
    private lateinit var pbUploading : ProgressBar
    private lateinit var adapter : ImagePickerAdapter
    private var numImagesRequired = -1
    private lateinit var btnSave : Button
    private lateinit var etGameName : EditText
    private lateinit var rvImagePicker : RecyclerView
    private val chosenImageUri = mutableListOf<Uri>()
    private val storage = Firebase.storage
    private val db = Firebase.firestore


    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)

        btnSave = findViewById(R.id.btnSave)
        pbUploading = findViewById(R.id.pbUploading)
        etGameName = findViewById(R.id.etGameName)
        rvImagePicker = findViewById(R.id.rvImagePicker)

        supportActionBar?.setDefaultDisplayHomeAsUpEnabled(true)
        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImagesRequired = boardSize.getNumPairs()
        supportActionBar?.title = "choose pics (0 / $numImagesRequired)"

        btnSave.setOnClickListener {
            saveDataToFirebase()
        }
        etGameName.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_LENGTH))
        etGameName.addTextChangedListener(object : TextWatcher{
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun afterTextChanged(p0: Editable?) {
                btnSave.isEnabled = shouldEnableSaveButton()
            }

        })

        adapter = ImagePickerAdapter(this,chosenImageUri,boardSize, object : ImagePickerAdapter.ImageClickListener {
           override fun onPlaceholderClicked() {
              if (isPermissionGranted(this@CreateActivity,READ_PHOTOS_PERMISSION)) {
                  launchIntentForPhotos()
              }
               else{
                   requestPermission(this@CreateActivity,READ_PHOTOS_PERMISSION,READ_EXTERNAL_PHOTOS_CODE)
              }

           }

       })
        rvImagePicker.adapter = adapter
        rvImagePicker.setHasFixedSize(true)
        rvImagePicker.layoutManager = GridLayoutManager(this,boardSize.getWidth())
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == READ_EXTERNAL_PHOTOS_CODE){
            if (grantResults.isNotEmpty() && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                launchIntentForPhotos()
            }
            else{
                Toast.makeText(this,"In order to create a custom game, we need permission",Toast.LENGTH_LONG).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home){
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_PHOTOS_CODE || resultCode != RESULT_OK || data == null){
            Log.w(TAG,"Didn't get back the result, user likely canceled the flow")
            return
        }
        val selectedUri = data.data
        val clipData = data.clipData
        if (clipData !=null){
            Log.i(TAG,"clipData numImages ${clipData.itemCount} : $clipData ")
            for (i in 0 until clipData.itemCount){
                val clipItem = clipData.getItemAt(i)
                if (chosenImageUri.size < numImagesRequired){
                    chosenImageUri.add(clipItem.uri)
                }
            }
        }
        else if (selectedUri != null){
            Log.i(TAG,"data : $selectedUri")
            chosenImageUri.add(selectedUri)
        }
        adapter.notifyDataSetChanged()
        supportActionBar?.title = "choose pics: (${chosenImageUri.size} / $numImagesRequired)"
        btnSave.isEnabled = shouldEnableSaveButton()
    }

    private fun saveDataToFirebase() {
        btnSave.isEnabled = false
        Log.i(TAG,"SaveToFirebase")
        val customGameName = etGameName.text.toString()
        // check if a game is already present or not

        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            if (document !=null && document.data!= null){
                AlertDialog.Builder(this)
                    .setTitle("Name already exists")
                    .setMessage("A game already exists with the name $customGameName. Choose another name")
                    .setPositiveButton("OK", null)
                    .show()
                btnSave.isEnabled = true
            }
            else{
               handleImageUploading(customGameName)
            }
        }.addOnFailureListener {exception ->
            Log.e(TAG,"error encountered while saving game", exception)
            Toast.makeText(this,"error encountered while saving game",Toast.LENGTH_SHORT).show()
            btnSave.isEnabled = true
        }
    }

    private fun handleImageUploading(gameName: String) {
        pbUploading.visibility = View.VISIBLE
        var didEncounterError = false
        val uploadedImageUrls = mutableListOf<String>()
        for ((index,photoUri) in chosenImageUri.withIndex()){
            val imageByteArray = getImageByteArray(photoUri)
            val filePath = "images/$gameName/${System.currentTimeMillis()}-${index}.jpg"
            val photoReference = storage.reference.child(filePath)
            photoReference.putBytes(imageByteArray)
                .continueWithTask{photoUploadTask ->
                    Log.i(TAG,"uploaded bytes: ${photoUploadTask.result?.bytesTransferred}")
                    photoReference.downloadUrl
                }.addOnCompleteListener{downloadUrlTask ->
                    if (!downloadUrlTask.isSuccessful){
                        Log.e(TAG,"exception with firebase storage",downloadUrlTask.exception)
                        Toast.makeText(this,"failed to upload image", Toast.LENGTH_LONG).show()
                        didEncounterError = true
                        return@addOnCompleteListener
                    }
                    if (didEncounterError){
                        pbUploading.visibility = View.GONE
                        return@addOnCompleteListener
                    }
                    val downloadUrl = downloadUrlTask.result.toString()
                    uploadedImageUrls.add(downloadUrl)
                    pbUploading.progress = uploadedImageUrls.size * 100 /chosenImageUri.size
                    Log.i(TAG,"finished uploading $photoUri, num uploading ${uploadedImageUrls.size}")
                    if (uploadedImageUrls.size == chosenImageUri.size){
                        handleAllImagesUploaded(gameName,uploadedImageUrls)
                    }
                }
        }
    }

    private fun handleAllImagesUploaded(
        gameName: String,
        imageUrls: MutableList<String>
    ) {
        // upload to fireStore
        db.collection("games").document(gameName)
            .set(mapOf("images" to imageUrls))
            .addOnCompleteListener { gameCreationTask ->
                pbUploading.visibility = View.GONE
                if (!gameCreationTask.isSuccessful){
                    Log.e(TAG,"exception with game creation",gameCreationTask.exception)
                    Toast.makeText(this,"failed game creation",Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }
                Log.i(TAG,"successfully created game $gameName")
                AlertDialog.Builder(this)
                    .setTitle("upload complete. Lets play your game $gameName")
                    .setPositiveButton("OK"){_,_ ->
                        val resultData = Intent()
                        resultData.putExtra(EXTRA_GAME_NAME, gameName)
                        setResult(Activity.RESULT_OK, resultData)
                        finish()
                    }.show()
            }

    }

    private fun getImageByteArray(photoUri: Uri): ByteArray {
        val originalBitmap = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
            val source = ImageDecoder.createSource(contentResolver, photoUri)
            ImageDecoder.decodeBitmap(source)
        }
        else{
            MediaStore.Images.Media.getBitmap(contentResolver,photoUri)
        }

        Log.i(TAG,"original width is ${originalBitmap.width} and height is ${originalBitmap.height}")
        val scaledBitmap = BitmapScaler.scaleToFitHeight(originalBitmap,250)
        Log.i(TAG,"scaled width is ${scaledBitmap.width} and height is ${scaledBitmap.height}")
        val byteOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG,60,byteOutputStream)
        return byteOutputStream.toByteArray()
    }


    private fun shouldEnableSaveButton(): Boolean {
        //
        if (chosenImageUri.size != numImagesRequired){
            return false
        }
        if (etGameName.text.isBlank() || etGameName.text.length < MIN_GAME_LENGTH){
            return false
        }
        return true
    }

    private fun launchIntentForPhotos() {
       val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(intent,"choose pics"),PICK_PHOTOS_CODE)

    }
}