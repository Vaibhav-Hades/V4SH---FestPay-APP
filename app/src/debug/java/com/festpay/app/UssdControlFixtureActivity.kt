package com.festpay.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Test-only visible native controls. No USSD, service command, bank data or journal access. */
class UssdControlFixtureActivity : Activity() {
    lateinit var field: EditText
        private set
    lateinit var button: Button
        private set
    var clicks = 0
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        field = EditText(this).apply { setText("before") }
        button = Button(this).apply { text = "Fixture action"; setOnClickListener { clicks++ } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@UssdControlFixtureActivity).apply { text = "Non-financial local control fixture" })
            addView(field); addView(button)
        })
    }
}
