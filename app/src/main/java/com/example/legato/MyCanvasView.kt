package com.example.legato

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import java.util.*
import java.util.stream.IntStream
import kotlin.math.abs
import kotlin.math.ln

// Stroke width for the the paint.
private const val STROKE_WIDTH = 12f

/**
 * Custom view that follows touch events to draw on a canvas.
 */
class MyCanvasView(context: Context) : View(context) {

    // Holds the path you are currently drawing.
    private var path = Path()

    private val drawColor = ResourcesCompat.getColor(resources, R.color.colorPaint, null)
    private val backgroundColor = ResourcesCompat.getColor(resources, R.color.colorBackground, null)
    private lateinit var extraCanvas: Canvas
    private lateinit var extraBitmap: Bitmap
    // private lateinit var frame: Rect
    private val fInfo = ArrayList<Array<Double>>()
    private val speed = 2.0

    // Set up the paint with which to draw.
    private val secretPaint = Paint().apply {
        color = backgroundColor //This needs counting
        // Smooths out edges of what is drawn without affecting shape.
        isAntiAlias = true
        // Dithering affects how colors with higher-precision than the device are down-sampled.
        isDither = true
        style = Paint.Style.STROKE // default: FILL
        strokeJoin = Paint.Join.ROUND // default: MITER
        strokeCap = Paint.Cap.ROUND // default: BUTT
        strokeWidth = STROKE_WIDTH // default: Hairline-width (really thin)
    }

    private val paint = Paint().apply {
        color = drawColor //This needs counting
        // Smooths out edges of what is drawn without affecting shape.
        isAntiAlias = true
        // Dithering affects how colors with higher-precision than the device are down-sampled.
        isDither = true
        style = Paint.Style.STROKE // default: FILL
        strokeJoin = Paint.Join.ROUND // default: MITER
        strokeCap = Paint.Cap.ROUND // default: BUTT
        strokeWidth = STROKE_WIDTH // default: Hairline-width (really thin)
    }

    /**
     * Don't draw every single pixel.
     * If the finger has has moved less than this distance, don't draw. scaledTouchSlop, returns
     * the distance in pixels a touch can wander before we think the user is scrolling.
     */
    private val touchTolerance = ViewConfiguration.get(context).scaledTouchSlop

    private var currentX = 0f
    private var currentY = 0f

    private var motionTouchEventX = 0f
    private var motionTouchEventY = 0f

    /**
     * Called whenever the view changes size.
     * Since the view starts out with no size, this is also called after
     * the view has been inflated and has a valid size.
     */
    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        // setting the layout
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        if (::extraBitmap.isInitialized) extraBitmap.recycle()
        extraBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        extraCanvas = Canvas(extraBitmap)
        extraCanvas.drawColor(backgroundColor)

        // Calculate a rectangular frame around the picture.
        // val inset = (width * 0.618).roundToInt()
        // frame = Rect(0, 0, inset, height)
    }

    override fun onDraw(canvas: Canvas) {
        // Draw the bitmap that has the saved path.
        canvas.drawBitmap(extraBitmap, 0f, 0f, null)
        // Draw a frame around the canvas.
        // extraCanvas.drawRect(frame, secretPaint)
    }

    /**
     * No need to call and implement MyCanvasView#performClick, because MyCanvasView custom view
     * does not handle click actions.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        motionTouchEventX = event.x
        motionTouchEventY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> touchStart()
            MotionEvent.ACTION_MOVE -> touchMove()
            MotionEvent.ACTION_UP -> touchUp()
        }
        return true
    }

    /**
     * The following methods factor out what happens for different touch events,
     * as determined by the onTouchEvent() when statement.
     * This keeps the when conditional block
     * concise and makes it easier to change what happens for each event.
     * No need to call invalidate because we are not drawing anything.
     */
    private fun touchStart() {
        path.reset()
        path.moveTo(motionTouchEventX, motionTouchEventY)
        currentX = motionTouchEventX
        currentY = motionTouchEventY
    }

    private fun touchMove() {
        val dx = abs(motionTouchEventX - currentX)
        val dy = abs(motionTouchEventY - currentY)
        if (dx >= touchTolerance || dy >= touchTolerance) {
            // QuadTo() adds a quadratic bezier from the last point,
            // approaching control point (x1,y1), and ending at (x2,y2).
            path.quadTo(currentX, currentY, (motionTouchEventX + currentX) / 2, (motionTouchEventY + currentY) / 2)
            Log.e("move", currentX.toString() +" "+ currentY.toString()+" "+ ((motionTouchEventX + currentX) / 2).toString()+" "+ ((motionTouchEventY + currentY) / 2).toString())
            currentX = motionTouchEventX
            currentY = motionTouchEventY
            // Draw the path in the extra bitmap to save it.
            extraCanvas.drawPath(path, paint)
        }
        // Invalidate() is inside the touchMove() under ACTION_MOVE because there are many other
        // types of motion events passed into this listener, and we don't want to invalidate the
        // view for those.
        invalidate()

        // invalidate() means to invalidate the current custom view and manually refresh the UI.
    }

    private fun touchUp() {
        // Reset the path so it doesn't get drawn again.
        path.reset()
    }

    private fun heightCanvas(freq: Double): Float {
        return if (freq > 20)
            (ln(440 / freq) / ln(2.0) * 100 + this.height / 2).toFloat()
        else
            0f
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun drawLine(f: Double, level: Double, t: Int){

        fInfo.add(arrayOf(f, level))

        //Smoothing the note
        if ((t > 2) && (StrictMath.abs(
                ln(fInfo[t][0]) -
                        ln(fInfo[t - 2][0])
            ) < ln(2.0 / 12))
        ) {
            fInfo[t - 1][0] = (fInfo[t][0] + fInfo[t - 2][0]) / 2
        }

        // extraCanvas.drawColor(backgroundColor)

        val minus: Int =
            StrictMath.floor(StrictMath.max(0.0, t - ((this.width - 30) / speed))).toInt() //Temporarily Speed
        var cn = minus
        path.reset()

        path.moveTo(((t - minus) * speed).toFloat(), heightCanvas(fInfo[t][0]))
        path.quadTo(((t - minus) * speed).toFloat(), heightCanvas(fInfo[t][0]), ((t - minus + 1) * speed).toFloat(), heightCanvas(fInfo[t][0]))


    /*
        for (i in IntStream.range(minus, t + 1)) {
            val noteSub: Double = StrictMath.abs(ln(fInfo[i][0] / fInfo[cn][0])) / ln(2.0) * 12
            // paint.color = Color.rgb(colorCalculate(noteSub)[0], colorCalculate(noteSub)[1], colorCalculate(noteSub)[2])
            //stroke(234, 110, 180);
            val strokeWeight = sw_calculate(fInfo[i][1])
            // paint.strokeWidth = strokeWeight
            if ((StrictMath.abs(ln(fInfo[i][0] / fInfo[cn][0])) / ln(2.0) > 1) && (fInfo[i][0] > 1e-3)) {
                cn = i;
                //if (stroke_weight > 10)
                //    usd_regularTriangle((i - minus) * speed(i - cn), heightCanvas(fInfo[i][0]), 2);
            }

            // TODO: 必须用叠加的方式去做。
            Log.e("test", ((i - minus) * speed).toString() +" "+ heightCanvas(fInfo[i][0]).toString() + " " + ((i - minus + 1) * speed).toString() +" "+ heightCanvas(fInfo[i][0]).toString())
            path.moveTo(((i - minus) * speed).toFloat(), heightCanvas(fInfo[i][0]))
            path.quadTo(((i - minus) * speed).toFloat(), heightCanvas(fInfo[i][0]), ((i - minus + 1) * speed).toFloat(), heightCanvas(fInfo[i][0]))

        }
     */

        extraCanvas.drawPath(path, paint)
        invalidate()
        //val toast = Toast.makeText(this.context, "Drawn", Toast.LENGTH_LONG)
        //toast.show()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun colorCalculate(sub: Double): FloatArray {
        var c = FloatArray(3)
        val r: Array<Int> = arrayOf(50,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0)
        val g: Array<Int> = arrayOf(0,   0, 50,100,150,200,240,180,140,100, 60, 20)
        val b: Array<Int> = arrayOf(200,240, 0, 0,   0,  0,  0,  0,  0, 50,100,150)
        for(i in IntStream.range(0, 12)) {
            c[0] += ((1 / StrictMath.exp(StrictMath.abs(i - sub))) * r[i]).toFloat()
            c[1] += ((1 / StrictMath.exp(StrictMath.abs(i - sub))) * g[i]).toFloat()
            c[2] += ((1 / StrictMath.exp(StrictMath.abs(i - sub))) * b[i]).toFloat()
        }
        var s : Float = c[0] + c[1] + c[2];
        c[0] = c[0] / s * 255;
        c[1] = c[1] / s * 255;
        c[2] = c[2] / s * 255;
        return c;
    }

    private fun sw_calculate(db: Double) : Float {
        var loudness = StrictMath.exp(db + 50)
        if (loudness * 3000 < 10)
            return (loudness * 3000).toFloat()
        else {
            return 10.1f
        }
    }
}