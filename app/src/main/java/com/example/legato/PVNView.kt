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
import java.lang.Integer.max
import java.lang.Integer.min
import java.lang.Math.pow
import java.util.*
import java.util.stream.IntStream
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

const val NO_VALUE = -1F
const val NO_VALUE_INT = -1
const val SPEED = 10f
const val STRETCH = 20f
const val ADJUST_Y = 3
const val NEIGHBOR = 3
const val REACT_RANGE = 50

open class Transcriber @JvmOverloads constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int = 0)
    : View(context, attrs, defStyleAttr){
    protected class InfoBlock(val id: Int, var frequency: Float, var volume: Double, val whetherNote: Boolean,
                              var noteBelong: Int = NO_VALUE_INT, var noteBelongStart: Int = NO_VALUE_INT)
    protected var infoList: MutableList<InfoBlock> = mutableListOf()
}

class PVNView  @JvmOverloads constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int = 0)
    : Transcriber(context, attrs, defStyleAttr){

    class Trunk(val id: Int, var x: Float, var y: Float,
                var alive: Boolean, var pitched: Boolean, var volumeHeight: Float)

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
    private val paintFillYellow = Paint().apply {
        isAntiAlias = true
        color = ContextCompat.getColor(context, R.color.yellow)
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
        trunks.add(Trunk(ib.id, this.width + SPEED / 2, heightCalc(ib.frequency), false,
                ib.whetherNote, volumeCalc(ib.volume)))
        trunks.forEach {
            it.x -= SPEED
            it.alive = !((it.x < 0) or (it.x > this.width))
        }
    }

    private fun trunkDraw(canvas: Canvas) {
        trunks.forEach {
            if (it.alive and it.pitched) {
                canvas.drawRect(it.x - SPEED / 2, it.y - STRETCH / 2,
                        it.x + SPEED / 2, it.y + STRETCH / 2, paintFill)
                canvas.drawRect(it.x - SPEED / 2, this.height - it.volumeHeight,
                        it.x + SPEED / 2, this.height.toFloat(), paintFillYellow)
            }
        }
    }

    fun moveBubbles(distanceX : Float){
        trunks.forEach{
            it.x -= distanceX
            it.alive = !((it.x < 0) or (it.x > this.width))
        }
        invalidate()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun moveBlocks(x: Float, y: Float, distanceY: Float){
        // get the id, use exp
        val currentId: Int = ((x - trunks[0].x) / SPEED).toInt()

        if (y in trunks[currentId].y - REACT_RANGE .. trunks[currentId].y + REACT_RANGE){
            trunks[currentId].y -= distanceY / ADJUST_Y
            infoList[currentId].frequency = pitchCalc(trunks[currentId].y)
            for(i in 1..NEIGHBOR) {
                if (currentId - i >= 0) {
                    trunks[currentId - i].y -= distanceY / ADJUST_Y / exp(i.toDouble()).toFloat()
                    infoList[currentId - i].frequency = pitchCalc(trunks[currentId - i].y)
                }
                if (currentId + i <= trunks.lastIndex) {
                    trunks[currentId + i].y -= distanceY / ADJUST_Y / exp(i.toDouble()).toFloat()
                    infoList[currentId + i].frequency = pitchCalc(trunks[currentId + i].y)
                }
            }
        }

        if (y in trunks[currentId].volumeHeight - REACT_RANGE .. trunks[currentId].volumeHeight + REACT_RANGE){
            trunks[currentId].volumeHeight -= distanceY / ADJUST_Y
            infoList[currentId].volume = volumeBackCalc(trunks[currentId].volumeHeight)
            for(i in 1..NEIGHBOR) {
                if (currentId - i >= 0) {
                    trunks[currentId - i].volumeHeight -= distanceY / ADJUST_Y / exp(i.toDouble()).toFloat()
                    infoList[currentId - i].volume = volumeBackCalc(trunks[currentId - i].volumeHeight)
                }
                if (currentId + i <= trunks.lastIndex) {
                    trunks[currentId + i].volumeHeight -= distanceY / ADJUST_Y / exp(i.toDouble()).toFloat()
                    infoList[currentId + i].volume = volumeBackCalc(trunks[currentId + i].volumeHeight)
                }
            }
        }
        invalidate()
    }

    private fun heightCalc(freq: Float): Float {
        return if (freq > 20)
            (ln(440 / freq) / ln(2.0) * 12 * STRETCH + this.height / 3).toFloat()
        else
            -1f
    }

    private fun pitchCalc(y: Float): Float {
        return 440 / (2f.pow((y - this.height / 3) / STRETCH / 12))
    }

    private fun volumeCalc(volume: Double) : Float {
        return ((volume - (-80)) * 6).toFloat()
    }

    private fun volumeBackCalc(volumeHeight: Float): Double{
        return (volumeHeight / 6 - 80).toDouble()
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

    fun test(){
        Log.d(DEBUG_TAG, "onDown")
    }

}