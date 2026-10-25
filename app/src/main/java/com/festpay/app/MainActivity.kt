package com.festpay.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.Scaffold
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.festpay.app.ui.navigation.V4SHBottomNavigation
import com.festpay.app.ui.navigation.MainNavHost

class MainActivity : ComponentActivity() {

    private lateinit var telephonyManager: TelephonyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        telephonyManager =
            getSystemService(TelephonyManager::class.java)

        setContent {
            val navController = rememberNavController()
            Scaffold(
                bottomBar = { V4SHBottomNavigation(navController) }
            ) {
                MainNavHost(navController)
            }
        }
    }

    @Composable
    fun UssdTester() {

        var response by remember { mutableStateOf("No response yet") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {

            Text("V4SH - USSD Test")

            Button(
                onClick = {

                    if (
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CALL_PHONE
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {

                        requestPermissions(
                            arrayOf(Manifest.permission.CALL_PHONE),
                            100
                        )

                    } else {
                        // Signal the AccessibilityService that a new USSD session is starting
                        val intent = Intent(FestPayAccessibilityService.ACTION_NEW_USSD_SESSION)
                        intent.setPackage(this@MainActivity.packageName)
                        sendBroadcast(intent)
                        sendUssd { response = it }
                    }
                }
            ) {
                Text("TEST *99#")
            }

            Text(
                text = response,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendUssd(responseUpdater: (String) -> Unit) {

        telephonyManager.sendUssdRequest(
            "*99#",

            object : TelephonyManager.UssdResponseCallback() {

                override fun onReceiveUssdResponse(
                    telephonyManager: TelephonyManager,
                    request: String,
                    response: CharSequence
                ) {
                    responseUpdater(response.toString())
                }

                override fun onReceiveUssdResponseFailed(
                    telephonyManager: TelephonyManager,
                    request: String,
                    failureCode: Int
                ) {
                    responseUpdater(
                        "USSD failed. Error code: $failureCode"
                    )
                }
            },

            Handler(Looper.getMainLooper())
        )
    }
}