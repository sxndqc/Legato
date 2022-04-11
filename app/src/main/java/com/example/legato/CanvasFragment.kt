package com.example.legato

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment

class CanvasFragment : Fragment() {

    //private lateinit var mySurfaceView: MySurfaceView
    private lateinit var myCanvasView: MyCanvasView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //mySurfaceView = MySurfaceView(this.requireContext())
        myCanvasView = MyCanvasView(this.requireContext())
        myCanvasView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        myCanvasView.contentDescription = getString(R.string.canvasContentDescription)
        return myCanvasView
        // return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun drawPic(f: Double, level: Double, t: Int){
        myCanvasView.drawLine(f, level, t)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
}