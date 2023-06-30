package com.example.android_camera2

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.util.concurrent.HandlerExecutor
import java.io.File
import java.io.IOException
import kotlin.concurrent.timer


@SuppressLint("NewApi")
class MainActivity : ComponentActivity() {

    private lateinit var btnCapture: Button
    private lateinit var textureView: TextureView
    private var orientation = arrayListOf<Int>()

    private var cameraDevice: CameraDevice? = null
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var imageDimension: Size

    private lateinit var file: File
    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null

    var mIsRecordingVideo: Boolean = false

    private var mMediaRecorder: MediaRecorder? = null

    companion object {
        private const val TAG = "MainActivity"
        private val REQUEST_CAMERA_PERMISSION = 200
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
        }

    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        orientation.add(Surface.ROTATION_0, 90)
        orientation.add(Surface.ROTATION_90, 0)
        orientation.add(Surface.ROTATION_180, 270)
        orientation.add(Surface.ROTATION_270, 180)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnCapture = findViewById(R.id.btnCapture)
        textureView = findViewById(R.id.textureView)

        textureView.surfaceTextureListener = textureListener

        btnCapture.setOnClickListener {
            if (btnCapture.text == "stop") {
                stopRecordingVideo()
            } else {
                startRecordingVideo()
            }
        }

        mMediaRecorder = MediaRecorder()
    }

    private fun createCameraPreview() {
        try {

            val texture = textureView.surfaceTexture
            assert(texture != null)
            texture!!.setDefaultBufferSize(imageDimension.width, imageDimension.height)
            val surface = Surface(texture)
            captureRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
//            [15, 15], [24, 24], [7, 30], [30, 30], [7, 60], [60, 60]
            val range: Range<Int> = Range.create(30, 30)
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
            captureRequestBuilder.addTarget(surface)

            val stateCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    this@MainActivity.cameraCaptureSession = session
                    updatePreview()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(this@MainActivity, "Changed", Toast.LENGTH_SHORT).show()
                }

            }
            val sessionConfiguration = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR, listOf(
                    OutputConfiguration(
                        surface
                    )
                ), HandlerExecutor(mBackgroundThread!!.looper), stateCallback
            )
            cameraDevice!!.createCaptureSession(sessionConfiguration)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "createCameraPreview: CameraAccessException : ", e)
        }
    }

    private fun updatePreview() {
        if (cameraDevice == null) {
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show()
        }

//        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)

        val range: Range<Int> = Range.create(30, 30)
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
        try {
            cameraCaptureSession.setRepeatingRequest(
                captureRequestBuilder.build(),
                null,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun openCamera() {

        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            val cameraId = manager.cameraIdList[0]
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map: StreamConfigurationMap? =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            assert(map != null)
            imageDimension = map!!.getOutputSizes(SurfaceTexture::class.java)[0]

            val cameraPermission = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            val audioPermission = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!cameraPermission || !audioPermission) {

                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.RECORD_AUDIO,
                    ),
                    REQUEST_CAMERA_PERMISSION,
                )
                return
            }

            manager.openCamera(cameraId, cameraStateCallback, null)
        } catch (e: CameraAccessException) {

            e.printStackTrace()
            Log.e(TAG, "openCamera: CameraAccessException ", e)
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this@MainActivity, "권한을 허용해주세요", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }


    private fun startRecordingVideo() {
        if (null == cameraDevice || !textureView.isAvailable) {
            return
        }
        try {
            closePreviewSession()

            setUpMediaRecorder()
            val texture: SurfaceTexture = textureView.surfaceTexture!!
            texture.setDefaultBufferSize(imageDimension.width, imageDimension.height)
            captureRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            val surfaces: MutableList<Surface> = ArrayList()

            val previewSurface = Surface(texture)
            surfaces.add(previewSurface)
            captureRequestBuilder.addTarget(previewSurface)

            val recorderSurface = mMediaRecorder!!.surface
            surfaces.add(recorderSurface)
            captureRequestBuilder.addTarget(recorderSurface)

            val range: Range<Int> = Range.create(40, 50)
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)

            val configurations = arrayListOf<OutputConfiguration>()

            for (surface in surfaces) {
                configurations.add(OutputConfiguration(surface))
            }

            val sessionConfiguration = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                configurations.toList(),
                HandlerExecutor(mBackgroundThread!!.looper),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {

                        cameraCaptureSession = session
                        updatePreview()
                        runOnUiThread {
                            mIsRecordingVideo = true

                            mMediaRecorder!!.start()

                            btnCapture.text = "stop"

                            timer(initialDelay = 31000, period = 3000) {
                                if (mIsRecordingVideo) {
                                    this.cancel()
                                    stopRecordingVideo()
                                }

                            }
                        }

                        Toast.makeText(this@MainActivity, "촬영 시작", Toast.LENGTH_SHORT).show()
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        Toast.makeText(this@MainActivity, "Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            cameraDevice!!.createCaptureSession(sessionConfiguration)

        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Log.e(TAG, "startRecordingVideo: CameraAccessException e", e)
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(TAG, "startRecordingVideo: IOException e", e)

        }
    }

    private fun closePreviewSession() {
        cameraCaptureSession.close()
    }

    @Throws(IOException::class)
    private fun setUpMediaRecorder() {
        mMediaRecorder!!.apply {
            setMaxDuration(30000)

            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            // 워치에서 사용 가능한 경로
            val path = File("${Environment.getExternalStorageDirectory()}/ihpRecord")

            // 갤럭시에서 사용 가능한 경로
//            val path = File("/sdcard/download/ihpRecord")

            if (!path.exists()) {
                path.mkdirs()
            }
            val fileName = String.format("%d.mp4", System.currentTimeMillis())
            file = File(path, fileName)

            Log.d(TAG, "setUpMediaRecorder: filePath : ${file.path}")

            setOutputFile(file.absolutePath)
            setVideoEncodingBitRate(10000000)
//            setVideoFrameRate(24)
//            setCaptureRate(24.0)
            setVideoSize(imageDimension.width, imageDimension.height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            val rotation = this@MainActivity.display?.rotation ?: 0
            setOrientationHint(orientation[rotation])
            prepare()
        }

    }

    private fun stopRecordingVideo() {
        // UI
        try {
            mIsRecordingVideo = false
            btnCapture.text = "Record"
            mMediaRecorder!!.stop()
            mMediaRecorder!!.reset()
            updatePreview()
        } catch (e: CameraAccessException) {
            Log.e(TAG, "stopRecordingVideo: eeee : ", e)
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (textureView.isAvailable) {
            openCamera()
        } else {
            textureView.surfaceTextureListener = textureListener
        }
    }

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("Camera Background")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)

    }

    override fun onPause() {
        super.onPause()
        stopBackgroundThread()
    }

    private fun stopBackgroundThread() {
        try {

            mBackgroundThread?.quitSafely()
            mBackgroundThread?.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
            Log.e(TAG, "stopBackgroundThread: e ", e)
        }
    }
}
