package com.example.neuralcreator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import org.json.JSONObject

class ImageClassificationActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var textView: TextView
    private lateinit var selectImageButton: Button
    private lateinit var cameraButton: Button
    private lateinit var showMoreButton: Button
    private lateinit var interpreter: Interpreter
    private val imageSize = 224
    private lateinit var classLabels: Map<String, String>
    private var top5Results: List<Pair<String, Float>> = emptyList()

    companion object {
        private const val REQUEST_WRITE_STORAGE = 112
        private const val REQUEST_IMAGE_PICK = 1
        private const val REQUEST_IMAGE_CAPTURE = 2
        private const val REQUEST_CAMERA_PERMISSION = 113
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_classification)

        imageView = findViewById(R.id.imageView)
        textView = findViewById(R.id.textView)
        selectImageButton = findViewById(R.id.selectImageButton)
        cameraButton = findViewById(R.id.cameraButton)
        showMoreButton = findViewById(R.id.showMoreButton)

        try {
            interpreter = Interpreter(loadModelFile())
            classLabels = loadClassLabels()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        selectImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_IMAGE_PICK)
        }

        cameraButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            } else {
                openCamera()
            }
        }

        showMoreButton.setOnClickListener {
            toggleResults()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                openCamera()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            val bitmap = when (requestCode) {
                REQUEST_IMAGE_PICK -> {
                    val uri = data?.data
                    val inputStream = contentResolver.openInputStream(uri!!)
                    BitmapFactory.decodeStream(inputStream)
                }
                REQUEST_IMAGE_CAPTURE -> {
                    data?.extras?.get("data") as Bitmap
                }
                else -> null
            }

            bitmap?.let {
                imageView.setImageBitmap(it)
                val result = classifyImage(it)
                textView.text = result.first
                top5Results = result.second
                showMoreButton.visibility = View.VISIBLE
                showMoreButton.text = "Показать больше"
            }
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = assets.openFd("mobilenet_v2.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun classifyImage(bitmap: Bitmap): Pair<String, List<Pair<String, Float>>> {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true)
        val byteBuffer = convertBitmapToByteBuffer(resizedBitmap)
        val result = Array(1) { FloatArray(1000) }
        interpreter.run(byteBuffer, result)
        val top5 = getTop5Labels(result[0])
        val top1 = top5.first()
        return Pair("${top1.first}: ${String.format("%.2f", top1.second * 100)}%", top5)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(imageSize * imageSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for (i in 0 until imageSize) {
            for (j in 0 until imageSize) {
                val value = intValues[pixel++]
                byteBuffer.putFloat(((value shr 16 and 0xFF) - 128f) / 128f)
                byteBuffer.putFloat(((value shr 8 and 0xFF) - 128f) / 128f)
                byteBuffer.putFloat(((value and 0xFF) - 128f) / 128f)
            }
        }
        return byteBuffer
    }

    private fun loadClassLabels(): Map<String, String> {
        val labels = mutableMapOf<String, String>()
        val inputStream = assets.open("imagenet_class_index.json")
        val json = inputStream.bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(json)

        jsonObject.keys().forEach { key ->
            val item = jsonObject.getJSONArray(key)
            val label = item.getString(1)
            labels[key] = label
        }
        return labels
    }

    private fun getTop5Labels(probabilities: FloatArray): List<Pair<String, Float>> {
        val topIndices = probabilities
            .mapIndexed { index, fl -> index to fl }
            .sortedByDescending { it.second }
            .take(5)

        return topIndices.map { (index, probability) ->
            val label = classLabels[index.toString()] ?: "Unknown"
            Pair(label, probability)
        }
    }

    private fun showMoreResults() {
        val moreResults = top5Results.joinToString("\n") { "${it.first}: ${String.format("%.2f", it.second * 100)}%" }
        textView.text = moreResults
    }

    private fun showLessResults() {
        val top1 = top5Results.first()
        textView.text = "${top1.first}: ${String.format("%.2f", top1.second * 100)}%"
    }

    private fun toggleResults() {
        if (showMoreButton.text == "Показать больше") {
            showMoreResults()
            showMoreButton.text = "Показать меньше"
        } else {
            showLessResults()
            showMoreButton.text = "Показать больше"
        }
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(cameraIntent, REQUEST_IMAGE_CAPTURE)
    }
}
