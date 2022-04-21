/*
This part of code is cited from PitchResynthesizer, GainProcessor and AudioEvent of the great TarsosDSP library.
*/
package com.example.legato
import be.tarsos.dsp.EnvelopeFollower
import java.util.*
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

class PR @JvmOverloads constructor(private val samplerate: Float, private val followEnvelope: Boolean = true, pureSine: Boolean = false, filterSize: Int = 5){
    private var phase = 0.0
    private var phaseFirst = 0.0
    private var phaseSecond = 0.0
    private var prevFrequency = 0.0
    private val envelopeFollower = EnvelopeFollower(samplerate.toDouble(), 0.005, 0.01)
    private val usePureSine = pureSine
    private val previousFrequencies = DoubleArray(filterSize)
    private var previousFrequencyIndex  = 0

    fun handlePitch(frequency: Double, originalAudioBuffer: FloatArray, volume: Double): FloatArray {
        var frequency = frequency * 2
        //var frequency = pitchDetectionResult.pitch.toDouble()
        if (frequency == -1.0) {
            frequency = prevFrequency
            // return ??
        } else {
            if (previousFrequencies.isNotEmpty()) {
                //median filter
                //store and adjust pointer
                previousFrequencies[previousFrequencyIndex] = frequency
                previousFrequencyIndex += 1
                previousFrequencyIndex %= previousFrequencies.size
                //sort to get median frequency
                val frequenciesCopy = previousFrequencies.clone()
                Arrays.sort(frequenciesCopy)
                //use the median as frequency
                frequency = frequenciesCopy[frequenciesCopy.size / 2]
            }
            prevFrequency = frequency
        }
        val twoPiF = 2 * Math.PI * frequency
        //val audioBuffer = audioEvent.floatBuffer
        val audioBuffer = originalAudioBuffer.clone()
        var envelope: FloatArray? = null
        if (followEnvelope) {
            envelope = audioBuffer.clone()
            envelopeFollower.calculateEnvelope(envelope)
        }
        for (sample in audioBuffer.indices) {
            val time = (sample / samplerate).toDouble()
            var wave = sin(twoPiF * time + phase)
            if (!usePureSine) {
                wave += 0.05 * sin(twoPiF * 4 * time + phaseFirst)
                wave += 0.01 * sin(twoPiF * 8 * time + phaseSecond)
            }
            audioBuffer[sample] = wave.toFloat()
            if (followEnvelope) {
                audioBuffer[sample] = audioBuffer[sample] * envelope!![sample]
            }
        }
        val timefactor : Double = twoPiF * audioBuffer.size / samplerate
        phase += timefactor
        if (!usePureSine) {
            phaseFirst += 4.0 * timefactor
            phaseSecond += 8.0 * timefactor
        }

        // volume aligning

        val newDB = soundPressureLevel(audioBuffer)
        val gain = 10.0.pow((volume - newDB) / 20.0)
        gainChange(audioBuffer, gain.toFloat())

        return audioBuffer
    }

    private fun gainChange(audioFloatBuffer: FloatArray, gain: Float){
        for (i in audioFloatBuffer.indices) {
            var newValue = (audioFloatBuffer[i] * gain)
            if (newValue > 1.0f) {
                newValue = 1.0f
            } else if (newValue < -1.0f) {
                newValue = -1.0f
            }
            audioFloatBuffer[i] = newValue
        }
    }

    private fun soundPressureLevel(buffer: FloatArray): Double {
        var value = localEnergy(buffer).pow(0.5)
        value /= buffer.size
        return linearToDecibel(value)
    }

    private fun localEnergy(buffer: FloatArray): Double {
        var power = 0.0
        for (element in buffer) {
            power += (element * element).toDouble()
        }
        return power
    }

    private fun linearToDecibel(value: Double): Double {
        return 20.0 * log10(value)
    }

    // TODO: alignment of volume should be done
    // 论文里面还应该写，这个地方重合成之后，音量是没有对齐的
}
