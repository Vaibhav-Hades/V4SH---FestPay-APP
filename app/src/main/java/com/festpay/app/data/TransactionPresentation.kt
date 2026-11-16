package com.festpay.app.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TransactionPresentation {
    fun timestamp(value: String, zone: ZoneId = ZoneId.systemDefault()): String = try {
        Instant.parse(value).atZone(zone)
            .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss z", Locale.ENGLISH))
    } catch (_: Exception) {
        "Timestamp unavailable for this older record"
    }
}
