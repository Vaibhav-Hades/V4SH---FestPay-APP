package com.festpay.app.data

object PaymentInputValidator {
    fun normalizedVpa(input: String): String? {
        if (input.any { it.isISOControl() }) return null
        return input.trim().takeIf(::isVpa)
    }
    data class Profile(val vpa: String, val name: String)
    fun profile(vpa: String, name: String): Profile? {
        if (vpa.any { it.isISOControl() } || name.any { it.isISOControl() }) return null
        val target = vpa.trim()
        val displayName = name.trim()
        if (!isVpa(target) || displayName.isEmpty() || displayName.length > 100 || displayName.any { it.isISOControl() }) return null
        return Profile(target, displayName)
    }
    fun isVpa(value: String): Boolean = value.length <= 255 && Regex("[A-Za-z0-9][A-Za-z0-9._-]*@[A-Za-z][A-Za-z0-9.-]*").matches(value)
    fun mobile(value: String): String? {
        val clean = value.replace(" ", "").removePrefix("+91")
        return clean.takeIf { Regex("[6-9][0-9]{9}").matches(it) }
    }
    fun amount(value: String): String? {
        if (!Regex("[0-9]{1,7}(?:\\.[0-9]{1,2})?").matches(value)) return null
        val amount = value.toBigDecimalOrNull() ?: return null
        return amount.takeIf { it.signum() > 0 }?.setScale(2)?.toPlainString()
    }
}
