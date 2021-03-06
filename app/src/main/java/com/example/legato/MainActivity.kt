package com.example.legato

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GestureDetectorCompat
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import kotlin.properties.Delegates

const val REQUEST_CODE = 200
const val SAMPLING_RATE = 22050
const val BLOCK_SIZE = 2048
const val BLOCK_OVERLAP = 0

class MainActivity : AppCompatActivity() {

    private var permissionGranted = false
    private var listeningThreadRunning by Delegates.notNull<Boolean>()
    private var cnt = 0
    private var frames = -1
    private var listeningProcessing by Delegates.notNull<Boolean>()
    private lateinit var dispatcher: AudioDispatcher
    private lateinit var p: PitchProcessor
    private lateinit var mDetector: GestureDetectorCompat
    private lateinit var editor: Editor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        listeningThreadRunning = false
        listeningProcessing = false

//        val times = findViewById<TextView>(R.id.tvTimes)
//        times.text = cnt.toString()
        val theFrame = findViewById<PVNView>(R.id.theFrame)
        editor = Editor(false, theFrame)
        mDetector = GestureDetectorCompat(this, editor)

        val btn = findViewById<Button>(R.id.btnStartStop)
        btn.setOnClickListener {
            if (listeningProcessing){
                stopRecording()
                theFrame.setReplay()
            } else {
                startRecording()
                theFrame.quitReplay()
            }
        }
        val btnClear = findViewById<Button>(R.id.btnClear)
        btnClear.setOnClickListener{
            recreate()
        }

        val btnUndo = findViewById<Button>(R.id.btnUndo)
        btnUndo.setOnClickListener{
            theFrame.undo()
        }

        val toggleNT: ToggleButton = findViewById(R.id.btnNote)
        toggleNT.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                theFrame.changeToNote()
            } else {
                // The toggle is disabled
                theFrame.changeToTrunk()
            }
        }

        val toggleGrid: ToggleButton = findViewById(R.id.btnGrid)
        toggleGrid.setOnCheckedChangeListener { _, isChecked ->
            theFrame.grid = isChecked
            theFrame.invalidate()
        }

        val toggleSYN: ToggleButton = findViewById(R.id.btnSYN)
        toggleSYN.setOnCheckedChangeListener { _, isChecked ->
            theFrame.synthesisOrOST = isChecked
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        mDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStart() {
        super.onStart()
        val info: PackageInfo = packageManager.getPackageInfo(this.packageName, PackageManager.GET_PERMISSIONS)
        val permissions: Array<String> = info.requestedPermissions
        permissionGranted = ActivityCompat.checkSelfPermission(this, permissions[0]) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) {
            if (!listeningThreadRunning) {
                prepareRecording()
            }
        } else {
//            val herc = findViewById<TextView>(R.id.tvHerz)
//            "No Audio Permission".also { herc.text = it }
            requestPermissions(permissions,
                    REQUEST_CODE
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE) {
            permissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED
            if(permissionGranted){
                prepareRecording()
            } else {
                val btnSS = findViewById<TextView>(R.id.btnStartStop)
                R.string.noAudioPermission.also { btnSS.text = it.toString() }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (listeningThreadRunning and listeningProcessing) {
            dispatcher.removeAudioProcessor(p)
            listeningProcessing = false
//            val times = findViewById<TextView>(R.id.tvTimes)
            cnt += 1
//            times.text = cnt.toString()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun prepareRecording(){
        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLING_RATE, BLOCK_SIZE, BLOCK_OVERLAP)
        val theFrame = findViewById<PVNView>(R.id.theFrame)
        //theFrame.format = dispatcher.format as AudioFormat
        // dispatcher is a Runnable that plays a file and send whole to processor
        // the process is a processor that send each buffer to a handler
        // the handler contains a Runnable which handle every single pitch
        val pdh = PitchDetectionHandler { result, e ->
            //val pitchInHz = result.pitch
            runOnUiThread {
                frames += 1
//                val herc = findViewById<TextView>(R.id.tvHerz)
//                val timestamp = findViewById<TextView>(R.id.tvTotalTime)
//                val theFrames = findViewById<TextView>(R.id.tvFrames)
//                val dbspl = findViewById<TextView>(R.id.tvDB)
//                herc.text = pitchInHz.toString()
//                timestamp.text = e.timeStamp.toString()
//                theFrames.text = frames.toString()
//                dbspl.text = e.getdBSPL().toString()
                theFrame.convertToInfo(result, e, frames)
            }
        }
        p = PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.MPM, SAMPLING_RATE.toFloat(), BLOCK_SIZE, pdh)

        Thread(dispatcher, "Audio Dispatcher").start()
        listeningThreadRunning = true

    }

    private fun startRecording() {
        if (listeningThreadRunning) {
            dispatcher.addAudioProcessor(p)
            listeningProcessing = true
//            val times = findViewById<TextView>(R.id.tvTimes)
            cnt += 1
//            times.text = cnt.toString()
            val btn = findViewById<Button>(R.id.btnStartStop)
            btn.text = getString(R.string.buttonTextPause)
            editor.editorValid = false
        }
    }

    private fun stopRecording() {
        if (listeningThreadRunning) {
            dispatcher.removeAudioProcessor(p)
            listeningProcessing = false
 //           val times = findViewById<TextView>(R.id.tvTimes)
            cnt += 1
 //           times.text = cnt.toString()
            val btn = findViewById<Button>(R.id.btnStartStop)
            btn.text = getString(R.string.buttonTextStart)
            editor.editorValid = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dispatcher.stop()
        val toast = Toast.makeText(this, "Drawn", Toast.LENGTH_LONG)
        toast.show()
    }

}