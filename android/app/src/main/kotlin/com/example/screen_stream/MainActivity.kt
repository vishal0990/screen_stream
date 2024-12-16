package com.example.screen_stream

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class MainActivity : FlutterActivity() {
    private var mediaProjection: MediaProjection? = null
    private val methodChannelName = "com.yourapp/screen_capture"

    override fun configureFlutterEngine(flutterEngine: io.flutter.embedding.engine.FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, methodChannelName)
            .setMethodCallHandler { call, result ->
                if (call.method == "captureScreen") {
                    captureScreen(result)
                } else {
                    result.notImplemented()
                }
            }
    }

    private fun captureScreen(result: MethodChannel.Result) {
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels

        val handlerThread = HandlerThread("ScreenCapture")
        handlerThread.start()
        val handler = Handler(handlerThread.looper)

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            imageReader.surface,
            null,
            handler
        )

        handler.post {
            val image = imageReader.acquireLatestImage()
            if (image != null) {
                val bitmap = processImage(image)
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val jpegBytes = stream.toByteArray()

                result.success(jpegBytes)

                image.close()
                imageReader.close()
            } else {
                result.error("ERROR", "Failed to capture image  ", null)
            }
            handlerThread.quitSafely()
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
