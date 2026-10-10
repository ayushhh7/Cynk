/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.together

import androidx.compose.runtime.Immutable
import kotlin.math.absoluteValue

@Immutable
data class TogetherClockSnapshot(
    val estimatedOffsetMs: Long = 0L,
    val estimatedRttMs: Long = 0L,
)

class TogetherClock(
    initialOffsetMs: Double = (System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()).toDouble(),
) {
    private var offsetMs: Double = initialOffsetMs
    private var rttMs: Double = 0.0
    private var hasPongSample: Boolean = false

    fun onPong(
        sentAtElapsedMs: Long,
        receivedAtElapsedMs: Long,
        serverElapsedMs: Long,
    ): TogetherClockSnapshot {
        val rtt = (receivedAtElapsedMs - sentAtElapsedMs).coerceAtLeast(0L)
        val mid = sentAtElapsedMs + rtt / 2L
        val newOffset = (serverElapsedMs - mid).toDouble()

        val rttAlpha = 0.2
        val offsetAlpha = if (!hasPongSample || (newOffset - offsetMs).absoluteValue > 1500.0) 0.6 else 0.2

        rttMs = if (!hasPongSample) rtt.toDouble() else rttMs + (rtt - rttMs) * rttAlpha
        offsetMs = if (!hasPongSample) newOffset else offsetMs + (newOffset - offsetMs) * offsetAlpha
        hasPongSample = true

        return snapshot()
    }

    fun snapshot(): TogetherClockSnapshot =
        TogetherClockSnapshot(
            estimatedOffsetMs = offsetMs.toLong(),
            estimatedRttMs = rttMs.toLong(),
        )

    fun serverTimeNow(): Long = android.os.SystemClock.elapsedRealtime() + offsetMs.toLong()
}

