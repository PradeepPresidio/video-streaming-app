package com.example.reelbox.domain

import com.example.reelbox.data.catalog.Title

object ProfileRules {
    const val MAX_PROFILES = 5
    const val MAX_NAME_LENGTH = 20
    const val AVATAR_COUNT = 6

    fun canAdd(existing: Int): Boolean = existing < MAX_PROFILES

    /** Trimmed name if valid (non-blank, ≤ 20 chars, not used by another profile), else null. */
    fun validName(name: String, otherNames: List<String>): String? {
        val n = name.trim().replace(Regex("\\s+"), " ")
        if (n.isEmpty() || n.length > MAX_NAME_LENGTH) return null
        if (otherNames.any { it.equals(n, ignoreCase = true) }) return null
        return n
    }

    /** What a profile may see: Kids profiles only get U and U/A 7+ titles. */
    fun visibility(isKids: Boolean): (Title) -> Boolean = if (isKids) { t -> t.maturity.kidsSafe } else { _ -> true }
}
