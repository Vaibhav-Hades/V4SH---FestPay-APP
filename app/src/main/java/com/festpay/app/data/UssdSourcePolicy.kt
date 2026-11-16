package com.festpay.app.data

object UssdSourcePolicy {
    val approvedPackages = setOf("com.android.phone", "com.android.server.telecom", "com.android.dialer", "com.google.android.dialer", "com.android.incallui", "com.miui.incallui",
        // Other makers' phone apps. Still accepted only when the package is a system app and the owner started the session.
        "com.samsung.android.incallui", "com.samsung.android.dialer", "com.oneplus.dialer", "com.coloros.phone",
        "com.oplus.phone", "com.vivo.phone", "com.android.contacts", "com.asus.phone")
    fun allows(rootPackage: String?, eventPackage: String?, systemApp: Boolean, owned: Boolean, eventTime: Long, startedAt: Long): Boolean =
        owned && systemApp && rootPackage in approvedPackages && rootPackage == eventPackage && eventTime >= startedAt && eventTime > 0
}
