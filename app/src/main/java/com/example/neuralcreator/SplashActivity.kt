package com.example.neuralcreator

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import android.widget.ProgressBar

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val progressBar: ProgressBar = findViewById(R.id.progressBar)
        progressBar.max = 100

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }, 3000)

        Thread {
            for (i in 0..100) {
                Thread.sleep(30)
                progressBar.progress = i
            }
        }.start()
    }
}
