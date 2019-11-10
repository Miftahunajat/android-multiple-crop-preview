package com.example.android_multiple_crop_preview

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.DisplayMetrics
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.abs


class MainActivity: AppCompatActivity() {

    private var lensFacing = CameraX.LensFacing.BACK
    lateinit var overlay: Bitmap
    private val rectangles: MutableList<Rect> = mutableListOf()
    private val bitmapResult: MutableList<Bitmap> = mutableListOf()
    private var numberOfRectangle = 1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selfPermission()

        // Every time the provided texture view changes, recompute layout
        texture.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }
    }

    private fun selfPermission(){
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this@MainActivity,
                Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity,
                    Manifest.permission.CAMERA)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this@MainActivity,
                    arrayOf(Manifest.permission.CAMERA),
                    1)

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            texture.post { startCamera() }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            1-> {
                texture.post { startCamera() }
            }
        }
    }

    private fun startCamera() {
        val metrics = DisplayMetrics().also { texture.display.getRealMetrics(it) }
        val screenSize = Size(metrics.widthPixels, metrics.heightPixels)
        val screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)

        val previewConfig = PreviewConfig.Builder().apply {
            setLensFacing(lensFacing)
            setTargetResolution(screenSize)
            setTargetAspectRatio(screenAspectRatio)
            setTargetRotation(windowManager.defaultDisplay.rotation)
            setTargetRotation(texture.display.rotation)
        }.build()

        val preview = Preview(previewConfig)
        preview.setOnPreviewOutputUpdateListener {
            texture.surfaceTexture = it.surfaceTexture
            updateTransform()
        }

        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            setImageReaderMode(
                ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
        }.build()

        val analyzerUseCase = ImageAnalysis(analyzerConfig).apply {
            analyzer = ImageAnalysis.Analyzer { image, rotationDegrees ->
                val bitmap = texture.bitmap ?: return@Analyzer
                overlay = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)


                GlobalScope.launch(Dispatchers.Unconfined) {
                    val canvas = buildRectangle(
                        overlay,
                        screenSize
                    )
                    Canvas(overlay).apply { canvas }

                }

                runOnUiThread {
                    imageView.setImageBitmap(overlay)
                }
            }
            }


            // Create configuration object for the image capture use case
            val imageCaptureConfig = ImageCaptureConfig.Builder()
                .apply {
                    setLensFacing(lensFacing)
                    setTargetAspectRatio(screenAspectRatio)
                    setTargetRotation(texture.display.rotation)
                    setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
                }.build()

            // Build the image capture use case and attach button click listener
            val imageCapture = ImageCapture(imageCaptureConfig)
            btn_minus.setOnClickListener { numberOfRectangle-- }
            btn_plus.setOnClickListener { numberOfRectangle++ }
            btn_take_picture.setOnClickListener {
                imageCapture.takePicture(
                    object : ImageCapture.OnImageCapturedListener() {
                        override fun onCaptureSuccess(image: ImageProxy?, rotationDegrees: Int) {

                            val buffer = image!!.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                            // 2 - Rotate the Bitmap
                            if (rotationDegrees != 0) {
                                val rotationMatrix = Matrix()
                                rotationMatrix.postRotate(rotationDegrees.toFloat())
                                bitmap = Bitmap.createBitmap(
                                    bitmap,
                                    0,
                                    0,
                                    bitmap.width,
                                    bitmap.height,
                                    rotationMatrix,
                                    true
                                )
                            }

                            // 3 - Crop the Bitmap
                            rectangles.forEach {
                                bitmapResult.add(
                                    Bitmap.createBitmap(bitmap, it.centerX(), it.centerY(), abs(it.right - it.left), abs(it.top - it.bottom))
                                )
                            }
                            processResult(bitmapResult)
                            super.onCaptureSuccess(image, rotationDegrees)

                        }
                    }
                )

//                val file = File(
//                    Environment.getExternalStorageDirectory().toString() + "app/src/main/java" +
//                            "${System.currentTimeMillis()}.jpg"
//                )
//
//                imageCapture.takePicture(file,
//                    object : ImageCapture.OnImageSavedListener {
//                        override fun onError(
//                            error: ImageCapture.UseCaseError,
//                            message: String, exc: Throwable?
//                        ) {
//                            val msg = "Photo capture failed: $message"
//                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
//                        }
//
//                        override fun onImageSaved(file: File) {
//                            val msg = "Photo capture successfully: ${file.absolutePath}"
//                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
//                        }
//                    })

            }
            CameraX.bindToLifecycle(this@MainActivity, preview, imageCapture, analyzerUseCase)

    }

    private fun processResult(bitmapResult: MutableList<Bitmap>) {

    }

    private fun buildRectangle(overlay: Bitmap, screenSize: Size): Canvas {
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = Color.YELLOW
            strokeWidth = 3f
        }

        val canvas = Canvas(overlay)
        rectangles.clear()

        // Don't Ask me It's magic
        val maxRight = screenSize.width - 60
        val width = maxRight - 60
        val top = 300
        val bottom = 500
        var currentLeft = 60
        var currentRight = width/ numberOfRectangle + currentLeft
        var currentWidth = currentRight
        for (i in 1..numberOfRectangle) {
//            if (i == numberOfRectangle) currentRight = maxRight
            val rect = Rect(currentLeft, top, currentRight, bottom)
            currentLeft = currentRight
            currentRight += currentWidth - 60
            rectangles.add(rect)
        }
        for (rect: Rect in rectangles){
            canvas.drawRect(
                rect.left.toFloat(),
                rect.top.toFloat(),
                rect.right.toFloat(),
                rect.bottom.toFloat(),
                paint
            )
        }
        return canvas
    }

    private fun updateTransform() {
        val matrix = Matrix()
        val centerX = texture.width / 2f
        val centerY = texture.height / 2f

        val rotationDegrees = when (texture.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)
        texture.setTransform(matrix)
    }
}
