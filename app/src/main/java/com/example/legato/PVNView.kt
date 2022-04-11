package com.example.legato

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color.rgb
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.pitch.PitchDetectionResult
import java.util.*
import java.util.stream.IntStream
import kotlin.math.ln

const val NO_VALUE = -1F
const val NO_VALUE_INT = -1
const val SPEED = 10f
const val STRETCH = 20f

open class Transcriber @JvmOverloads constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int = 0)
    : View(context, attrs, defStyleAttr){
    protected class InfoBlock(val id: Int, var frequency: Float, var volume: Double, val whetherNote: Boolean,
                              var noteBelong: Int = NO_VALUE_INT, var noteBelongStart: Int = NO_VALUE_INT)
    protected var infoList: MutableList<InfoBlock> = mutableListOf()
}

class PVNView  @JvmOverloads constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int = 0)
    : Transcriber(context, attrs, defStyleAttr){

    class Trunk(val id: Int, var x: Float, var y: Float,
                var alive: Boolean, var pitched: Boolean)

    class Note(val id: Int, var infoStart: Int, var infoEnd: Int, var radius: Float,
               var x: Float = NO_VALUE, var y: Float = NO_VALUE,
               var alive: Boolean = true)

    private var trunks: MutableList<Trunk> = mutableListOf()
    private var notes: MutableList<Note> = mutableListOf()
    private val paintFill = Paint().apply {
        isAntiAlias = true
        color = ContextCompat.getColor(context, R.color.black)
        style = Paint.Style.FILL_AND_STROKE
    }
    var gestureOperation: Boolean = false

    fun convertToInfo(result: PitchDetectionResult, e: AudioEvent, t: Int) {
        infoList.add(InfoBlock(t, result.pitch, e.getdBSPL(), result.isPitched))
        trunking(infoList[t])
        // 手机上出问题是因为onSizeChange之后, pitchdetection 失效了, t变成-1
        // notize(infoList[t])
        invalidate()
    }

    private fun trunking(ib: InfoBlock) {
        // each move forward a speed length
        trunks.add(Trunk(ib.id, this.width + SPEED / 2, heightCalc(ib.frequency), false, ib.whetherNote))
        trunks.forEach {
            it.x -= SPEED
            it.alive = !((it.x < 0) or (it.x > this.width))
        }
    }

    private fun trunkDraw(canvas: Canvas) {
        trunks.forEach {
            if (it.alive and it.pitched)
                canvas.drawRect(it.x - SPEED / 2, it.y - STRETCH / 2,
                        it.x + SPEED / 2, it.y + STRETCH / 2, paintFill)
        }
    }

    private fun heightCalc(freq: Float): Float {
        return if (freq > 20)
            (ln(440 / freq) / ln(2.0) * 12 * STRETCH + this.height / 3).toFloat()
        else
            -1f
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun colorCalculate(sub: Double): FloatArray {
        var c = FloatArray(3)
        val r: Array<Int> = arrayOf(50, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val g: Array<Int> = arrayOf(0, 0, 50, 100, 150, 200, 240, 180, 140, 100, 60, 20)
        val b: Array<Int> = arrayOf(200, 240, 0, 0, 0, 0, 0, 0, 0, 50, 100, 150)
        for (i in IntStream.range(0, 12)) {
            c[0] += ((1 / StrictMath.exp(StrictMath.abs(i - sub))) * r[i]).toFloat()
            c[1] += ((1 / StrictMath.exp(StrictMath.abs(i - sub))) * g[i]).toFloat()
            c[2] += ((1 / StrictMath.exp(StrictMath.abs(i - sub))) * b[i]).toFloat()
        }
        var s: Float = c[0] + c[1] + c[2];
        c[0] = c[0] / s * 255;
        c[1] = c[1] / s * 255;
        c[2] = c[2] / s * 255;
        return c;
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        trunkDraw(canvas)
    }

}

// ---------------------Editor Part------------------------------

const val DEBUG_TAG = "TEST"

class Editor: GestureDetector.SimpleOnGestureListener(){

    override fun onDown(event: MotionEvent): Boolean {
        Log.d(DEBUG_TAG, "onDown: $event")
        return true
    }

    override fun onFling(
            event1: MotionEvent,
            event2: MotionEvent,
            velocityX: Float,
            velocityY: Float
    ): Boolean {
        Log.d(DEBUG_TAG, "onFling: $event1 $event2")
        return true
    }

    override fun onLongPress(event: MotionEvent) {
        Log.d(DEBUG_TAG, "onLongPress: $event")
    }

    override fun onScroll(
            event1: MotionEvent,
            event2: MotionEvent,
            distanceX: Float,
            distanceY: Float
    ): Boolean {
        Log.d(DEBUG_TAG, "onScroll: $event1 $event2")
        return true
    }

    override fun onShowPress(event: MotionEvent) {
        Log.d(DEBUG_TAG, "onShowPress: $event")
    }

    override fun onSingleTapUp(event: MotionEvent): Boolean {
        Log.d(DEBUG_TAG, "onSingleTapUp: $event")
        return true
    }

    override fun onDoubleTap(event: MotionEvent): Boolean {
        Log.d(DEBUG_TAG, "onDoubleTap: $event")
        return true
    }

    override fun onDoubleTapEvent(event: MotionEvent): Boolean {
        Log.d(DEBUG_TAG, "onDoubleTapEvent: $event")
        return true
    }

    override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
        Log.d(DEBUG_TAG, "onSingleTapConfirmed: $event")
        return true
    }

}