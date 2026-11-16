package com.festpay.app

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Public widget action handlers only. Not a carrier dialog, bank response or USSD submission. */
class UssdInputControlFixtureTest {
    @Test fun localNonfinancialWidgetsAdvertiseAndAcceptTextAndClickActions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(UssdControlFixtureActivity::class.java).use { scenario ->
          val laidOut = CountDownLatch(1)
          scenario.onActivity { activity ->
            if (activity.button.isLaidOut && activity.button.width > 0) laidOut.countDown()
            else activity.button.viewTreeObserver.addOnGlobalLayoutListener {
                if (activity.button.width > 0 && activity.button.isShown) laidOut.countDown()
            }
          }
          assertTrue("Fixture controls must be visible and attached", laidOut.await(5, TimeUnit.SECONDS))
          instrumentation.waitForIdleSync()
          scenario.onActivity { activity ->
            val field = activity.field
            val node = field.createAccessibilityNodeInfo()
            assertTrue(node.isEditable)
            assertTrue(node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT })
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "nonfinancial fixture")
            }
            assertTrue(field.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
            assertEquals("nonfinancial fixture", field.text.toString()) // Only this synthetic local widget.
            val button = activity.button
            assertTrue(button.createAccessibilityNodeInfo().actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
            assertTrue(button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
            assertEquals(1, activity.clicks)
          }
        }
    }
}
