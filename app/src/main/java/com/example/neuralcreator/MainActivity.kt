package com.example.neuralcreator

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnGenerateText).setOnClickListener {
            startActivity(Intent(this, GenerateTextActivity::class.java))
        }

        findViewById<Button>(R.id.btnGenerateImage).setOnClickListener {
            startActivity(Intent(this, GenerateImageActivity::class.java))
        }

        findViewById<Button>(R.id.btnClassification).setOnClickListener {
            startActivity(Intent(this, ImageClassificationActivity::class.java))
        }

    }
}
