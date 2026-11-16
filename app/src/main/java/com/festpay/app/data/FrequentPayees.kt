package com.festpay.app.data

data class PastPayee(val vpa: String, val name: String, val succeeded: Boolean)
data class FrequentPayee(val vpa: String, val name: String, val times: Int)

/**
 * People the owner pays often, worked out from the local history (no extra storage). Only payments the carrier itself closed as
 * successful count, so a failed or unclear attempt never promotes someone to "Pay again".
 * [history] must be newest first; ties keep the most recently paid payee first.
 */
object FrequentPayees {
    fun from(history: List<PastPayee>, limit: Int = 5): List<FrequentPayee> {
        val byVpa = LinkedHashMap<String, Triple<String, String, Int>>() // key -> (display vpa, name, count)
        for (p in history) {
            if (!p.succeeded || !PaymentInputValidator.isVpa(p.vpa)) continue
            val key = p.vpa.lowercase()
            val old = byVpa[key]
            byVpa[key] = if (old == null) Triple(p.vpa, p.name.trim(), 1)
            else Triple(old.first, old.second.ifBlank { p.name.trim() }, old.third + 1)
        }
        return byVpa.values.map { FrequentPayee(it.first, it.second, it.third) }.sortedByDescending { it.times }.take(limit)
    }
}
