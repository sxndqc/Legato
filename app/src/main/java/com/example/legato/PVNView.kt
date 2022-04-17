package com.example.legato

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Color.*
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock.sleep
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.PitchShifter
import be.tarsos.dsp.pitch.PitchDetectionResult
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

abstract class Transcriber @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int = 0
)
    : View(context, attrs, defStyleAttr){

    protected class InfoBlock(
        val id: Int,
        var bufferPos: Int,
        var bufferContent: AudioEvent,
        var frequency: Float,
        var volume: Double,
        val isANote: Boolean,
        var noteBelong: Int = NO_VALUE_INT,
        var noteBelongStart: Int = NO_VALUE_INT,
        var originalFrequency: Float
    )
    protected var infoList: MutableList<InfoBlock> = mutableListOf()
    var isForReplay: Boolean = false
    var isOnReplay: Boolean = false
    var grid : Boolean = false
    protected val streaming : MutableList<Byte> = mutableListOf()
    private val minBufferSize = AudioTrack.getMinBufferSize(
        SAMPLING_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    @RequiresApi(Build.VERSION_CODES.M)
    protected val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLING_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(minBufferSize)
        .build()
}

class PVNView  @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int = 0
)
    : Transcriber(context, attrs, defStyleAttr){

    class Trunk(
        val id: Int, var x: Float, var y: Float,
        var alive: Boolean, var pitched: Boolean,
        var volumeHeight: Float, var played: Boolean = false
    )
    class Note(
        val id: Int, var thisNote: Int, var infoStart: Int, var infoEnd: Int, var radius: Float,
        var x: Float = NO_VALUE, var y: Float = NO_VALUE, var isRest: Boolean,
        var alive: Boolean, var played: Boolean = false, var lastNote: Int = -150
    )
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
    lateinit var dispatcher: AudioDispatcher
    lateinit var format : AudioFormat

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
        infoList.add(
            InfoBlock(
                t, streaming.size, e, result.pitch, e.getdBSPL(), result.isPitched,
                originalFrequency = result.pitch
            )
        )
        streaming += e.byteBuffer.toList()
        trunking(infoList[t])
        noting(infoList[t])
        invalidate()
    }

    private fun trunking(ib: InfoBlock) {
        // each move forward a speed length
        // Log.d("TEST", "nums:  $splitLine , $upperSplit , $lowerSplit , $lastIndexX")
        addTrunk(lastIndexX + SPEED / 2, ib)
        trunks.forEach {
            it.x -= SPEED
            it.alive = !((it.x < 0) or (it.x > this.width))
            it.played = it.x < splitLine
        }
    }

    private fun addTrunk(x: Float, ib: InfoBlock){
        trunks.add(
            Trunk(
                ib.id, x = x, heightCalc(ib.frequency), false,
                ib.isANote, volumeCalc(ib.volume)
            )
        )
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
            notes.add(
                Note(
                    notes.size, thisNote, ib.id, ib.id, radiusCalc(0),
                    x, noteHeightCalc(thisNote), isRest = !ib.isANote,
                    alive = true, played = false, lastNote = currentNote
                )
            )
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
                    notes[infoList[it.id].noteBelong].lastNote
                )
                paintFill.color = theColor
                canvas.drawRect(
                    it.x - SPEED / 2, it.y - STRETCH / 2,
                    it.x + SPEED / 2, it.y + STRETCH / 2,
                    paintFill
                )
                canvas.drawRect(
                    it.x - SPEED / 2, this.height - it.volumeHeight,
                    it.x + SPEED / 2, this.height.toFloat(),
                    paintFillYellow
                )
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
                    notes[infoList[it.id].noteBelong].lastNote
                )
                paintFill.color = theColor
                paintPlayed.color = theColor
                paintPlayed.alpha = 50
                canvas.drawRect(
                    it.x - SPEED / 2, it.y - STRETCH / 2,
                    it.x + SPEED / 2, it.y + STRETCH / 2,
                    if (it.played) paintPlayed else paintFill
                )
                canvas.drawRect(
                    it.x - SPEED / 2, this.height - it.volumeHeight,
                    it.x + SPEED / 2, this.height.toFloat(),
                    if (it.played) paintFillYellowPlayed else paintFillYellow
                )
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

    fun moveBubbles(distanceX: Float){
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
            if (currentId !in 0..trunks.lastIndex) return
            if (y in trunks[currentId].y - REACT_RANGE .. trunks[currentId].y + REACT_RANGE){
                trunks[currentId].y -= distanceY
                infoList[currentId].frequency = pitchCalc(trunks[currentId].y)
                for(i in 1..NEIGHBOR) {
                    if (currentId - i >= 0) {
                        trunks[currentId - i].y -= distanceY / exp(i.toDouble()).toFloat()
                        infoList[currentId - i].frequency = pitchCalc(trunks[currentId - i].y)
                    }
                    if (currentId + i <= trunks.lastIndex) {
                        trunks[currentId + i].y -= distanceY  / exp(i.toDouble()).toFloat()
                        infoList[currentId + i].frequency = pitchCalc(trunks[currentId + i].y)
                    }
                }
            }

            if (y in (this.height - trunks[currentId].volumeHeight - REACT_RANGE) .. (this.height - trunks[currentId].volumeHeight + REACT_RANGE)){
                trunks[currentId].volumeHeight += distanceY / ADJUST_Y
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
            if (currentId !in 0..notes.lastIndex) return
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
        val distance = (abs(last - now)) % 12
        return if (distance <= 7)
            theColor.evaluate(distance / 7f, MAGENTA, GREEN) as Int
            //theColor.evaluate(distance / 7f, argb(0xFF,0xFF,0x4E,0x02),
            //        argb(0xFF,0x1B,0xD1,0xA5)) as Int
        else
            theColor.evaluate((distance - 7) / 5f, GREEN, BLUE) as Int
            //theColor.evaluate((distance - 7) / 5f, argb(0xFF, 0x1B,0xD1,0xA5)
             //       , argb(0xFF,0x4B,0x5C,0xC4)) as Int
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

        // audioTrack.play() // Play the track, because it is streaming, so need play first
        // audioTrack.pause()
        invalidate()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun startReplay(){
        // play block at splitLine
        if (isForReplay) {
            isOnReplay = true
            val currentId = if (isInTrunk) {
                ((splitLine - trunks[0].x) / SPEED).toInt()
            } else {
                notes[((splitLine - notes[0].x) / NOTE_MOVE).toInt()].infoStart
            }

//            val audioTrack = AudioTrack(
//                AudioManager.STREAM_MUSIC, SAMPLING_RATE, AudioFormat.CHANNEL_OUT_MONO,
//                AudioFormat.ENCODING_PCM_16BIT, BLOCK_SIZE * 2, AudioTrack.MODE_STATIC
//            )
//            val toStream = streaming.slice(infoList[currentId].bufferPos until streaming.size).toByteArray()
 //           val pitchShifter = PitchShifter(1.0, SAMPLING_RATE.toDouble(), BLOCK_SIZE, 0)

//            audioTrack.write(
//                toStream,
//              0, toStream.size
//            )

            // TODO:整个事情就很扑朔迷离：设成STATIC的话，只能放一小节，而且换个位置就放不出来了；设成STREAM的话，如果PLAY在远处，
        //  那么什么都放不出来；如果用toStream一次写入大量buffer，play在write后面，那只能放一小节；如果play在write前面一点点，那就可以放
            // 整个后半段；如果play放在setReplay那里，那就只能放一次，能够都放出来，但是噼里啪啦；如果用runnable写入的话，那就会放出极其空灵的声音。
            // 我猜测跟stream的流机制有关？
            // TODO: 如果把AudioTrack的buffer调到4倍， 那么噼里啪啦的声音就没了，效果好得一逼。
            // TODO: 这个时候把play刚刚放在write前面的话，那随便拖动随便放。如果play放在setReplay里面的话，就只能播放一次。
            // TODO: 看起来像是，play只会等候一次，如果一次播完了，后面没有接上，它就自动销毁了，不会再播放后面新写入的内容了；
            // TODO： 所以如果play放在write后面，意思就是只写入了buffer那么多的内容。放在write前面，就会认为是一个流输入。
            // TODO： 而如果play放在了setReplay里面，那就是我点击第一次，等候流进入，然后播放了一次，停了，我再播的话，喔，是因为pause了没恢复。
            // TODO: 所以play只能放在startReplay里面，不然没法点击开始和暂停，除非暂停只是切断了流入。
            // TODO: 而play如果buffer没被填满的话，等待它的结果就是没满也开始播放，那就变成空灵音了。
            // TODO: 而如果play被放在了write后面的话，那就只能写入buffer那么多的内容，所以就只能播一点。
            // TODO: 所以我play放太远了，就没声音，是因为等待流的时候过长，自动结束了。放空灵声音，是因为写入速度太慢。爆破音是buffer问题。
            // 那么事情明了了：全力写入，然后再来动图。但还是不能分批写入，，，这样写入了一次就会开始播放，还是音效奇葩。
            // 要看怎么做成流。要不就只能合成之后再做了。

            val audioSender = Runnable {
                Log.d("Here!", "Here")
                for (i in currentId..infoList.lastIndex) {
                    if (isOnReplay) {
                        val ib = infoList[i]
                        val factor = ib.frequency / ib.originalFrequency
                        //pitchShifter.setPitchShiftFactor(1f)
                        //pitchShifter.process(ib.bufferContent)

                        audioTrack.write(
                            ib.bufferContent.byteBuffer,
                            0, ib.bufferContent.byteBuffer.size //ib.bufferContent.bufferSize
                        )
                        // 那个复杂音效应该因为play必须要buffer填满了才播放，否则播放的时候采样率可能有问题
                    }
                }
            }
            val imageMover = Runnable {
                for (i in currentId..infoList.lastIndex) if (isOnReplay){
                    if (isInTrunk) {
                        moveBubbles(SPEED)
                    } else {
                        moveBubbles(NOTE_MOVE.toFloat())
                    }
                    invalidate()
                    sleep((BLOCK_SIZE * 1000 / SAMPLING_RATE).toLong())
                }
            }
            audioTrack.play()
            Thread(audioSender).start()
            Thread(imageMover).start()
        }
   }

    fun stopReplay() {
        if (isForReplay) {
            if (isOnReplay){
                audioTrack.pause()
                audioTrack.flush()
            }
            isOnReplay = false
        }
    }

    fun quitReplay(){
        if (isForReplay) {
            audioTrack.stop()
        }
        isForReplay = false
         // Play the track, because it is streaming, so need play first
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
            ptsGrid += listOf(
                0f,
                iNote * STRETCH + height440,
                this.width.toFloat(),
                iNote * STRETCH + height440
            )
        }
    }

    private fun drawGrid(canvas: Canvas){
        canvas.drawLines(ptsGrid.toFloatArray(), paintGridLine)
    }
}