package com.festpay.app.data

/** Window removal is UI evidence only, never payment or network-session finality. */
class CarrierDialogLifecycle {
    private var currentWindow: Int? = null
    private var lastEvent = 0L
    private var removed = false
    private var completed = false

    fun observeOwnedWindow(id: Int, time: Long) {
        if (completed || id < 0 || time <= lastEvent) return
        currentWindow = id; lastEvent = time; removed = false
    }

    fun removedWindow(id: Int, time: Long) {
        if (completed || id != currentWindow || time <= lastEvent) return
        lastEvent = time; removed = true
    }

    fun canReturn(owned: Boolean, completeSnapshot: Boolean, phoneWindowPresent: Boolean,
                  visibleWindowIds: Set<Int>): Boolean = !completed && owned && removed &&
        completeSnapshot && !phoneWindowPresent && currentWindow !in visibleWindowIds

    fun consumeReturn(owned: Boolean, completeSnapshot: Boolean, phoneWindowPresent: Boolean,
                      visibleWindowIds: Set<Int>): Boolean {
        if (!canReturn(owned, completeSnapshot, phoneWindowPresent, visibleWindowIds)) return false
        completed = true
        return true
    }
}
