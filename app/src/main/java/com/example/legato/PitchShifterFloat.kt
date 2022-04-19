package com.example.legato

import android.util.Log
import be.tarsos.dsp.util.fft.FFT
import java.lang.System.arraycopy
import java.lang.System.out
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class PitchShifterFloat(private var pitchShiftRatio: Double, private val sampleRate: Double,
                        private val size: Int, private val overlap: Int) {

    private val fft = FFT(size)
    private val currentMagnitudes = FloatArray(size / 2)
    private val currentFrequencies = FloatArray(size / 2)
    private val currentPhase = FloatArray(size / 2)
    private val previousPhase = FloatArray(size / 2)
    private val summedPhase = FloatArray(size / 2)
    private val outputAccumulator = FloatArray(size * 2)
    private val osamp = (size / (size - overlap)).toLong()
    private val excpt = 2 * PI * (size - overlap).toDouble() / size.toDouble()

    fun setPitchShiftFactor(newPSF: Float) {
        pitchShiftRatio = newPSF.toDouble()
    }

    fun process(floatInfo: FloatArray): FloatArray {
        if (pitchShiftRatio == 1.0) return floatInfo
        Log.d("factor", pitchShiftRatio.toString())
        //val var2 = byteInfo.byteToFloatArray().clone()
        //val var2 = var1.floatBuffer.clone()
        val fftData = floatInfo.clone()

        for (i in 0 until size) {
            val window = (-0.5 * cos(2 * PI * i.toDouble() / size.toDouble()) + 0.5).toFloat()
            fftData[i] = window * fftData[i]
        }
        // Fourier transform the audio
        fft.forwardTransform(fftData)
        // Calculate the magnitudes and phase information
        fft.powerAndPhaseFromFFT(fftData, currentMagnitudes, currentPhase)
        val freqPerBin = (sampleRate / size.toFloat()).toFloat()

        for (i in 0 until size / 2) {
            val phase = currentPhase[i]

            //phase difference calculation
            var tmp = (phase - previousPhase[i]).toDouble()
            previousPhase[i] = phase

            //expected phase difference subtraction
            tmp -= (i * excpt)

            //map delta phase into +/- PI interval
            var qpd = (tmp / PI).toLong()
            if (qpd >= 0) {
                qpd += qpd and 1L
            } else {
                qpd -= qpd and 1L
            }
            tmp -= PI * qpd.toDouble()

            // get deviation from bin frequency from the +/- Pi int.
            tmp = osamp * tmp / (2 * PI)

            // k-th partials true frequency
            tmp = i.toDouble() * freqPerBin + tmp * freqPerBin
            currentFrequencies[i] = tmp.toFloat()
        }

        val newMagnitudes = FloatArray(size / 2)
        val newFrequencies = FloatArray(size / 2)

        var index: Int
        for (i in 0 until size / 2) {
            index = (i * pitchShiftRatio).toInt()
            if (index < size / 2) {
                newMagnitudes[index] += currentMagnitudes[i]
                newFrequencies[index] = (currentFrequencies[i] * pitchShiftRatio).toFloat()
            }
        }

        // synthesis
        val newFFTData = FloatArray(size)

        for (i in 0 until size / 2) {

            val magn: Float = newMagnitudes[i]
            var tmp: Double = newFrequencies[i].toDouble()

            tmp -= (i * freqPerBin).toDouble()
            tmp /= freqPerBin
            tmp = (2 * PI) * tmp / osamp
            tmp += i.toDouble() * excpt

            summedPhase[i] += tmp.toFloat()
            val phase = summedPhase[i]

            newFFTData[2 * i] = (magn * cos(phase))
            newFFTData[2 * i + 1] = (magn * sin(phase))
        }

        for (i in size / 2 + 2 until size) {
            newFFTData[i] = 0.0f
        }
        fft.backwardsTransform(newFFTData)



        for (i in newFFTData.indices) {
            val window = (-0.5 * cos((2 * PI) * i.toDouble() / size.toDouble()) + 0.5).toFloat()
            outputAccumulator[i] += window * newFFTData[i] / osamp.toFloat()

            if (outputAccumulator[i] > 1.0 || outputAccumulator[i] < -1.0) {
                Log.e("Warning", "Clipping!")
            }
        }

        // if no overlap, osamp == 1.

        val stepSize = (size / osamp).toInt()
        // if no overlap, this is not working
        return outputAccumulator.toList().slice(0 until size).toFloatArray()
//        for (i in outputAccumulator.indices)
//            Log.d("output", outputAccumulator[i].toString())
//        arraycopy(outputAccumulator, stepSize, outputAccumulator, 0, size)
//        for (i in outputAccumulator.indices)
//            Log.d("JJJJJ", outputAccumulator[i].toString())
//        val newAudioBuffer = FloatArray(size)
//        System.arraycopy(outputAccumulator, 0, newAudioBuffer,size-stepSize, stepSize)


    }

}