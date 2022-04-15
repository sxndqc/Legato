package com.example.legato

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Color.RED
import android.graphics.Color.rgb
import android.graphics.Paint
import android.icu.text.IDNA
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
import androidx.core.content.res.ResourcesCompat
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
const val NEIGHBOR = 10
const val REACT_RANGE = 100
const val NOTE_DISPOSITION = 5
const val NOTE_MOVE = 50

abstract class Transcriber @JvmOverloads constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int = 0)
    : View(context, attrs, defStyleAttr){
    protected class InfoBlock(val id: Int, var frequency: Float, var volume: Double, val isANote: Boolean,
                              var noteBelong: Int = NO_VALUE_INT, var noteBelongStart: Int = NO_VALUE_INT)
    protected var infoList: MutableList<InfoBlock> = mutableListOf()
    var isForReplay: Boolean = false
    var grid : Boolean = false
}

class PVNView  @JvmOverloads constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int = 0)
    : Transcriber(context, attrs, defStyleAttr){

    class Trunk(val id: Int, var x: Float, var y: Float,
                var alive: Boolean, var pitched: Boolean,
                var volumeHeight: Float, var played: Boolean = false)
    class Note(val id: Int, var thisNote: Int, var infoStart: Int, var infoEnd: Int, var radius: Float,
               var x: Float = NO_VALUE, var y: Float = NO_VALUE, var isRest: Boolean,
               var alive: Boolean, var played: Boolean = false, var lastNote: Int = -150)
    private var trunks: MutableList<Trunk> = mutableListOf()
    private var notes: MutableList<Note> = mutableListOf()
    private var isInTrunk: Boolean = true
    private val ptsGrid = mutableListOf<Float>()
    private val paintFill = Paint().apply {
        isAntiAlias = true
        color = ContextCompat.getColor(context, R.color.black)
        style = Paint.Style.FILL_AND_STROKE
    }
    private val paintSplitLine = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1f
    }
    private val paintGridLine = Paint().apply {
        isAntiAlias = true
        color = Color.GRAY
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.5f
    }
    private val paintPlayed = Paint().apply {
        isAntiAlias = true
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
    private val paintNoteUnselected = Paint().apply {
        isAntiAlias = true
        color = ContextCompat.getColor(context, R.color.black)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val paintCover = Paint().apply{
        color = ContextCompat.getColor(context, R.color.black)
        alpha = 50
    }

    private var splitLine = 0f
    private var upperSplit = 0f
    private var lowerSplit = 0f
    private var lastIndexX = 0f
    private var height440 = 0f
    private var tempo = 60
    private var currentNote: Int = -150


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        splitLine = this.width / 3f
        upperSplit = this.height / 5f
        lowerSplit = this.height * 4 / 5f
        lastIndexX = (this.width).toFloat()
        height440 = this.height / 3f
        putGrid()
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
        addTrunk(this.width + SPEED / 2, ib)
        trunks.forEach {
            it.x -= SPEED
            it.alive = !((it.x < 0) or (it.x > this.width))
            it.played = it.x < splitLine
        }
    }

    private fun addTrunk(x: Float, ib: InfoBlock){
        trunks.add(Trunk(ib.id, x = x, heightCalc(ib.frequency), false,
                ib.isANote, volumeCalc(ib.volume)))
    }

    private fun noting(ib: InfoBlock){
        // if diverged from previous average for 1/12 scale, then counted as a separated note
        if (addNote(lastIndexX - NOTE_DISPOSITION, ib)) {
            notes.forEach {
                it.x -= NOTE_MOVE
                it.alive = !((it.x < 0) or (it.x > this.width))
                it.played = it.x < splitLine
            }
        }
    }

    private fun addNote(x: Float, ib: InfoBlock):Boolean{
        // this should include an algorithm
        // unstably and harmony
        val thisNote = noteCalc(ib.frequency)
        return if (thisNote !=currentNote){
            notes.add(Note(notes.size, thisNote, ib.id, ib.id, radiusCalc(0),
                    x, noteHeightCalc(thisNote), isRest = !ib.isANote,
                    alive=true, played = false, lastNote = currentNote))
            currentNote = thisNote
            ib.noteBelong = notes.lastIndex
            ib.noteBelongStart = notes.last().infoStart
            true
        } else {
            notes.last().infoEnd = ib.id
            notes.last().radius = radiusCalc(notes.last().infoEnd - notes.last().infoStart)
            ib.noteBelong = notes.lastIndex
            ib.noteBelongStart = notes.last().infoStart
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun trunkDraw(canvas: Canvas) {
        trunks.forEach {
            if (it.alive and it.pitched) {
                val theColor = colorCalculate(
                        notes[infoList[it.id].noteBelong].thisNote,
                        notes[infoList[it.id].noteBelong].lastNote)
                paintFill.color = theColor
                canvas.drawRect(it.x - SPEED / 2, it.y - STRETCH / 2,
                        it.x + SPEED / 2, it.y + STRETCH / 2,
                        paintFill)
                canvas.drawRect(it.x - SPEED / 2, this.height - it.volumeHeight,
                        it.x + SPEED / 2, this.height.toFloat(),
                        paintFillYellow)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun noteDraw(canvas: Canvas){
        notes.forEach{
            if (it.alive and !it.isRest) {
                paintNoteUnselected.color = colorCalculate(it.thisNote, it.lastNote)
                canvas.drawCircle(it.x, it.y, it.radius, paintNoteUnselected)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun trunkReplayDraw(canvas: Canvas) {
        canvas.drawLine(splitLine, upperSplit, splitLine, lowerSplit, paintSplitLine)
        trunks.forEach {
            if (it.alive and it.pitched) {
                val theColor = colorCalculate(
                        notes[infoList[it.id].noteBelong].thisNote,
                        notes[infoList[it.id].noteBelong].lastNote)
                paintFill.color = theColor
                paintPlayed.color = theColor
                paintPlayed.alpha = 50
                canvas.drawRect(it.x - SPEED / 2, it.y - STRETCH / 2,
                        it.x + SPEED / 2, it.y + STRETCH / 2,
                        if (it.played) paintPlayed else paintFill)
                canvas.drawRect(it.x - SPEED / 2, this.height - it.volumeHeight,
                        it.x + SPEED / 2, this.height.toFloat(),
                        if (it.played) paintFillYellowPlayed else paintFillYellow)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun noteReplayDraw(canvas: Canvas){
        canvas.drawLine(splitLine, upperSplit, splitLine, lowerSplit, paintSplitLine)
        notes.forEach{
            paintNoteUnselected.color = colorCalculate(it.thisNote, it.lastNote)
            if (it.alive and !it.isRest) {
                canvas.drawCircle(it.x, it.y, it.radius, paintNoteUnselected)
            }
        }
        canvas.drawRect(0f, 0f, splitLine, this.height.toFloat(), paintCover)
    }

    fun moveBubbles(distanceX : Float){
        if (isInTrunk){
            trunks.forEach{
                it.x -= distanceX
                it.alive = !((it.x < 0) or (it.x > this.width))
                it.played = it.x < splitLine
            }
        } else {
            notes.forEach{
                it.x -= distanceX
                it.alive = !((it.x < 0) or (it.x > this.width))
                it.played = it.x < splitLine
            }
        }
        lastIndexX -= distanceX
        invalidate()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun moveAdjust(x: Float, y: Float, distanceY: Float){
        if (isInTrunk)
            moveBlocks(x, y, distanceY)
        else
            moveNotes(x, y, distanceY)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun moveBlocks(x: Float, y: Float, distanceY: Float){
        // get the id, use exp
        if (trunks.isNotEmpty()) {
            val currentId: Int = ((x - trunks[0].x) / SPEED).toInt()
            if (y in trunks[currentId].y - REACT_RANGE .. trunks[currentId].y + REACT_RANGE){
                trunks[currentId].y -= distanceY // ADJUST_Y
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
    }

    private fun moveNotes(x: Float, y: Float, distanceY: Float){
        if (notes.isNotEmpty()) {
            val currentId: Int = ((x - notes[0].x) / NOTE_MOVE).toInt()
            val itt = notes[currentId]
//            val distance = sqrt((x - itt.x).pow(2) + (y-itt.y).pow(2))
//            if (distance > itt.radius)
//                return
            itt.y -= distanceY
            itt.thisNote = noteCalc(pitchCalc(itt.y))
            for (i in itt.infoStart..itt.infoEnd)
                infoList[i].frequency = pitchCalc(heightCalc(infoList[i].frequency) - distanceY)
            invalidate()
        }
    }

    private fun heightCalc(freq: Float): Float {
        return if (freq > 20)
            (ln(440 / freq) / ln(2.0) * 12 * STRETCH + height440).toFloat()
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
        return if (freq>20)
            (log2(freq / 440) * 12).roundToInt()
        else
            -100
    }

    private fun radiusCalc(length: Int) : Float {
        // this function can be renewable
        val base = 10f
        val multiplier = 1f
        val compressor = 0.2f
        return (ln(length * multiplier + 1)) / compressor + base
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun colorCalculate(last: Int, now: Int): Int {
        val theColor = ArgbEvaluator()
        // five-scale is green
        val distance = (last - now) % 12
        return if (distance <= 7)
            theColor.evaluate(distance / 7f, Color.MAGENTA, Color.GREEN) as Int
        else
            theColor.evaluate((distance - 7) / 5f, Color.GREEN, Color.BLUE) as Int
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(ResourcesCompat.getColor(resources, R.color.colorBackground, null))
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
        if (grid) drawGrid(canvas)
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
        // align at lastX
        // actually, after editing, note needs re-generation from info blocks...
        var cnt = 0
        if (infoList.isNotEmpty()) {
            notes.clear()
            currentNote = -150
            infoList.forEach {
                if (addNote(lastIndexX + NOTE_MOVE * cnt - NOTE_DISPOSITION, it)){
                   cnt += 1
                }
            }
        }
        if (notes.isNotEmpty()) {
            val xGap = cnt * NOTE_MOVE
            notes.forEach {
                it.x -= xGap
                it.alive = !((it.x < 0) or (it.x > this.width))
                it.played = it.x < splitLine
            }
            invalidate()
        }
    }

    fun changeToTrunk(){
        isInTrunk = true
        // align at lastX
        if (infoList.isNotEmpty()) {
            val firstX = lastIndexX - infoList.lastIndex * SPEED
            trunks.clear()
            infoList.forEach {
                addTrunk(firstX + it.id * SPEED, it)
            }
        }
        if (trunks.isNotEmpty()) {
            trunks.forEach {
                it.alive = it.x in 0f..this.width.toFloat()
                it.played = it.x < splitLine
            }
            invalidate()
        }
    }

    private fun putGrid(){
        // with tempo and all lines
        val lowestNote : Int = ((this.height - height440) / STRETCH).toInt() - 1
        val highestNote : Int =  - (height440 / STRETCH).toInt() + 1
        for (iNote in highestNote..lowestNote){
            ptsGrid += listOf(0f, iNote * STRETCH + height440, this.width.toFloat(), iNote * STRETCH + height440)
        }
    }

    private fun drawGrid(canvas: Canvas){
        canvas.drawLines(ptsGrid.toFloatArray(), paintGridLine)
    }
}