package com.helgolabs.trego.utils

/**
 * Utility class that provides a dictionary of bank name aliases and search optimizations.
 */
object BankNameDictionary {

    /**
     * Maps common aliases, abbreviations, and misspellings to their official bank names.
     * Key: The search term variant (lowercase)
     * Value: List of official bank names that should match this term
     */
    private val bankAliasMap = mapOf(
        // Co-operative Bank aliases
        "coop" to listOf("Co-operative Bank"),
        "co-op" to listOf("Co-operative Bank"),
        "cooperative" to listOf("Co-operative Bank"),

        // Nationwide aliases
        "nbs" to listOf("Nationwide Building Society", "Nationwide"),
        "nation wide" to listOf("Nationwide Building Society", "Nationwide"),


        // Halifax/Lloyds/HBOS group
        "halifax" to listOf("Halifax", "Halifax Bank of Scotland", "HBOS"),
        "lloyds" to listOf("Lloyds Bank", "Lloyds TSB", "Lloyds Banking Group"),
        "hbos" to listOf("Halifax Bank of Scotland", "Halifax", "Bank of Scotland"),

        // NatWest/RBS group
        "natwest" to listOf("NatWest", "National Westminster Bank"),
        "rbs" to listOf("Royal Bank of Scotland", "RBS"),

        // Barclays aliases
        "barclays" to listOf("Barclays", "Barclaycard"),
        "barclay" to listOf("Barclays", "Barclaycard"),

        // HSBC aliases
        "hsbc" to listOf("HSBC"),
        "midland" to listOf("HSBC"), // Historical name before HSBC acquisition

        // Santander aliases
        "santander" to listOf("Santander", "Santander UK"),
        "abbey" to listOf("Santander"), // Former Abbey National

        // Digital banks
        "monzo" to listOf("Monzo"),
        "starling" to listOf("Starling Bank", "Starling"),
        "revolut" to listOf("Revolut"),

        // Metro Bank
        "metro" to listOf("Metro Bank"),

        // American Express
        "amex" to listOf("American Express"),

        // TSB
        "tsb" to listOf("TSB Bank", "TSB"),

        // Virgin Money
        "virgin" to listOf("Virgin Money"),

        // First Direct
        "first direct" to listOf("First Direct"),
        "firstdirect" to listOf("First Direct")
    )

    /**
     * Checks if the query term matches an alias and returns the official bank names.
     * Returns null if no match is found.
     */
    fun getMatchingBankNames(query: String): List<String>? {
        val lowercaseQuery = query.lowercase().trim()
        return bankAliasMap[lowercaseQuery]
    }

    /**
     * Checks if a bank name matches against a search query, including alias checking.
     * This considers both direct name matching and aliases.
     */
    fun isMatchingSearchTerm(bankName: String, searchQuery: String): Boolean {
        val lowercaseQuery = searchQuery.lowercase().trim()
        val lowercaseBankName = bankName.lowercase()

        // Direct match check
        if (lowercaseBankName.contains(lowercaseQuery)) {
            return true
        }

        // Alias match check
        val matchedNames = getMatchingBankNames(lowercaseQuery) ?: return false
        return matchedNames.any {
            lowercaseBankName.contains(it.lowercase())
        }
    }

    /**
     * Extended search that includes fuzzy matching for minor typos.
     * This allows for some spelling mistakes in the search query.
     */
    fun getExpandedSearchResults(allBankNames: List<String>, searchQuery: String): List<String> {
        // If search is empty, return all
        if (searchQuery.isBlank()) {
            return allBankNames
        }

        val lowercaseQuery = searchQuery.lowercase().trim()
        val results = mutableListOf<String>()

        // First add exact matches
        results.addAll(allBankNames.filter { it.lowercase().contains(lowercaseQuery) })

        // Then add alias matches that weren't already caught
        val aliasMatches = getMatchingBankNames(lowercaseQuery)
        if (aliasMatches != null) {
            val missingMatches = allBankNames.filter { bankName ->
                !results.contains(bankName) &&
                        aliasMatches.any { alias -> bankName.lowercase().contains(alias.lowercase()) }
            }
            results.addAll(missingMatches)
        }

        // Simple fuzzy matching for typos (if no results yet)
        if (results.isEmpty() && lowercaseQuery.length > 2) {
            // For queries with 3+ characters, allow matches with 1 character difference
            results.addAll(allBankNames.filter { bankName ->
                val lowercaseName = bankName.lowercase()

                // Check if query is within edit distance 1 of any word in the bank name
                lowercaseName.split(" ").any { word ->
                    levenshteinDistance(word.lowercase(), lowercaseQuery) <= 1
                }
            })
        }

        return results
    }

    /**
     * Calculates the Levenshtein distance between two strings.
     * This measures how many single-character edits are needed to change one string into the other.
     */
    fun levenshteinDistance(a: String, b: String): Int {
        val matrix = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) {
            matrix[i][0] = i
        }

        for (j in 0..b.length) {
            matrix[0][j] = j
        }

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,      // deletion
                    matrix[i][j - 1] + 1,      // insertion
                    matrix[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return matrix[a.length][b.length]
    }
}