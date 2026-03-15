package com.ludokid.neutech.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.ludokid.neutech.R
import com.ludokid.neutech.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Logo pulse animation
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
        binding.ivLogo.startAnimation(pulse)

        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        binding.tvAppName.startAnimation(fadeIn)
        binding.tvTagline.startAnimation(fadeIn)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }, 2500)
    }
}
