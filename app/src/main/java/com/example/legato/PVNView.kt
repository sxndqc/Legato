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
import java.lang.Math.round
import java.util.*
import java.util.stream.IntStream
import kotlin.math.*

const val NO_VALUE = -1F
const val NO_VALUE_INT = -1
const val SPEED = 10f
const val STRETCH = 20f
const val ADJUST_Y = 1
const val NEIGHBOR = 5
const val REACT_RANGE = 100
const val NOTE_DISPOSITION = 5

abstract class Transcriber @JvmOverloads constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int = 0)
    : View(context, attrs, defStyleAttr){
    protected class InfoBlock(val id: Int, var frequency: Float, var volume: Double, val whetherNote: Boolean,
                              var noteBelong: Int = NO_VALUE_INT, var noteBelongStart: Int = NO_VALUE_INT)
    protected var infoList: MutableList<InfoBlock> = mutableListOf()
    var isForReplay: Boolean = false
}

class PVNView  @JvmOverloads constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int = 0)
    : Transcriber(context, attrs, defStyleAttr){

    class Trunk(val id: Int, var x: Float, var y: Float,
                var alive: Boolean, var pitched: Boolean,
                var volumeHeight: Float, var played: Boolean = false)
    class Note(val id: Int, var infoStart: Int, var infoEnd: Int, var radius: Float,
               var x: Float = NO_VALUE, var y: Float = NO_VALUE,
               var alive: Boolean, var played: Boolean = false)
    private var trunks: MutableList<Trunk> = mutableListOf()
    private var notes: MutableList<Note> = mutableListOf()
    private var isInTrunk: Boolean = true
    private val paintFill = Paint().apply {
        isAntiAlias = true
        color = ContextCompat.getColor(context, R.color.black)
        style = Paint.Style.FILL_AND_STROKE
    }
    private val paintSplitLine = Paint().apply {
        isAntiAlias = true
        color = ContextCompat.getColor(context, R.color.black)
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1f
    }
    private val paintPlayed = Paint().apply {
        isAntiAlias = true
        color = ContextCompat.getColor(context, R.color.black)
        style = Paint.Style.FILL_AND_STROKE
        alpha = 50
    }
    private val paintFillYellow = Paint().apply {
        isAntiAlias = true
        color = ContextCompat.getColor(context, R.color.yellow)
        style = Paint.Style.FILL_AND_STROKE
    }
    private val paintFillYellowPlayed = Paint().apply {
        isAntiAlias = true
        color = ContextCompat.getColor(context, R.color.yellow)
        style = Paint.Style.FILL_AND_STROKE
        alpha = 50
    }

    private var splitLine = 0f
    private var upperSplit = 0f
    private var lowerSplit = 0f
    private var lastIndexX = 0f

    private var currentNote: Int = -100

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        splitLine = this.width / 3f
        upperSplit = this.height / 5f
        lowerSplit = this.height * 4 / 5f
        lastIndexX = (this.width).toFloat()
    }

    fun convertToInfo(result: PitchDetectionResult, e: AudioEvent, t: Int) {
        infoList.add(InfoBlock(t, result.pitch, e.getdBSPL(), result.isPitched))
        trunking(infoList[t])
        noting(infoList[t])
        invalidate()
    }

    private fun trunking(ib: InfoBlock) {
        // each move forward a speed length
        // Log.d("TEST", "nums:  $splitLine , $upperSplit , $lowerSplit , $lastIndexX")
        trunks.add(Trunk(ib.id, this.width + SPEED / 2, heightCalc(ib.frequency), false,
                ib.whetherNote, volumeCalc(ib.volume)))
        trunks.forEach {
            it.x -= SPEED
            it.alive = !((it.x < 0) or (it.x > this.width))
            it.played = it.x < splitLine
        }
    }

    private fun noting(ib: InfoBlock){
        // if diverged from previous average for 1/12 scale, then counted as a separated note
        val thisNote = noteCalc(ib.frequency)
        if (thisNote!=currentNote){
            currentNote = thisNote
            notes.add(Note(notes.size, ib.id, ib.id, radiusCalc(0),
                    lastIndexX - NOTE_DISPOSITION, noteHeightCalc(currentNote), true, false))
        } else {
            notes.last().infoEnd = ib.id
            notes.last().radius = radiusCalc(notes.last().infoEnd - notes.last().infoStart)
        }

    }

    private fun trunkDraw(canvas: Canvas) {
        trunks.forEach {
            if (it.alive and it.pitched) {
                canvas.drawRect(it.x - SPEED / 2, it.y - STRETCH / 2,
                        it.x + SPEED / 2, it.y + STRETCH / 2,
                        paintFill)
                canvas.drawRect(it.x - SPEED / 2, this.height - it.volumeHeight,
                        it.x + SPEED / 2, this.height.toFloat(),
                        paintFillYellow)
            }
        }
    }

    private fun noteDraw(canvas: Canvas){

    }

    private fun trunkReplayDraw(canvas: Canvas) {
        canvas.drawLine(splitLine, upperSplit, splitLine, lowerSplit, paintSplitLine)
        trunks.forEach {
            if (it.alive and it.pitched) {
                canvas.drawRect(it.x - SPEED / 2, it.y - STRETCH / 2,
                        it.x + SPEED / 2, it.y + STRETCH / 2,
                        if (it.played) paintPlayed else paintFill)
                canvas.drawRect(it.x - SPEED / 2, this.height - it.volumeHeight,
                        it.x + SPEED / 2, this.height.toFloat(),
                        if (it.played) paintFillYellowPlayed else paintFillYellow)
            }
        }
    }

    private fun noteReplayDraw(canvas: Canvas){

    }

    fun moveBubbles(distanceX : Float){
        if (isInTrunk){
            trunks.forEach{
                it.x -= distanceX
                it.alive = !((it.x < 0) or (it.x > this.width))
                it.played = it.x < splitLine
            }
            lastIndexX -= distanceX
        } else {
            notes.forEach{
                it.x -= distanceX
                it.alive = !((it.x < 0) or (it.x > this.width))
                it.played = it.x < splitLine
            }
            lastIndexX -= distanceX
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

    private fun noteHeightCalc(theNote: Int) : Float{
        return if (theNote > -50)
            this.height / 3 + (-theNote) * STRETCH
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

    private fun noteCalc(freq: Float) : Int {
        return (log2(freq / 440) * 12).roundToInt()
    }

    private fun radiusCalc(length: Int) : Float {
        // this function can be renewable
        val base = 3f
        val multiplier = 10f
        val compressor = 10f
        return (ln(length * multiplier + 1)) / compressor + base
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
        if (isForReplay)
            if (isInTrunk)
                trunkReplayDraw(canvas)
            else
                noteReplayDraw(canvas)
        else
            if (isInTrunk)
                trunkDraw(canvas)
            else
                noteDraw(canvas)
    }

    fun test(){
        Log.d(DEBUG_TAG, "onDown")
    }

    fun setReplay(){
        isForReplay = true
        invalidate()
    }

    fun controlReplay(){
        // play block at splitLine
        val currentId: Int = ((splitLine - trunks[0].x) / SPEED).toInt()
        //Replay, how to quit?
        invalidate()
    }

    fun quitReplay(){
        isForReplay = false
        invalidate()
    }

    fun changeToNote(){
        isInTrunk = false

    }

    fun changeToTrunk(){
        isInTrunk = true

    }

}