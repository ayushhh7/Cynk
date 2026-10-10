package com.nikhil.yt.together

import androidx.media3.common.PlaybackException
import com.nikhil.yt.playback.MusicService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test suite verifying the forensic audit defect fixes for the Cynk Together pipeline:
 * 1. Conservative drift correction policy (eliminating repeated hard seeks during normal playback).
 * 2. Echo suppression (preventing remote state application events from looping as guest control requests).
 * 3. Stream timeout (code 2000) and bounded retry recovery.
 * 4. Participant resolution ordering (preventing guest synthetic snapshot race and ensuring both host & guest presence).
 */
class TogetherPipelineRegressionTest {

    // --- Requirement 2: Conservative Drift Policy Tests ---

    @Test
    fun driftPolicy_playing_normalSubsecondDrift_doesNotHardSeek() {
        // Under conservative threshold (<= 2500ms), steady playback should never hard-seek
        val normalDrifts = listOf(100L, 350L, 750L, 1200L, 2000L, 2500L)
        for (drift in normalDrifts) {
            val shouldSeek = MusicService.shouldApplyHardSeek(
                isPlaying = true,
                driftMs = drift,
                elapsedSinceLastSeekMs = 5000L,
            )
            assertFalse("Playing drift of $drift ms should NOT trigger a hard seek", shouldSeek)
        }
    }

    @Test
    fun driftPolicy_playing_largeDrift_triggersHardSeek_onlyAfterThrottleElapsed() {
        // Drift > 2500ms with elapsed time >= 3000ms should trigger a hard seek
        assertTrue(
            MusicService.shouldApplyHardSeek(
                isPlaying = true,
                driftMs = 2501L,
                elapsedSinceLastSeekMs = 3000L,
            )
        )
        assertTrue(
            MusicService.shouldApplyHardSeek(
                isPlaying = true,
                driftMs = 5000L,
                elapsedSinceLastSeekMs = 4500L,
            )
        )
    }

    @Test
    fun driftPolicy_playing_largeDrift_throttled_whenSeekOccurredRecently() {
        // Drift > 2500ms but elapsed time < 3000ms must be throttled to prevent seek loops
        assertFalse(
            "Large drift should be throttled if a seek was applied recently",
            MusicService.shouldApplyHardSeek(
                isPlaying = true,
                driftMs = 4000L,
                elapsedSinceLastSeekMs = 1500L,
            )
        )
        assertFalse(
            MusicService.shouldApplyHardSeek(
                isPlaying = true,
                driftMs = 6000L,
                elapsedSinceLastSeekMs = 2999L,
            )
        )
    }

    @Test
    fun driftPolicy_paused_smallDrift_doesNotSeek() {
        // Paused with drift <= 1000ms does not seek
        assertFalse(
            MusicService.shouldApplyHardSeek(
                isPlaying = false,
                driftMs = 500L,
                elapsedSinceLastSeekMs = 0L,
            )
        )
        assertFalse(
            MusicService.shouldApplyHardSeek(
                isPlaying = false,
                driftMs = 1000L,
                elapsedSinceLastSeekMs = 0L,
            )
        )
    }

    @Test
    fun driftPolicy_paused_largeDrift_seeksImmediately() {
        // Paused with drift > 1000ms seeks immediately to align with room position
        assertTrue(
            MusicService.shouldApplyHardSeek(
                isPlaying = false,
                driftMs = 1001L,
                elapsedSinceLastSeekMs = 0L,
            )
        )
        assertTrue(
            MusicService.shouldApplyHardSeek(
                isPlaying = false,
                driftMs = 3500L,
                elapsedSinceLastSeekMs = 0L,
            )
        )
    }

    // --- Requirement 5: Echo Suppression Tests ---

    @Test
    fun echoSuppression_whenApplyingRemoteState_alwaysSuppressed() {
        // When togetherApplyingRemote is true, all player events are echo
        val isEcho = MusicService.isControlEcho(
            isApplyingRemote = true,
            nowElapsedMs = 1000L,
            suppressEchoUntilElapsedMs = 4000L,
            lastAppliedIndex = 0,
            actionIndex = 1,
        )
        assertTrue("Events during isApplyingRemote=true must be suppressed as echoes", isEcho)
    }

    @Test
    fun echoSuppression_withinWindow_matchingIndex_isEcho() {
        val now = 2000L
        val suppressUntil = 5000L
        val isEcho = MusicService.isControlEcho(
            isApplyingRemote = false,
            nowElapsedMs = now,
            suppressEchoUntilElapsedMs = suppressUntil,
            lastAppliedIndex = 3,
            actionIndex = 3,
        )
        assertTrue("Matching index transition within suppression window is an echo", isEcho)
    }

    @Test
    fun echoSuppression_withinWindow_userDifferentIndex_isNotEcho() {
        val now = 2000L
        val suppressUntil = 5000L
        val isEcho = MusicService.isControlEcho(
            isApplyingRemote = false,
            nowElapsedMs = now,
            suppressEchoUntilElapsedMs = suppressUntil,
            lastAppliedIndex = 3,
            actionIndex = 4, // User manually selected a different track
        )
        assertFalse("Different index transition is a deliberate user action and not an echo", isEcho)
    }

    @Test
    fun echoSuppression_withinWindow_matchingPlayWhenReady_isEcho() {
        val now = 2000L
        val suppressUntil = 5000L
        val isEcho = MusicService.isControlEcho(
            isApplyingRemote = false,
            nowElapsedMs = now,
            suppressEchoUntilElapsedMs = suppressUntil,
            lastAppliedPlayWhenReady = true,
            actionPlayWhenReady = true,
        )
        assertTrue("Matching playWhenReady within window is an echo", isEcho)
    }

    @Test
    fun echoSuppression_withinWindow_userDifferentPlayWhenReady_isNotEcho() {
        val now = 2000L
        val suppressUntil = 5000L
        val isEcho = MusicService.isControlEcho(
            isApplyingRemote = false,
            nowElapsedMs = now,
            suppressEchoUntilElapsedMs = suppressUntil,
            lastAppliedPlayWhenReady = true,
            actionPlayWhenReady = false, // User pressed pause
        )
        assertFalse("Toggling playWhenReady is a deliberate user control and not an echo", isEcho)
    }

    @Test
    fun echoSuppression_afterWindowExpired_notEcho() {
        val now = 6000L
        val suppressUntil = 5000L
        val isEcho = MusicService.isControlEcho(
            isApplyingRemote = false,
            nowElapsedMs = now,
            suppressEchoUntilElapsedMs = suppressUntil,
            lastAppliedIndex = 2,
            actionIndex = 2,
        )
        assertFalse("Events occurring after window expired must not be suppressed", isEcho)
    }

    // --- Requirements 1 & 6: Timeout & Bounded Recovery Allowance Tests ---

    @Test
    fun recoveryPolicy_timeoutErrorCode2000_isRecoverable() {
        // PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT must be classified as recoverable
        assertTrue(
            "Timeout error code 2000 must be marked recoverable for stream refresh",
            MusicService.shouldAttemptStreamRefreshForError(
                errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                httpStatusCode = null,
            )
        )
    }

    @Test
    fun recoveryPolicy_httpErrorCodes_areRecoverable() {
        val httpCodes = listOf(403, 404, 410, 416, 429, 500, 502, 503)
        for (code in httpCodes) {
            assertTrue(
                "HTTP $code must be recoverable",
                MusicService.shouldAttemptStreamRefreshForError(
                    errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    httpStatusCode = code,
                )
            )
        }
    }

    @Test
    fun recoveryPolicy_boundsRetries_toMaxTwoAttempts_preventingInfiniteLoops() {
        val now = 10_000L

        // 1st attempt: allowed
        val (firstAllowed, state1) = MusicService.checkRecoveryAllowance(null, now)
        assertTrue("First recovery attempt should be allowed", firstAllowed)
        assertEquals(1, state1.first)

        // 2nd attempt: allowed
        val (secondAllowed, state2) = MusicService.checkRecoveryAllowance(state1, now + 1000L)
        assertTrue("Second recovery attempt should be allowed", secondAllowed)
        assertEquals(2, state2.first)

        // 3rd attempt within window: rejected to prevent infinite retry loops
        val (thirdAllowed, _) = MusicService.checkRecoveryAllowance(state2, now + 2000L)
        assertFalse("Third recovery attempt within window MUST be rejected", thirdAllowed)
    }

    @Test
    fun recoveryPolicy_resetsAfterWindowExpires() {
        val now = 10_000L
        val state = 2 to now // exhausted max retries

        // After 45s window expires (e.g. 50s later)
        val (allowed, newState) = MusicService.checkRecoveryAllowance(state, now + 50_000L)
        assertTrue("Recovery allowance should reset after 45s window", allowed)
        assertEquals(1, newState.first)
    }

    // --- Requirement 4: Participant Resolution & Welcome Ordering Tests ---

    @Test
    fun participantResolution_emptyList_synthesizesBothHostAndGuest() {
        val result = MusicService.resolveParticipantsList(
            stateParticipants = emptyList(),
            hostId = "host123",
            hostName = "Host User",
            selfId = "guest456",
            selfName = "Guest User",
        )
        assertEquals(2, result.size)
        assertTrue("Host participant must be present", result.any { it.id == "host123" && it.isHost })
        assertTrue("Guest participant must be present", result.any { it.id == "guest456" && !it.isHost })
    }

    @Test
    fun participantResolution_missingHost_prependsHost() {
        val guestOnly = listOf(
            TogetherParticipant(id = "guest456", name = "Guest User", isHost = false, isPending = false, isConnected = true)
        )
        val result = MusicService.resolveParticipantsList(
            stateParticipants = guestOnly,
            hostId = "host123",
            hostName = "Host User",
            selfId = "guest456",
            selfName = "Guest User",
        )
        assertEquals(2, result.size)
        assertEquals("host123", result.first().id)
        assertTrue(result.first().isHost)
    }

    @Test
    fun participantResolution_missingSelf_appendsSelf() {
        val hostOnly = listOf(
            TogetherParticipant(id = "host123", name = "Host User", isHost = true, isPending = false, isConnected = true)
        )
        val result = MusicService.resolveParticipantsList(
            stateParticipants = hostOnly,
            hostId = "host123",
            hostName = "Host User",
            selfId = "guest456",
            selfName = "Guest User",
        )
        assertEquals(2, result.size)
        assertTrue("Self guest must be added", result.any { it.id == "guest456" && !it.isHost })
    }

    @Test
    fun participantResolution_bothPresent_preservesListWithoutDuplication() {
        val both = listOf(
            TogetherParticipant(id = "host123", name = "Host User", isHost = true, isPending = false, isConnected = true),
            TogetherParticipant(id = "guest456", name = "Guest User", isHost = false, isPending = false, isConnected = true),
            TogetherParticipant(id = "guest789", name = "Other Guest", isHost = false, isPending = false, isConnected = true),
        )
        val result = MusicService.resolveParticipantsList(
            stateParticipants = both,
            hostId = "host123",
            hostName = "Host User",
            selfId = "guest456",
            selfName = "Guest User",
        )
        assertEquals(3, result.size)
        assertEquals(listOf("host123", "guest456", "guest789"), result.map { it.id })
    }
}
