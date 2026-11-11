package com.example.reelbox.domain

import com.example.reelbox.data.catalog.Maturity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRulesTest {
    @Test fun maxFiveProfiles() {
        assertTrue(ProfileRules.canAdd(4))
        assertFalse(ProfileRules.canAdd(5))
    }

    @Test fun nameValidation() {
        assertEquals("Kids Room", ProfileRules.validName("  Kids   Room ", emptyList()))
        assertNull(ProfileRules.validName("   ", emptyList()))
        assertNull(ProfileRules.validName("x".repeat(21), emptyList()))
        assertNull(ProfileRules.validName("kids", listOf("Kids")))
    }

    @Test fun kidsSeeOnlyUAndUA7() {
        val kids = ProfileRules.visibility(isKids = true)
        val seen = Maturity.entries.filter { kids(testTitle("t", maturity = it)) }
        assertEquals(listOf(Maturity.U, Maturity.UA7), seen)
        assertTrue(Maturity.entries.all { ProfileRules.visibility(false)(testTitle("t", maturity = it)) })
    }
}
