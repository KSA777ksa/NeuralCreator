package com.example.neuralcreator

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.ByteArrayOutputStream
import java.io.InputStream

class GenerateTextActivity : AppCompatActivity() {

    private val PICK_IMAGE = 1
    private val REQUEST_IMAGE_CAPTURE = 2
    private lateinit var imageView: ImageView
    private lateinit var textView: TextView
    private lateinit var progressBar: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate_text)

        imageView = findViewById(R.id.imageView)
        textView = findViewById(R.id.textView)
        progressBar = findViewById(R.id.progressBar)
        val btnUpload = findViewById<Button>(R.id.btnUpload)
        val btnOpenCamera = findViewById<Button>(R.id.btnOpenCamera)
        val btnCopyText = findViewById<Button>(R.id.btnCopyText)

        btnUpload.setOnClickListener {
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE)
        }

        btnOpenCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), REQUEST_IMAGE_CAPTURE)
            } else {
                val takePictureIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                if (takePictureIntent.resolveActivity(packageManager) != null) {
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }

        btnCopyText.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Generated Text", textView.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Текст скопирован в буфер обмена", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_IMAGE_CAPTURE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            val takePictureIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            if (takePictureIntent.resolveActivity(packageManager) != null) {
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
            }
        } else {
            Toast.makeText(this, "Разрешение на использование камеры отклонено", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            val imageUri = data?.data ?: return

            val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            imageView.setImageBitmap(bitmap)

            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()

            uploadImage(byteArray)
        } else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            val extras = data?.extras
            val imageBitmap = extras?.get("data") as Bitmap
            imageView.setImageBitmap(imageBitmap)

            val byteArrayOutputStream = ByteArrayOutputStream()
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()

            uploadImage(byteArray)
        }
    }

    private fun uploadImage(byteArray: ByteArray) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://76ba-46-191-176-55.ngrok-free.app/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkHttpClient())
            .build()

        val service = retrofit.create(ApiService::class.java)
        val requestFile = RequestBody.create("image/jpeg".toMediaTypeOrNull(), byteArray)
        val body = MultipartBody.Part.createFormData("image", "image.jpg", requestFile)

        progressBar.visibility = View.VISIBLE

        val call = service.uploadImage(body)
        call.enqueue(object : Callback<CaptionResponse> {
            override fun onResponse(call: Call<CaptionResponse>, response: Response<CaptionResponse>) {
                progressBar.visibility = View.GONE
                if (response.isSuccessful) {
                    textView.text = response.body()?.caption ?: "No caption generated"
                } else {
                    textView.text = "Failed to get caption"
                }
            }

            override fun onFailure(call: Call<CaptionResponse>, t: Throwable) {
                progressBar.visibility = View.GONE
                textView.text = "Error: ${t.message}"
                Log.e("GenerateTextActivity", "Error: ${t.message}", t)
            }
        })
    }

    interface ApiService {
        @Multipart
        @POST("/generate_caption")
        fun uploadImage(@Part image: MultipartBody.Part): Call<CaptionResponse>
    }

    data class CaptionResponse(val caption: String)
}
