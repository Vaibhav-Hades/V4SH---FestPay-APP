package com.festpay.app

import com.festpay.app.data.UssdNavigationPolicy
import org.junit.Assert.*
import org.junit.Test

class UssdNavigationPolicyTest {
    @Test fun rootRequiresKnownBankingOptionMapping() {
        assertTrue(UssdNavigationPolicy.isRootMenu("Carrier info Select option 1.Send Money 2.Request money 3.Check Balance 7.UPI PIN"))
        assertTrue(UssdNavigationPolicy.isRootMenu("1 Send Money\n3 Check Balance"))
        assertFalse(UssdNavigationPolicy.isRootMenu("Select option 1. Buy data 3. Check balance"))
        assertFalse(UssdNavigationPolicy.isRootMenu("1.Check balance 3.Send money"))
    }
    @Test fun reviewRequiresExplicitMatchingRecipientAndExactAmount() {
        assertTrue(UssdNavigationPolicy.isMatchingReview("Confirm transfer to synthetic@upi Rs. 1.00. Press 1 to confirm", "synthetic@upi", "1.00"))
        for (text in listOf("Enter remarks to synthetic@upi Rs.1 Send", "Confirm to other@upi Rs.1", "Confirm to synthetic@upi Rs.10", "Confirm to synthetic@upi Rs.1e3", "Confirm to synthetic@upi Rs.1,000", "Confirm to synthetic@upi Rs.1 and fee Rs.2", "Unsuccessful. Confirm to synthetic@upi Rs.1", "Confirm to synthetic@upi Rs.1 Enter UPI PIN")) {
            assertFalse(text, UssdNavigationPolicy.isMatchingReview(text, "synthetic@upi", "1.00"))
        }
        assertEquals("1", UssdNavigationPolicy.confirmationDigit("Press 1 to confirm"))
        assertNull(UssdNavigationPolicy.confirmationDigit("Confirm? Send"))
        assertTrue(UssdNavigationPolicy.isAmountPrompt("Enter amount for UPI ID"))
        assertFalse(UssdNavigationPolicy.isAmountPrompt("Confirm amount Rs.1"))
    }
    @Test fun reviewRejectsEmbeddedAddressesAndMultiplePossibleRecipients() {
        for (address in listOf("evil@synthetic@upi", "synthetic@upi/extra", "ésynthetic@upi", "other@upi; intended synthetic@upi")) {
            assertFalse(UssdNavigationPolicy.isMatchingReview("Confirm transfer to $address Rs.1.00 Press 1 to confirm", "synthetic@upi", "1.00"))
        }
        assertTrue(UssdNavigationPolicy.isMatchingReview("Confirm transfer to (synthetic@upi) Rs.1.00 Press 2 to confirm", "synthetic@upi", "1.00"))
    }
}
