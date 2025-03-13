package com.example.cameratest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity

import androidx.core.app.ActivityCompat


class MultiCameraMainActivity : ComponentActivity() {

    private lateinit var cameraManager: CameraManager
    private var cameraIds: List<String> = listOf()
    private var uvcCameraIds: ArrayList<String> = arrayListOf()

    private lateinit var textureView1: TextureView
    private lateinit var textureView2: TextureView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_multi_camera)

        textureView1 = findViewById(R.id.textureView1)
        textureView2 = findViewById(R.id.textureView2)

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        findUvcCamera()
        cameraIds = cameraManager.cameraIdList.toList()

        if (cameraIds.size >= 2) {
            startCamera("0", textureView1)
            startCamera("102", textureView2)
        } else {
            Log.e(TAG, "Device does not support multiple cameras")
        }

    }

    private fun findUvcCamera() {
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                    uvcCameraIds.add(id)
                    Log.d(TAG, "Found UVC Camera: $id")
                    val previewSizes = getSupportedPreviewSizes(id, cameraManager)
                    previewSizes.forEach {
                        Log.d(TAG, "Supported Preview Size: ${it.width} x ${it.height}")
                    }
                    return
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun getSupportedPreviewSizes(cameraId: String, cameraManager: CameraManager): List<Size> {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        return streamConfigurationMap?.getOutputSizes(SurfaceTexture::class.java)?.toList() ?: emptyList()
    }

    private fun startCamera(cameraId: String, textureView: TextureView) {
        if (textureView.isAvailable) {
            setupCamera(cameraId, textureView)
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    setupCamera(cameraId, textureView)
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    return false
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    private fun setupCamera(cameraId: String, textureView: TextureView) {
        val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        val streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val previewSize = streamConfigurationMap!!.getOutputSizes(SurfaceTexture::class.java)[0]

        val cameraHandler = Handler(Looper.getMainLooper())
        val cameraThread = HandlerThread("CameraThread").apply { start() }
        val cameraBackgroundHandler = Handler(cameraThread.looper)

        val stateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                val surfaceTexture = textureView.surfaceTexture
                if (surfaceTexture == null) {
                    Log.e(TAG, "SurfaceTexture is null!")
                    return
                }
                surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
                val surface = Surface(surfaceTexture)

                val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(surface)
                }

                camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        session.setRepeatingRequest(captureRequestBuilder.build(), null, cameraBackgroundHandler)
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Camera configuration failed")
                    }
                }, cameraBackgroundHandler)
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "Camera error: $error")
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        cameraManager.openCamera(cameraId, stateCallback, cameraHandler)
    }

    companion object {
        const val TAG = "MultiCameraMainActivity"
    }

}
