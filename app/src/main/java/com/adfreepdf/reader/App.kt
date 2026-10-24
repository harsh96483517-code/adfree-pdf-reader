package com.adfreepdf.reader

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Initialises PDFBox so that all PDF processing (merge, split, compress,
 * annotate, image-to-PDF) works fully offline on the device.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
