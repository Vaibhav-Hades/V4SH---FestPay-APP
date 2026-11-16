package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

class AccessibilityConnectionTest {
    private class Host : UssdSessionHost {
        var alive = true
        override fun connectionAlive() = alive
        override fun armInitializedSession(command: OwnedUssdCommand, connectionId: String) = false
    }
    @Test fun connectedDisconnectedAndStaleLiveInstanceAreDistinct() {
        val connection = AccessibilityConnection(); val host = Host()
        assertFalse(connection.snapshot.connected)
        connection.connect(host)
        assertTrue(connection.snapshot.connected); assertTrue(connection.snapshot.instanceAvailable)
        host.alive = false
        assertFalse(connection.snapshot.connected)
        assertNull(connection.claim(OwnedUssdCommand.balance()))
        assertTrue(connection.disconnect(host)); assertFalse(connection.snapshot.instanceAvailable)
    }
    @Test fun duplicateConnectionDoesNotResetAnOwnedSession() {
        val connection = AccessibilityConnection(); val host = Host()
        val epoch = connection.connect(host); val command = OwnedUssdCommand.balance()
        connection.claim(command); connection.initialize(host, command.sessionId, epoch)
        assertEquals(epoch, connection.connect(host))
        assertEquals(command.sessionId, connection.snapshot.sessionId)
        assertTrue(connection.snapshot.initialized)
        assertFalse(connection.initialize(host, command.sessionId, epoch))
    }
    @Test fun reconnectInvalidatesAckAndNeverAutomaticallyArmsOrRelaunches() {
        val connection = AccessibilityConnection(); val old = Host(); val next = Host()
        val epoch = connection.connect(old); val command = OwnedUssdCommand.balance()
        connection.claim(command); connection.initialize(old, command.sessionId, epoch)
        assertTrue(connection.acknowledge(command, epoch))
        assertTrue(connection.arm(old, command, epoch))
        val nextEpoch = connection.connect(next)
        assertNotEquals(epoch, nextEpoch)
        assertFalse(connection.disconnect(old)); assertTrue(connection.snapshot.connected)
        assertFalse(connection.snapshot.initialized); assertFalse(connection.snapshot.acknowledged)
        assertFalse(connection.snapshot.armed); assertNull(connection.snapshot.sessionId)
        assertFalse(connection.acknowledge(command, epoch)); assertFalse(connection.arm(old, command, epoch))
    }
    @Test fun missingAckWrongEpochAndWrongOwnerCannotArm() {
        val connection = AccessibilityConnection(); val host = Host(); val wrong = Host()
        val epoch = connection.connect(host); val command = OwnedUssdCommand.balance()
        assertEquals(epoch, connection.claim(command))
        assertNull(connection.claim(OwnedUssdCommand.balance()))
        assertFalse(connection.acknowledge(command, epoch))
        assertFalse(connection.initialize(wrong, command.sessionId, epoch))
        assertTrue(connection.initialize(host, command.sessionId, epoch))
        assertFalse(connection.arm(host, command, epoch))
        assertFalse(connection.acknowledge(command, "stale"))
        assertTrue(connection.acknowledge(command, epoch))
        assertFalse(connection.arm(wrong, command, epoch))
        assertTrue(connection.arm(host, command, epoch))
        assertFalse(connection.arm(host, command, epoch))
    }
    @Test fun releasedLeaseRejectsLateAckAndCannotReleaseNewOwner() {
        val connection = AccessibilityConnection(); val host = Host(); val epoch = connection.connect(host)
        val old = OwnedUssdCommand.balance(); val next = OwnedUssdCommand.balance()
        connection.claim(old); connection.initialize(host, old.sessionId, epoch)
        assertTrue(connection.release(old.sessionId)); connection.claim(next)
        assertFalse(connection.acknowledge(old, epoch)); assertFalse(connection.release(old.sessionId))
        assertEquals(next.sessionId, connection.snapshot.sessionId)
        connection.clearSession()
        assertFalse(connection.snapshot.armed); assertFalse(connection.snapshot.acknowledged)
    }
    @Test fun observedBindingLossCannotResurrectAnOldAcknowledgment() {
        val connection = AccessibilityConnection(); val host = Host()
        val epoch = connection.connect(host); val command = OwnedUssdCommand.balance()
        connection.claim(command); connection.initialize(host, command.sessionId, epoch)
        connection.acknowledge(command, epoch)
        host.alive = false
        assertFalse(connection.snapshot.connected)
        host.alive = true
        assertFalse(connection.snapshot.connected)
        assertFalse(connection.acknowledge(command, epoch))
        assertFalse(connection.arm(host, command, epoch))
        assertNotEquals(epoch, connection.connect(host))
        assertFalse(connection.snapshot.acknowledged); assertFalse(connection.snapshot.armed)
    }
}
