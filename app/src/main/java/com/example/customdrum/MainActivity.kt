package com.example.customdrum

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var cvSpinTheWheel:CustomDrum
    private lateinit var btnDelete: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cvSpinTheWheel = findViewById(R.id.cv_SpinTheWheel)
        btnDelete = findViewById(R.id.btn_delete)

        btnDelete.setOnClickListener { cvSpinTheWheel.deleteContent() }
    }
}