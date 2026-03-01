package com.jucha.acometidasapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jucha.acometidasapp.core.navigation.AppNavGraph
import com.jucha.acometidasapp.core.theme.AcometidasTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PDFBoxResourceLoader.init(applicationContext)

        setContent {
            AcometidasTheme {
                AppNavGraph()
            }
        }
    }
}