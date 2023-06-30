package com.example.android_camera2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseArray
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Arrays
import java.util.UUID


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
    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null

    var mIsRecordingVideo: Boolean = false

    /**
     * MediaRecorder
     */
    private var mMediaRecorder: MediaRecorder? = null

    companion object {
        private const val TAG = "MainActivity"
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
        ORIENTATION.append(Surface.ROTATION_0, 90)
        ORIENTATION.append(Surface.ROTATION_90, 0)
        ORIENTATION.append(Surface.ROTATION_180, 270)
        ORIENTATION.append(Surface.ROTATION_270, 180)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnCapture = findViewById(R.id.btnCapture)
        textureView = findViewById(R.id.textureView)

        textureView.surfaceTextureListener = textureListener

        btnCapture.setOnClickListener {
            if (btnCapture.text == "stop") {
                stopRecordingVideo()
            }else{

            startRecordingVideo()
            }
//            takePicture()
        }

        mMediaRecorder = MediaRecorder()
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

            val rotation = windowManager.defaultDisplay.rotation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATION.get(rotation))

            val path = "/sdcard/download/${UUID.randomUUID()}.jpg"
//            val path = "${Environment.getExternalStorageDirectory()}/${UUID.randomUUID()}.jpg"
            file = File(path)
            val readerListener = ImageReader.OnImageAvailableListener {

                var image: Image? = null

                try {

                    image = reader.acquireLatestImage()
                    val buffer = image.planes[0].buffer
                    val byte = ByteArray(buffer.capacity())
                    buffer.get(byte)
                    Log.d(TAG, "takePicture: byte : $byte")
                    save(byte)
                } catch (e: FileNotFoundException) {
                    Log.e(TAG, "takePicture: FileNotFoundException ", e)
                    e.printStackTrace()
                } catch (e: IOException) {
                    Log.e(TAG, "takePicture: IOException ", e)
                    e.printStackTrace()
                } finally {
                    image?.close()
                }

            }


            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler)
            val captureListener = object : CameraCaptureSession.CaptureCallback() {

                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    Toast.makeText(this@MainActivity, "save : $file", Toast.LENGTH_SHORT).show()
                    createCameraPreview()
                }

            }

            val stateListener = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    try {
                        cameraCaptureSession = session
                        cameraCaptureSession.capture(
                            captureBuilder.build(),
                            captureListener,
                            mBackgroundHandler
                        )
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "onConfigured: e", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.d(TAG, "onConfigureFailed: session : $session")
                }

            }
            cameraDevice?.createCaptureSession(outputSurface, stateListener, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Log.e(TAG, "takePicture: CameraAccessException : ", e)

        }

    }

    private fun createCameraPreview() {
        try {

            val texture = textureView.surfaceTexture
            assert(texture != null)
            texture!!.setDefaultBufferSize(imageDimension.width, imageDimension.height)
            val surface = Surface(texture)
            captureRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
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
            cameraDevice!!.createCaptureSession(listOf(surface), stateCallback, null)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "createCameraPreview: CameraAccessException : ", e)
        }
    }

    private fun updatePreview() {
        if (cameraDevice == null) {
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show()
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
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

    private fun save(byte: ByteArray) {

        var outPutStream: OutputStream? = null
        try {
            outPutStream = FileOutputStream(file)
            outPutStream.write(byte)

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "save: e :", e)
        } finally {
            outPutStream?.close()
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

            // Set up Surface for the camera preview
            val previewSurface = Surface(texture)
            surfaces.add(previewSurface)
            captureRequestBuilder.addTarget(previewSurface)

            // Set up Surface for the MediaRecorder
            val recorderSurface = mMediaRecorder!!.surface
            surfaces.add(recorderSurface)
            captureRequestBuilder.addTarget(recorderSurface)

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            cameraDevice!!.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {

                        cameraCaptureSession = session
                        updatePreview()
                        runOnUiThread(Runnable { // UI
                            mIsRecordingVideo = true

                            // Start recording
                            mMediaRecorder!!.start()

                            btnCapture.text = "stop"
                        })

                        Toast.makeText(this@MainActivity, "촬영 시작", Toast.LENGTH_SHORT).show()
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        Toast.makeText(this@MainActivity, "Failed", Toast.LENGTH_SHORT).show()
                    }
                },
                mBackgroundHandler
            )
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
        mMediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
        mMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mMediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        val path = File("/sdcard/download/ihpRecord")
        if (!path.exists()) {
            path.mkdirs()
        }
        val fileName = String.format("%d.mp4", System.currentTimeMillis())
        file= File(path, fileName)

        val mNextVideoAbsolutePath = file.absolutePath
        mMediaRecorder!!.setOutputFile(mNextVideoAbsolutePath)
        mMediaRecorder!!.setVideoEncodingBitRate(10000000)
        mMediaRecorder!!.setVideoFrameRate(30)
        mMediaRecorder!!.setVideoSize(imageDimension.width, imageDimension.height)
        mMediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mMediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

        val rotation = windowManager.defaultDisplay.rotation
        mMediaRecorder!!.setOrientationHint(ORIENTATION.get(rotation))
        mMediaRecorder!!.prepare()
    }

    private fun stopRecordingVideo() {
        // UI
        try {
            mIsRecordingVideo = false
            btnCapture.text = "Record"
            // Stop recording
            mMediaRecorder!!.stop()
            mMediaRecorder!!.reset()
            Toast.makeText(
                this@MainActivity, "Video saved: ${file.path}",
                Toast.LENGTH_SHORT
            ).show()
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
