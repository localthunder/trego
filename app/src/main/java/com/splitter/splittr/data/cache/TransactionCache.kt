package com.splitter.splittr.data.cache

import com.splitter.splittr.model.Transaction

object TransactionCache {
    private var recentTransactions: List<Transaction>? = null
    private var nonRecentTransactions: List<Transaction>? = null

    fun getRecentTransactions(): List<Transaction>? {
        return recentTransactions
    }

    fun getNonRecentTransactions(): List<Transaction>? {
        return nonRecentTransactions
    }

    fun saveRecentTransactions(transactions: List<Transaction>) {
        recentTransactions = transactions
    }

    fun saveNonRecentTransactions(transactions: List<Transaction>) {
        nonRecentTransactions = transactions
    }
}