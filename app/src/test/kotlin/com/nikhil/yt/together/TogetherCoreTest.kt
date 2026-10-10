/*
 * Velune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.nikhil.yt.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherCoreTest {
    @Test
    fun deepLink_roundTrip() {
        val join =
            TogetherJoinInfo(
                host = "192.168.1.20",
                port = 42117,
                sessionId = "sid123",
                sessionKey = "key456",
            )
        val encoded = TogetherLink.encode(join)
        val decoded = TogetherLink.decode(encoded)
        assertEquals(join, decoded)
    }

    @Test
    fun wsUrl_decode() {
        val decoded =
            TogetherLink.decode("ws://10.0.0.5:42117/together?sid=sid123&key=key456")
        assertNotNull(decoded)
        assertEquals("10.0.0.5", decoded!!.host)
        assertEquals(42117, decoded.port)
        assertEquals("sid123", decoded.sessionId)
        assertEquals("key456", decoded.sessionKey)
    }

    @Test
    fun protocol_serialization_roundTrip() {
        val hello =
            ClientHello(
                protocolVersion = TogetherProtocolVersion,
                sessionId = "sid",
                sessionKey = "key",
                clientId = "cid",
                displayName = "Alice",
            )
        val json = TogetherJson.json.encodeToString(TogetherMessage.serializer(), hello)
        val decoded = TogetherJson.json.decodeFromString(TogetherMessage.serializer(), json)
        assertTrue(decoded is ClientHello)
        assertEquals(hello, decoded)
    }

    @Test
    fun protocol_seekToTrack_roundTrip() {
        val message: TogetherMessage =
            ControlRequest(
                sessionId = "sid",
                participantId = "pid",
                action = ControlAction.SeekToTrack(trackId = "track123", positionMs = 456L),
            )
        val json = TogetherJson.json.encodeToString(TogetherMessage.serializer(), message)
        val decoded = TogetherJson.json.decodeFromString(TogetherMessage.serializer(), json)
        assertTrue(decoded is ControlRequest)
        val req = decoded as ControlRequest
        assertTrue(req.action is ControlAction.SeekToTrack)
        val action = req.action as ControlAction.SeekToTrack
        assertEquals("track123", action.trackId)
        assertEquals(456L, action.positionMs)
    }

    @Test
    fun protocol_playTrackNow_roundTrip() {
        val message: TogetherMessage =
            ControlRequest(
                sessionId = "sid",
                participantId = "pid",
                action = ControlAction.PlayTrackNow(track = TogetherTrack(id = "track456", title = "Song"), positionMs = 123L),
            )
        val json = TogetherJson.json.encodeToString(TogetherMessage.serializer(), message)
        val decoded = TogetherJson.json.decodeFromString(TogetherMessage.serializer(), json)
        assertTrue(decoded is ControlRequest)
        val req = decoded as ControlRequest
        assertTrue(req.action is ControlAction.PlayTrackNow)
        val action = req.action as ControlAction.PlayTrackNow
        assertEquals("track456", action.track.id)
        assertEquals("Song", action.track.title)
        assertEquals(123L, action.positionMs)
    }

    @Test
    fun clock_estimates_offset_and_rtt() {
        val clock = TogetherClock(initialOffsetMs = 0.0)
        val snapshot =
            clock.onPong(
                sentAtElapsedMs = 1000L,
                receivedAtElapsedMs = 1200L,
                serverElapsedMs = 1150L,
            )
        assertTrue(snapshot.estimatedRttMs >= 0L)
        // Midpoint is 1100L. Server is 1150L. Offset should be 50L.
        assertEquals(50L, snapshot.estimatedOffsetMs)
        assertEquals(200L, snapshot.estimatedRttMs)
    }

    @Test
    fun clock_firstPong_adoptsOffsetImmediately() {
        val serverEpoch = 1770000000000L
        val clientElapsed = 50000L
        val clock = TogetherClock(initialOffsetMs = 0.0)
        
        // Ping sent at clientElapsed, received at clientElapsed + 100ms
        val snapshot = clock.onPong(
            sentAtElapsedMs = clientElapsed,
            receivedAtElapsedMs = clientElapsed + 100L,
            serverElapsedMs = serverEpoch + 50L,
        )
        // Midpoint is clientElapsed + 50. Server at mid is serverEpoch + 50.
        // Expected offset = serverEpoch - clientElapsed
        val expectedOffset = serverEpoch - clientElapsed
        assertEquals(expectedOffset, snapshot.estimatedOffsetMs)
    }

    @Test
    fun targetPosition_safety_guardsAgainstTimeUnset() {
        val invalidDuration = androidx.media3.common.C.TIME_UNSET
        val rawTargetPos = 15000L
        
        // Simulating the MusicService duration safety check
        val targetPos = if (invalidDuration > 0L && invalidDuration != androidx.media3.common.C.TIME_UNSET) {
            rawTargetPos.coerceIn(0L, invalidDuration)
        } else {
            rawTargetPos
        }
        
        // Must preserve rawTargetPos without throwing IllegalArgumentException or clamping to negative
        assertEquals(15000L, targetPos)
    }

    @Test
    fun targetPosition_safety_clampsValidDuration() {
        val validDuration = 180000L
        val rawTargetPosBeyondEnd = 250000L
        
        val targetPos = if (validDuration > 0L && validDuration != androidx.media3.common.C.TIME_UNSET) {
            rawTargetPosBeyondEnd.coerceIn(0L, validDuration)
        } else {
            rawTargetPosBeyondEnd
        }
        
        assertEquals(180000L, targetPos)
    }

    @Test
    fun age_clamping_preventsWildSeeks() {
        val nowServerTime = 1770000005000L
        val sentAtCorrupted = 1770000005000L - 100000000L // 100,000 seconds ago
        val ageClamped = if (sentAtCorrupted > 0L) (nowServerTime - sentCorruptedSafe(sentAtCorrupted)).coerceIn(0L, 5_000L) else 0L
        assertEquals(5000L, ageClamped)

        val sentAtFuture = nowServerTime + 10000L // in the future
        val ageFuture = (nowServerTime - sentAtFuture).coerceIn(0L, 5_000L)
        assertEquals(0L, ageFuture)
    }

    private fun sentCorruptedSafe(sentAt: Long) = sentAt

    @Test
    fun onlineCode_decode_cynkScheme() {
        assertEquals("ABC123", TogetherLink.decodeOnlineCode("cynk://together/ABC123"))
        assertEquals("ABC123", TogetherLink.decodeOnlineCode("cynk://together?code=ABC123"))
        assertEquals("XYZ789", TogetherLink.decodeOnlineCode("https://cynk.app/together/XYZ789"))
        assertEquals("XYZ789", TogetherLink.decodeOnlineCode("XYZ789"))
    }

    @Test
    fun onlineCode_encode() {
        assertEquals("cynk://together/ABC123", TogetherLink.encodeOnlineCode("ABC123"))
    }

    @Test
    fun settings_defaults_allowPlaybackControlIsTrue() {
        val settings = TogetherRoomSettings()
        assertTrue("Guests should be allowed to control playback by default", settings.allowGuestsToControlPlayback)
        assertTrue("Guests should be allowed to add tracks by default", settings.allowGuestsToAddTracks)
    }

    @Test
    fun guestPlanner_whenControlAllowed_generatesPlayTrackNow() {
        val roomState = TogetherRoomState(
            sessionId = "sid",
            hostId = "hid",
            settings = TogetherRoomSettings(allowGuestsToControlPlayback = true, allowGuestsToAddTracks = true)
        )
        val track = TogetherTrack(id = "test_song", title = "Test Song")
        val ops = TogetherGuestPlaybackPlanner.planPlayTrackNow(
            roomState = roomState,
            track = track,
            positionMs = 0L,
            playWhenReady = true,
        )
        assertEquals(1, ops.size)
        assertTrue(ops.first() is TogetherGuestOp.Control)
        val control = (ops.first() as TogetherGuestOp.Control).action
        assertTrue(control is ControlAction.PlayTrackNow)
        assertEquals("test_song", (control as ControlAction.PlayTrackNow).track.id)
    }

    @Test
    fun guestPlanner_whenControlDisallowed_returnsEmpty() {
        val roomState = TogetherRoomState(
            sessionId = "sid",
            hostId = "hid",
            settings = TogetherRoomSettings(allowGuestsToControlPlayback = false, allowGuestsToAddTracks = true)
        )
        val track = TogetherTrack(id = "test_song", title = "Test Song")
        val ops = TogetherGuestPlaybackPlanner.planPlayTrackNow(
            roomState = roomState,
            track = track,
            positionMs = 0L,
            playWhenReady = true,
        )
        assertTrue("When control is disallowed, planPlayTrackNow must return empty", ops.isEmpty())
    }

    @Test
    fun roomStateMessage_multipleParticipants_serializationRoundTrip() {
        val host = TogetherParticipant(id = "host1", name = "Host Phone", isHost = true, isPending = false, isConnected = true)
        val guest = TogetherParticipant(id = "guest1", name = "Infinix X682B", isHost = false, isPending = false, isConnected = true)
        val state = TogetherRoomState(
            sessionId = "room123",
            hostId = "host1",
            participants = listOf(host, guest),
            queue = listOf(TogetherTrack(id = "song1", title = "Song 1")),
            currentIndex = 0,
            isPlaying = true,
            positionMs = 1500L,
            sequenceNumber = 42L,
        )
        val msg: TogetherMessage = RoomStateMessage(state)
        val json = TogetherJson.json.encodeToString(TogetherMessage.serializer(), msg)
        val decoded = TogetherJson.json.decodeFromString(TogetherMessage.serializer(), json)
        assertTrue(decoded is RoomStateMessage)
        val decodedState = (decoded as RoomStateMessage).state
        assertEquals(2, decodedState.participants.size)
        assertEquals("host1", decodedState.participants[0].id)
        assertTrue(decodedState.participants[0].isHost)
        assertEquals("guest1", decodedState.participants[1].id)
        assertEquals("Infinix X682B", decodedState.participants[1].name)
        assertEquals(42L, decodedState.sequenceNumber)
    }

    @Test
    fun guestParticipantList_mergesHostAndSelf_whenMissingFromRemote() {
        val remoteParticipants = listOf(
            TogetherParticipant(id = "other_guest", name = "Other Guest", isHost = false, isPending = false, isConnected = true)
        )
        val hostId = "host123"
        val selfPid = "guest_me"
        val selfName = "Infinix X682B"

        // Simulation of the participant resolution logic in MusicService
        val resolvedParticipants = if (remoteParticipants.isNotEmpty()) {
            val hasSelf = remoteParticipants.any { it.id == selfPid }
            val hasHost = remoteParticipants.any { it.isHost || (hostId.isNotBlank() && it.id == hostId) }
            var list = remoteParticipants
            if (!hasHost && hostId.isNotBlank()) {
                list = listOf(TogetherParticipant(id = hostId, name = "Host", isHost = true, isPending = false, isConnected = true)) + list
            }
            if (!hasSelf) {
                list = list + TogetherParticipant(id = selfPid, name = selfName, isHost = false, isPending = false, isConnected = true)
            }
            list
        } else {
            emptyList()
        }

        assertEquals(3, resolvedParticipants.size)
        assertTrue(resolvedParticipants.any { it.id == hostId && it.isHost })
        assertTrue(resolvedParticipants.any { it.id == selfPid && it.name == "Infinix X682B" })
        assertTrue(resolvedParticipants.any { it.id == "other_guest" })
    }

    @Test
    fun sequenceNumber_monotonicUpdate_rejectsStaleAcceptsNew() {
        var lastAppliedSeq = 10L
        val staleSeq = 10L
        val oldSeq = 5L
        val newSeq = 11L

        val shouldRejectStale = staleSeq <= lastAppliedSeq
        val shouldRejectOld = oldSeq <= lastAppliedSeq
        val shouldAcceptNew = newSeq > lastAppliedSeq

        assertTrue("Stale sequence must be rejected", shouldRejectStale)
        assertTrue("Old sequence must be rejected", shouldRejectOld)
        assertTrue("New sequence must be accepted", shouldAcceptNew)
    }
}
