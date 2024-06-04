package com.example.neuralcreator

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.squareup.picasso.Callback as PicassoCallback
import com.squareup.picasso.Picasso
import com.yalantis.ucrop.UCrop
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.Callback as RetrofitCallback
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class GenerateImageActivity : AppCompatActivity() {

    private lateinit var editTextPrompt: EditText
    private lateinit var imageView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSaveImage: Button
    private lateinit var btnCropImage: Button
    private var imageBitmap: Bitmap? = null
    private var imageUri: Uri? = null
    private var originalImageUri: Uri? = null

    companion object {
        private const val REQUEST_WRITE_STORAGE = 112
        private const val UCROP_REQUEST_CODE = 69
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate_image)

        editTextPrompt = findViewById(R.id.editTextPrompt)
        imageView = findViewById(R.id.imageView)
        progressBar = findViewById(R.id.progressBar)
        val btnGenerateImage = findViewById<Button>(R.id.btnGenerateImage)
        btnSaveImage = findViewById(R.id.btnSaveImage)
        btnCropImage = findViewById(R.id.btnCropImage)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_WRITE_STORAGE)
        }

        btnGenerateImage.setOnClickListener {
            val prompt = editTextPrompt.text.toString()
            if (prompt.isNotEmpty()) {
                generateImage(prompt)
            } else {
                Toast.makeText(this, "Please enter a prompt", Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveImage.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_WRITE_STORAGE)
            } else {
                imageBitmap?.let {
                    saveImageToStorage(it)
                } ?: Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show()
            }
        }

        btnCropImage.setOnClickListener {
            imageUri?.let {
                startCrop(it)
            } ?: Toast.makeText(this, "No image to crop", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                imageBitmap?.let {
                    saveImageToStorage(it)
                }
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateImage(prompt: String) {
        val client = OkHttpClient.Builder()
            .readTimeout(120, TimeUnit.SECONDS)
            .connectTimeout(120, TimeUnit.SECONDS)
            .hostnameVerifier { _, _ -> true }  // Разрешить все сертификаты для разработки
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://efaa-46-191-176-55.ngrok-free.app/")  // Замените на актуальный URL ngrok
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        val service = retrofit.create(ApiService::class.java)
        val call = service.generateImage(GenerateImageRequest(prompt))

        progressBar.visibility = View.VISIBLE

        call.enqueue(object : RetrofitCallback<GenerateImageResponse> {
            override fun onResponse(call: Call<GenerateImageResponse>, response: Response<GenerateImageResponse>) {
                if (response.isSuccessful) {
                    val imageUrl = response.body()?.image_path
                    if (imageUrl != null) {
                        Log.d("GenerateImageActivity", "Image URL: $imageUrl")
                        loadImageFromUrl(imageUrl)
                    } else {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@GenerateImageActivity, "Failed to load image", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@GenerateImageActivity, "Failed to generate image", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<GenerateImageResponse>, t: Throwable) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@GenerateImageActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("GenerateImageActivity", "Error: ${t.message}", t)
            }
        })
    }

    private fun loadImageFromUrl(imageUrl: String) {
        runOnUiThread {
            progressBar.visibility = View.VISIBLE
            Picasso.get().invalidate(imageUrl)
            Picasso.get()
                .load(imageUrl)
                .resize(512, 512)
                .centerInside()
                .into(imageView, object : PicassoCallback {
                    override fun onSuccess() {
                        progressBar.visibility = View.GONE
                        imageView.visibility = View.VISIBLE
                        btnSaveImage.visibility = View.VISIBLE
                        btnCropImage.visibility = View.VISIBLE
                        imageBitmap = (imageView.drawable as BitmapDrawable).bitmap
                        imageUri = getImageUri(imageBitmap!!)
                        originalImageUri = imageUri
                    }

                    override fun onError(e: Exception?) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@GenerateImageActivity, "Failed to load image", Toast.LENGTH_SHORT).show()
                        e?.printStackTrace()
                    }
                })
        }
    }

    private fun saveImageToStorage(bitmap: Bitmap) {
        val filename = "Generated_Image_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            contentResolver?.also { resolver ->
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val imageUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val image = File(downloadsDir, filename)
            fos = FileOutputStream(image)
        }

        fos?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            Toast.makeText(this, "Image saved to Downloads", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCrop(uri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, "croppedImage.png"))
        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(true) // Позволяет пользователю свободно выбирать область для обрезки
        }
        UCrop.of(uri, destinationUri)
            .withAspectRatio(0f, 0f) // Устанавливаем свободный аспект
            .withMaxResultSize(512, 512)
            .withOptions(options)
            .start(this, UCROP_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UCROP_REQUEST_CODE && resultCode == RESULT_OK) {
            val resultUri = UCrop.getOutput(data!!)
            if (resultUri != null) {
                imageView.setImageURI(resultUri)
                imageBitmap = MediaStore.Images.Media.getBitmap(contentResolver, resultUri)
                imageUri = resultUri

                // Удаление оригинального файла
                originalImageUri?.let { uri ->
                    contentResolver.delete(uri, null, null)
                }
                originalImageUri = null
            } else {
                Toast.makeText(this, "Crop failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getImageUri(bitmap: Bitmap): Uri {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "TempImage", null)
        return Uri.parse(path)
    }

    interface ApiService {
        @Headers("Content-Type: application/json")
        @POST("generate")
        fun generateImage(@Body request: GenerateImageRequest): Call<GenerateImageResponse>
    }

    data class GenerateImageRequest(val prompt: String)

    data class GenerateImageResponse(val image_path: String)
}
