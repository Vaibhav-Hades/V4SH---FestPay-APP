package com.festpay.app.data

enum class CarrierOutcome { SUCCESS, FAILED }

/**
 * Owner policy (2026-11-06): the carrier's own final message is the evidence V4SH acts on; V4SH does not query a bank.
 *  - SUCCESS only when the message says success AND prints a reference AND shows the reviewed amount (the reference is
 *    only parsed in that case);
 *  - FAILED when the message says it failed;
 *  - anything else (unclear, pending, no reference) decides nothing and the attempt stays open.
 */
object CarrierOutcomePolicy {
    fun decide(progress: CarrierProgress): CarrierOutcome? {
        if (progress.stage != CarrierStage.RESULT) return null
        return when (progress.resultWording) {
            CarrierResultObservation.SUCCESS_WORDING -> if (progress.reference != null) CarrierOutcome.SUCCESS else null
            CarrierResultObservation.FAILURE_WORDING -> CarrierOutcome.FAILED
            else -> null
        }
    }
}
