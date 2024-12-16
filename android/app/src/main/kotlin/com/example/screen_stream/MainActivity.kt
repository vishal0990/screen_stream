package com.example.screen_stream

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class MainActivity : FlutterActivity() {
    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private val methodChannelName = "com.yourapp/screen_capture"
    private val REQUEST_CODE = 1001 // Request code for screen capture permission

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun configureFlutterEngine(flutterEngine: io.flutter.embedding.engine.FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, methodChannelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startProjection" -> startProjection(result)
                    "captureScreen" -> captureScreen(result)
                    else -> result.notImplemented()
                }
            }
    }

    private fun startProjection(result: MethodChannel.Result) {
        val intent = mediaProjectionManager?.createScreenCaptureIntent()
        if (intent != null) {
            startActivityForResult(intent, REQUEST_CODE)
        } else {
            result.error("ERROR", "Unable to create screen capture intent", null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
                Log.i("ScreenCapture", "MediaProjection initialized successfully")
            } else {
                Log.e("ScreenCapture", "Permission denied or invalid result")
            }
        }
    }

    private fun captureScreen(result: MethodChannel.Result) {
        if (mediaProjection == null) {
            result.error("ERROR", "MediaProjection is not initialized", null)
            return
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val handlerThread = HandlerThread("ScreenCapture")
        handlerThread.start()
        val handler = Handler(handlerThread.looper)

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        // Create the virtual display
        val virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            imageReader.surface,
            null,
            handler
        )

        // Capture the screen
        handler.post {
            val image = imageReader.acquireLatestImage()
            if (image == null) {
                Log.e("ScreenCapture", "Failed to acquire latest image")
                result.error("ERROR", "Failed to capture image", null)
                imageReader.close()
                handlerThread.quitSafely()
                return@post
            }

            // Process the image
            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)

                // Compress and return the image
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val jpegBytes = outputStream.toByteArray()

                result.success(jpegBytes)
            } catch (e: Exception) {
                Log.e("ScreenCapture", "Error processing image", e)
                result.error("ERROR", "Error processing image", null)
            } finally {
                image.close()
                imageReader.close()
                virtualDisplay?.release()
                handlerThread.quitSafely()
            }
        }
    }

    private fun processImage(image: Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }
}
