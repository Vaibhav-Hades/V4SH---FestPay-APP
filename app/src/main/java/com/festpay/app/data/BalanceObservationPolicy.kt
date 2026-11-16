package com.festpay.app.data

/** A window change during authentication is not a final balance response. */
internal object BalanceObservationPolicy {
    // Carrier result dialogs may retain an input control; collection excludes its value.
    fun canReadResult(sameSource: Boolean, sensitive: Boolean): Boolean =
        sameSource && !sensitive
}
