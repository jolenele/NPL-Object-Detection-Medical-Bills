package com.example.npl_object_detection_medical_bills

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.Locale

import java.net.HttpURLConnection
import java.io.*
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*
import android.graphics.BitmapFactory
import android.os.Debug
import android.util.Log


//import android.Manifest
//import android.content.pm.PackageManager
//import android.graphics.Bitmap
//import android.os.Bundle
//import android.speech.tts.TextToSpeech
//import android.view.View
//import android.widget.*
//import androidx.activity.enableEdgeToEdge
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs
//import org.opencv.android.CameraBridgeViewBase
//import org.opencv.android.OpenCVLoader
//import org.opencv.android.Utils
//import org.opencv.core.CvType
//import org.opencv.core.Mat
//import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
//import java.util.Locale

class MainActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {
    private lateinit var textViewStatus: TextView
    private var isOpenCvInitialized = false

    private val cameraPermissionRequestCode = 100

    private lateinit var buttonStartPreview: Button
    private lateinit var buttonTextToSpeech: Button
    private lateinit var buttonStopPreview: Button
    private lateinit var checkBoxProcessing: CheckBox
    private lateinit var imageView: ImageView
    private lateinit var openCvCameraView: CameraBridgeViewBase

    private val roboflowApiKey = "C5VtmRIJ0FEVWW0zgY1X"
    private val modelEndpoint = "https://detect.roboflow.com/receipt_detecttion-fasym/1?api_key=$roboflowApiKey"

    private var isPreviewActive = false

    private lateinit var inputMat: Mat

    private lateinit var processedMat: Mat

    var t1: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        textViewStatus = findViewById(R.id.textViewStatus)
        buttonStartPreview = findViewById(R.id.buttonStartPreview)
        buttonTextToSpeech = findViewById(R.id.buttonTextToSpeech)
        buttonStopPreview = findViewById(R.id.buttonStopPreview)
        checkBoxProcessing = findViewById(R.id.checkboxEnableProcessing)
        imageView = findViewById(R.id.imageView)
        openCvCameraView = findViewById(R.id.cameraView)

        isOpenCvInitialized = OpenCVLoader.initLocal()

        // Request access to camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                cameraPermissionRequestCode
            )
        }

        openCvCameraView.setCameraIndex(0)
        openCvCameraView.setCvCameraViewListener(this)

        buttonStartPreview.setOnClickListener {
            openCvCameraView.setCameraPermissionGranted()
            openCvCameraView.enableView()

            updateControls()
        }

        buttonStopPreview.setOnClickListener {
            openCvCameraView.disableView()

            updateControls()
        }

        // Text to Speech
        t1 = TextToSpeech(
            applicationContext
        ) { status ->
            if (status != TextToSpeech.ERROR) {
                t1!!.setLanguage(Locale.UK)
            }
        }

        buttonTextToSpeech.setOnClickListener(View.OnClickListener {
            processFrameWithRoboflow()
            val toSpeak: String = textViewStatus.getText().toString()
            Toast.makeText(applicationContext, toSpeak, Toast.LENGTH_SHORT).show()
            t1!!.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null)
        })

        updateControls()
    }

    public override fun onPause() {
        if (t1 != null) {
            t1!!.stop()
            t1!!.shutdown()
        }
        super.onPause()
    }

    private fun updateControls() {
        if(!isOpenCvInitialized) {
            textViewStatus.text = "OpenCV initialization error"

            buttonStartPreview.isEnabled = false;
            buttonStopPreview.isEnabled = false;
        } else {
            textViewStatus.text = "OpenCV initialized"

            buttonStartPreview.isEnabled = !isPreviewActive;
            buttonStopPreview.isEnabled = isPreviewActive;
        }
    }

    private fun processFrameWithRoboflow() {
//4

//        val apiKey = "YOUR_API_KEY" // Replace with your actual API key
//        val modelEndpoint = "https://detect.roboflow.com/receipt_detecttion-fasym/1?api_key=$apiKey"

        try {
            // Encode `inputMat` to JPEG
            val encodedImage = MatOfByte()
            Imgcodecs.imencode(".jpg", inputMat, encodedImage)
            val jpegBytes = encodedImage.toArray()

            // Base64 encode the JPEG bytes
            val encodedFile = Base64.getEncoder().encodeToString(jpegBytes)

            // Create the HTTP connection
            val url = URL(modelEndpoint)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true

            // Send the Base64-encoded image
            val postData = "image=$encodedFile"
            connection.outputStream.use { it.write(postData.toByteArray(StandardCharsets.UTF_8)) }

            // Read the response
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val responseJson = JSONObject(response)

            Log.d("tag", response)
            // Extract predictions from the JSON response
            val predictions = responseJson.optJSONArray("predictions")?.toString() ?: "No predictions found"

            // Update UI with predictions
            runOnUiThread {
                textViewStatus.text = predictions
            }

        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }




    override fun onCameraViewStarted(width: Int, height: Int) {
        isPreviewActive = true

        inputMat = Mat(height, width, CvType.CV_8UC4)
        processedMat = Mat(height, width, CvType.CV_8UC1)

        updateControls()
    }

    override fun onCameraViewStopped() {
        isPreviewActive = false

        inputMat.release()
        processedMat.release()

        updateControls()
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        // Ensure inputFrame is not null
        if (inputFrame == null) {
            return Mat() // Return an empty Mat if inputFrame is null
        }

        // Copy the RGBA frame into inputMat
        inputFrame.rgba().copyTo(inputMat)

        // Apply processing if the checkbox is checked
        var matToDisplay = inputMat
        if (checkBoxProcessing.isChecked) {
            Imgproc.cvtColor(inputMat, processedMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.adaptiveThreshold(
                processedMat, processedMat, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY, 21, 0.0
            )
            matToDisplay = processedMat
        }

        // Encode the Mat to a byte array (JPG or PNG)
        val encodedImage = MatOfByte()
        Imgcodecs.imencode(".jpg", matToDisplay, encodedImage) // Use ".png" for PNG format

        // Convert the encoded MatOfByte to a byte array
        val imageBytes = encodedImage.toArray()

        // Optionally, display the encoded image in an ImageView for debugging
        val bitmapToDisplay = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        runOnUiThread {
            imageView.setImageBitmap(bitmapToDisplay)
        }

        // Return the processed Mat (optional, depending on your requirements)
        return matToDisplay
    }


    fun getEncodedImage(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): ByteArray? {
        inputFrame?.rgba()?.copyTo(inputMat)

        if (inputMat.empty()) return null

        val encodedImage = MatOfByte()
        Imgcodecs.imencode(".jpg", inputMat, encodedImage)
        return encodedImage.toArray()
    }

}