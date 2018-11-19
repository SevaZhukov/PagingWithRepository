package com.memebattle.pagingwithrepository.presentation

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.memebattle.pagingwithrepository.R

class MainActivity : AppCompatActivity() {

    lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
