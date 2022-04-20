package com.example.legato

import be.tarsos.dsp.resample.RateTransposer
import be.tarsos.dsp.resample.Resampler

class RateTransposerDirect(factor: Double) : RateTransposer(factor) {
    private val factor = factor
    private val r= Resampler(false, 0.1, 4.0);
    fun processWithFloat(src: FloatArray): FloatArray {
        val out = FloatArray((src.size * factor).toInt())
        r.process(factor, src, 0, src.size, false, out, 0, out.size)
        return out
    }
}

fun pitchShifting(src: FloatArray, factor: Double): FloatArray {
    val rateTransposer = RateTransposerDirect(factor)
    val wsola = WSOLA(WSOLA.Parameters.musicDefaults(factor, SAMPLING_RATE.toDouble()))
    return rateTransposer.processWithFloat(wsola.processWithFloat(src))
}