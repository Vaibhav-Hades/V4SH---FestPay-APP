package com.festpay.app

import com.festpay.app.data.BalanceParser
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class BalanceParserSafetyTest {
    @Test fun `static balance is parsed despite unrelated account number before it`() {
        assertEquals(BigDecimal("12345.67"), BalanceParser.parseBalance("Account 987654321012 Available balance is Rs. 12,345.67").balance)
    }
    @Test fun `menu and authentication numbers cannot become a balance`() {
        for (text in listOf("Select option 1 Send Money 3 Check Balance 7 UPI PIN", "Check Balance 3", "Balance inquiry enter PIN 123456", "Available balance password 123456", "Mobile 9876543210", "Transaction ID 123456", "Random 12345", "Balance 9876543210")) {
            assertNull(text, BalanceParser.parseBalance(text).balance)
        }
    }
    @Test fun `ambiguous and malformed values are rejected`() {
        for (text in listOf("Available balance Rs 10.00 Ledger balance Rs 20.00", "Balance Rs 10.00 fee Rs 2.00", "Balance Rs -10.00", "Balance Rs 12,34.56", "Balance Rs 10.123", "Balance unavailable 123")) {
            assertNull(text, BalanceParser.parseBalance(text).balance)
        }
    }
    @Test fun `plain and grouped complete values preserve precision`() {
        assertEquals(BigDecimal("12345.67"), BalanceParser.parseBalance("Balance INR 12345.67").balance)
        assertEquals(BigDecimal("125500.50"), BalanceParser.parseBalance("Available balance ₹1,25,500.50").balance)
        assertEquals(BigDecimal("0.00"), BalanceParser.parseBalance("Balance Rs. 0.00").balance)
    }
}
