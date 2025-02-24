package com.helgolabs.trego.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.repositories.TransactionRepository
import com.helgolabs.trego.data.model.Transaction
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val dispatchers: CoroutineDispatchers,
    private val context: Context
) : ViewModel() {
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions

    private val _transaction = MutableStateFlow<Transaction?>(null)
    val transaction: StateFlow<Transaction?> = _transaction

    private val _recentTransactions = MutableStateFlow<List<Transaction>>(emptyList())
    val recentTransactions: StateFlow<List<Transaction>> = _recentTransactions

    private val _nonRecentTransactions = MutableStateFlow<List<Transaction>>(emptyList())
    val nonRecentTransactions: StateFlow<List<Transaction>> = _nonRecentTransactions

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    data class TransactionResponse(
        val transactions: List<Transaction>,
        val accountsNeedingReauthentication: List<AccountReauthState>
    )

    data class AccountReauthState(
        val accountId: String,
        val institutionId: String?
    )

    fun loadTransactions(userId: Int) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            try {
                val transactions = fetchTransactions(userId)
                _transactions.value = transactions ?: emptyList()
            } catch (e: Exception) {
                _error.value = "Error loading transactions: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun refreshTransactions(userId: Int) {
        // Force a refresh by clearing the cache
        TransactionCache.clearCache()
        loadTransactions(userId)
    }

    fun loadTransaction(transactionId: String) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = true
            try {
                transactionRepository.getTransactionById(transactionId).collect { transactionEntity ->
                    _transaction.value = transactionEntity?.toModel()
                }
            } catch (e: Exception) {
                _error.value = "Failed to load transaction: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    suspend fun fetchTransactions(userId: Int): List<Transaction>? {
        return try {
            withContext(dispatchers.io) {
                val fetchedTransactions = transactionRepository.fetchTransactions(userId)
                Log.d("TransactionViewModel", "Fetched ${fetchedTransactions?.size} transactions")
                fetchedTransactions?.distinct()?.sortedByDescending { it.bookingDateTime }
            }
        } catch (e: Exception) {
            Log.e("TransactionViewModel", "Error fetching transactions", e)
            _error.value = e.message
            null
        }
    }

    fun fetchRecentTransactions(userId: Int) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = true
            try {
                val recentTransactions = transactionRepository.fetchRecentTransactions(userId)
                _recentTransactions.value = recentTransactions ?: emptyList()
                updateCombinedTransactions()
            } catch (e: Exception) {
                _error.value = "Failed to fetch recent transactions: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun fetchNonRecentTransactions(userId: Int) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = true
            try {
                val nonRecentTransactions = transactionRepository.fetchNonRecentTransactions(userId)
                _nonRecentTransactions.value = nonRecentTransactions ?: emptyList()
                updateCombinedTransactions()
            } catch (e: Exception) {
                _error.value = "Failed to fetch non-recent transactions: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    private fun updateCombinedTransactions() {
        _transactions.value = (_recentTransactions.value + _nonRecentTransactions.value)
            .distinctBy { it.transactionId }
            .sortedByDescending { it.bookingDateTime }
    }

    fun createTransaction(transaction: Transaction) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = true
            try {
                val result = transactionRepository.createTransaction(transaction)
                if (result.isSuccess) {
                    loadTransactions(transaction.userId ?: 0)
                } else {
                    _error.value = ("Failed to create transaction: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                _error.value = ("Error creating transaction: ${e.message}")
            } finally {
                _loading.value = (false)
            }
        }
    }

    suspend fun getAccountsNeedingReauth(userId: Int): List<AccountReauthState> {
        return try {
            withContext(dispatchers.io) {
                transactionRepository.getAccountsNeedingReauth(userId)
            }
        } catch (e: Exception) {
            Log.e("TransactionViewModel", "Error getting accounts needing reauth", e)
            _error.value = e.message
            emptyList()
        }
    }

    fun saveTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                // Get userId from preferences
                val userId = getUserIdFromPreferences(context)

                // Create a copy of transaction with userId
                val transactionWithUser = transaction.copy(
                    userId = userId,
                    createdAt = DateUtils.getCurrentTimestamp(),
                    updatedAt = DateUtils.getCurrentTimestamp()
                )

                transactionRepository.saveTransaction(transactionWithUser)
            } catch (e: Exception) {
                _error.value = "Failed to save transaction: ${e.message}"
            }
        }
    }

    fun syncTransactions() {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = (true)
            try {
                transactionRepository.sync()
                // After syncing, you might want to reload the transactions
                // Assuming you have a way to get the current user ID
                // loadTransactions(currentUserId)
            } catch (e: Exception) {
                _error.value = ("Error syncing transactions: ${e.message}")
            } finally {
                _loading.value = (false)
            }
        }
    }
}