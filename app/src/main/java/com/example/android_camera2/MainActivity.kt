package com.example.android_camera2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseArray
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.android_camera2.ui.theme.Android_camera2Theme
import java.io.File
import java.lang.Exception


class MainActivity : ComponentActivity() {

    private lateinit var btnCapture: Button
    private lateinit var textureView: TextureView
    var ORIENTATION = SparseArray<Int>()

    private var cameraId = ""
    private var cameraDevice: CameraDevice? = null
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var imageDimension: Size
    private lateinit var imageReader: ImageReader

    private lateinit var file: File
    private val REQUEST_CAMERA_PERMISSION = 200
    private var mFlashSupported = false
    private lateinit var mBackgroundHandler: Handler
    private lateinit var mBackgroundThread: HandlerThread

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ORIENTATION.append(Surface.ROTATION_0, 90)
        ORIENTATION.append(Surface.ROTATION_90, 0)
        ORIENTATION.append(Surface.ROTATION_180, 270)
        ORIENTATION.append(Surface.ROTATION_270, 180)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnCapture = findViewById(R.id.btnCapture)
        textureView = findViewById(R.id.textureView)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {

                openCamera()

            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            }

        }

        btnCapture.setOnClickListener {
            takePicture()
        }
    }

    private fun takePicture() {
        if (cameraDevice == null) return
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val characteristics: CameraCharacteristics =
                manager.getCameraCharacteristics(cameraDevice!!.id)

            val jpegSize: Array<Size>? =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(ImageFormat.JPEG)

            var width = 640
            var height = 480

            if (!jpegSize.isNullOrEmpty()) {
                width = jpegSize[0].width
                height = jpegSize[0].height
            }

            val reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
            val outputSurface = ArrayList<Surface>()
            outputSurface.add(reader.surface)
            outputSurface.add(Surface(textureView.surfaceTexture))


            val captureBuilder: CaptureRequest.Builder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(reader.surface)
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)


        } catch (e: Exception) {
            Log.e(TAG, "takePicture: e : ", e)

        }
    }

    private fun openCamera() {


    }
}
