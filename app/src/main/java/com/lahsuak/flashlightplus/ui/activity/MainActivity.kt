package com.lahsuak.flashlightplus.ui.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.lahsuak.flashlightplus.R
import com.lahsuak.flashlightplus.databinding.ActivityMainBinding
import com.lahsuak.flashlightplus.ui.fragments.HomeFragment.Companion.screenState

class MainActivity :AppCompatActivity(){
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var reviewManager: ReviewManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        reviewManager = ReviewManagerFactory.create(this)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.container) as NavHostFragment
        navController = navHostFragment.navController
        setupActionBarWithNavController(navController)//,appBarConfiguration)

    }
    override fun onSupportNavigateUp(): Boolean {
        //Pass argument appBarConfiguration in navigateUp() method
        // for hamburger icon respond to click events
        //navConfiguration
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
    private fun showRateApp() {
        Log.d("TAG", "showRateApp: ")
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("TAG", "successful ")
                val reviewInfo = task.result
                val flow = reviewManager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener {
                    //nothing to do
                }
            }
        }
    }
    override fun onBackPressed() {
        showRateApp()
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        screenState = false
    }
}