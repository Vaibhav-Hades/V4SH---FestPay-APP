package com.festpay.app.data

/** Describes wording only. None of these observations authorizes a financial state. */
enum class CarrierResultObservation { FAILURE_WORDING, SUCCESS_WORDING, INCONCLUSIVE }

object CarrierResultClassifier {
    private val failure = Regex("\\b(?:unsuccessful|failure|failed|declined|rejected|not\\s+successful|not\\s+completed)\\b", RegexOption.IGNORE_CASE)
    private val success = Regex("\\b(?:success|successful)\\b", RegexOption.IGNORE_CASE)
    fun observe(text: String): CarrierResultObservation = when {
        failure.containsMatchIn(text) -> CarrierResultObservation.FAILURE_WORDING
        success.containsMatchIn(text) -> CarrierResultObservation.SUCCESS_WORDING
        else -> CarrierResultObservation.INCONCLUSIVE
    }
}
