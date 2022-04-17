package com.example.legato

import android.os.Build
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import java.lang.Math.abs
import kotlin.properties.Delegates

// ---------------------Editor Part------------------------------

const val DEBUG_TAG = "TEST"

class Editor(initialValid: Boolean, private val viewToEdit: PVNView): GestureDetector.SimpleOnGestureListener() {

    var editorValid: Boolean = initialValid
    var canReallyReplay: Boolean = false
    private var initialMoveCount = 0
    private var movingSwipe by Delegates.notNull<Boolean>()

    override fun onDown(event: MotionEvent): Boolean {
        return if (editorValid) {
            Log.d(DEBUG_TAG, "onDown: $event")
            true
        } else false
    }

//    override fun onFling(
//            event1: MotionEvent,
//            event2: MotionEvent,
//            velocityX: Float,
//            velocityY: Float
//    ): Boolean {
//        Log.d(DEBUG_TAG, "onFling: $event1 $event2")
//        return true
//    }
//
//    override fun onLongPress(event: MotionEvent) {
//        Log.d(DEBUG_TAG, "onLongPress: $event")
//    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onScroll(
            event1: MotionEvent,
            event2: MotionEvent,
            distanceX: Float,
            distanceY: Float
    ): Boolean {
        return if (editorValid) {
            initialMoveCount += 1
            Log.d(DEBUG_TAG, "onScroll: $event1 $event2")
            if (initialMoveCount == 1) {
                movingSwipe = kotlin.math.abs(distanceX) > kotlin.math.abs(distanceY)
                if (canReallyReplay){
                    canReallyReplay = false
                    viewToEdit.stopReplay()
                }
            }
            if (movingSwipe) {
                viewToEdit.moveBubbles(distanceX)
            } else {
                viewToEdit.moveAdjust(event1.x, event1.y, distanceY)
            }
            true
            // y轴变化需要定位到哪几个id需要动，这个比x轴的变化要难搞一些，还需要记录id和x的对应关系
        } else false
    }

    override fun onShowPress(event: MotionEvent) {
        // use this to determine initial move direction
        if (editorValid) {
            initialMoveCount = 0
            Log.d(DEBUG_TAG, "onShowPress: $event")
        }
    }

//    override fun onSingleTapUp(event: MotionEvent): Boolean {
//        Log.d(DEBUG_TAG, "onSingleTapUp: $event")
//        return true
//    }
//
//    override fun onDoubleTap(event: MotionEvent): Boolean {
//        Log.d(DEBUG_TAG, "onDoubleTap: $event")
//        return true
//    }
//
//    override fun onDoubleTapEvent(event: MotionEvent): Boolean {
//        Log.d(DEBUG_TAG, "onDoubleTapEvent: $event")
//        return true
//    }

    override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
        Log.d(DEBUG_TAG, "onSingleTapConfirmed: $event")
        return if (editorValid) {
            canReallyReplay = !canReallyReplay
            if(canReallyReplay){
                viewToEdit.startReplay()
                true
            } else {
                viewToEdit.stopReplay()
                true
            }
        } else false
    }
}