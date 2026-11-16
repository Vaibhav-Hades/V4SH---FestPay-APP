package com.festpay.app.data

import java.net.URI
import java.net.URLDecoder

data class ParsedUpiQr(val vpa: String, val name: String?, val amount: String?, val note: String?, val currency: String = "INR")
enum class QrIssue { NONE, MALFORMED_URI, UNSUPPORTED_SCHEME, UNSUPPORTED_ACTION, DUPLICATE_FIELD, MISSING_RECIPIENT, INVALID_RECIPIENT, INVALID_AMOUNT, UNSUPPORTED_CURRENCY, INVALID_FIELD }
data class QrAnalysis(val value: ParsedUpiQr?, val issue: QrIssue)

object UpiQrParser {
    private class Rejected(val issue: QrIssue) : IllegalArgumentException()
    private fun requireField(ok: Boolean, issue: QrIssue) { if (!ok) throw Rejected(issue) }
    fun parse(raw: String): ParsedUpiQr? = analyze(raw).value
    fun analyze(raw: String): QrAnalysis = try {
        requireField(raw.length <= 4096 && raw.none { it.isISOControl() }, QrIssue.INVALID_FIELD)
        val uri = URI(raw.trim())
        requireField(uri.scheme.equals("upi", true), QrIssue.UNSUPPORTED_SCHEME)
        requireField(uri.host.equals("pay", true), QrIssue.UNSUPPORTED_ACTION)
        requireField(uri.userInfo == null && uri.port == -1 && uri.fragment == null && uri.path.orEmpty() in listOf("", "/"), QrIssue.MALFORMED_URI)
        val fields = linkedMapOf<String, String>()
        for (part in uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }) {
            val bits = part.split('=', limit = 2)
            requireField(bits.size == 2, QrIssue.INVALID_FIELD)
            val key = URLDecoder.decode(bits[0], "UTF-8").lowercase()
            val value = URLDecoder.decode(bits[1], "UTF-8")
            requireField(key !in fields, QrIssue.DUPLICATE_FIELD)
            requireField(value.length <= 1024 && value.none {
                it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() || it == '\uFFFD'
            } && wellFormedUnicode(value), QrIssue.INVALID_FIELD)
            fields[key] = value
        }
        val vpa = fields["pa"] ?: throw Rejected(QrIssue.MISSING_RECIPIENT)
        requireField(PaymentInputValidator.isVpa(vpa), QrIssue.INVALID_RECIPIENT)
        requireField(fields["cu"].isNullOrBlank() || fields["cu"].equals("INR", true), QrIssue.UNSUPPORTED_CURRENCY)
        val rawAmount = fields["am"]
        // Missing/blank amount requires entry; an explicitly zero/negative amount is invalid.
        val amount = if (rawAmount.isNullOrBlank()) null
            else PaymentInputValidator.amount(rawAmount) ?: throw Rejected(QrIssue.INVALID_AMOUNT)
        requireField(fields["pn"].orEmpty().length <= 100 && fields["tn"].orEmpty().length <= 140, QrIssue.INVALID_FIELD)
        QrAnalysis(ParsedUpiQr(vpa, fields["pn"], amount, fields["tn"], "INR"), QrIssue.NONE)
    } catch (error: Rejected) { QrAnalysis(null, error.issue) }
      catch (_: java.net.URISyntaxException) { QrAnalysis(null, QrIssue.MALFORMED_URI) }
      catch (_: Exception) { QrAnalysis(null, QrIssue.INVALID_FIELD) }

    private fun wellFormedUnicode(value: String): Boolean {
        var i = 0
        while (i < value.length) {
            val c = value[i++]
            if (c.isHighSurrogate()) { if (i == value.length || !value[i++].isLowSurrogate()) return false }
            else if (c.isLowSurrogate()) return false
        }
        return true
    }
}
