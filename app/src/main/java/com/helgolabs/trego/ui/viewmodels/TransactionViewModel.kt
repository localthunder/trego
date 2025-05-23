package com.helgolabs.trego.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dataClasses.AccountReauthState
import com.helgolabs.trego.data.local.dataClasses.RateLimitInfo
import com.helgolabs.trego.data.local.dataClasses.RefreshMessage
import com.helgolabs.trego.data.local.dataClasses.RefreshMessageType
import com.helgolabs.trego.data.repositories.TransactionRepository
import com.helgolabs.trego.data.model.Transaction
import com.helgolabs.trego.data.repositories.PaymentRepository
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val paymentRepository: PaymentRepository,
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

    // Track already added transaction IDs
    private val _addedTransactionIds = MutableStateFlow<Set<String>>(emptySet())
    val addedTransactionIds: StateFlow<Set<String>> = _addedTransactionIds

    // Track if we're loading the added transaction IDs
    private val _loadingAddedIds = MutableStateFlow(false)
    val loadingAddedIds: StateFlow<Boolean> = _loadingAddedIds

    // Filter state for showing/hiding already added transactions
    private val _showAlreadyAdded = MutableStateFlow(true)
    val showAlreadyAdded: StateFlow<Boolean> = _showAlreadyAdded

    // Filtered transactions based on visibility settings
    private val _filteredTransactions = MutableStateFlow<List<Transaction>>(emptyList())
    val filteredTransactions: StateFlow<List<Transaction>> = _filteredTransactions

    private val _rateLimitInfo = MutableStateFlow(RateLimitInfo(0))
    val rateLimitInfo: StateFlow<RateLimitInfo> = _rateLimitInfo

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _refreshMessage = MutableStateFlow<RefreshMessage?>(null)
    val refreshMessage: StateFlow<RefreshMessage?> = _refreshMessage

    // State to track if we need to show the cooldown override confirmation
    private val _showCooldownOverrideConfirmation = MutableStateFlow(false)
    val showCooldownOverrideConfirmation: StateFlow<Boolean> = _showCooldownOverrideConfirmation

    // Store cooldown minutes for the dialog
    private val _cooldownMinutesRemaining = MutableStateFlow(0L)
    val cooldownMinutesRemaining: StateFlow<Long> = _cooldownMinutesRemaining

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        // Initialize by getting the rate limit info from the repository
        viewModelScope.launch {
            _rateLimitInfo.value = transactionRepository.rateLimitInfo.value
        }
    }


    fun loadTransactions(userId: Int) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            try {
                val transactions = fetchTransactions(userId)
                _transactions.value = transactions ?: emptyList()

                // Update rate limit info after fetching
                _rateLimitInfo.value = transactionRepository.rateLimitInfo.value
            } catch (e: Exception) {
                _error.value = "Error loading transactions: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun manualRefreshTransactions(forceCooldownOverride: Boolean = false) {
        viewModelScope.launch {
            val userId = getUserIdFromPreferences(context) ?: return@launch

            _isRefreshing.value = true
            _refreshMessage.value = null

            try {
                // Call the manual refresh in the repository
                val result = transactionRepository.manualRefreshTransactions(userId, forceCooldownOverride)

                // Update rate limit info
                _rateLimitInfo.value = transactionRepository.rateLimitInfo.value

                // Process the result
                when (result) {
                    is TransactionRepository.ManualRefreshResult.Success -> {
                        val count = result.count
                        _refreshMessage.value = RefreshMessage(
                            type = RefreshMessageType.SUCCESS,
                            message = "Successfully refreshed $count transactions"
                        )

                        // Also update the transactions list
                        val freshTransactions = fetchTransactions(userId)
                        _transactions.value = freshTransactions ?: emptyList()

                        // Reset confirmation state
                        _showCooldownOverrideConfirmation.value = false
                    }
                    is TransactionRepository.ManualRefreshResult.RateLimited -> {
                        val resetTime = result.timeUntilReset?.let {
                            val resetDateTime = LocalDateTime.now().plus(it)
                            resetDateTime.format(DateTimeFormatter.ofPattern("h:mm a"))
                        } ?: "soon"

                        _refreshMessage.value = RefreshMessage(
                            type = RefreshMessageType.WARNING,
                            message = "You've reached your refresh limit for today. Resets at $resetTime.",
                            duration = result.timeUntilReset
                        )
                    }
                    is TransactionRepository.ManualRefreshResult.InCooldown -> {
                        // Instead of showing a message, trigger the cooldown override confirmation
                        _cooldownMinutesRemaining.value = result.cooldownMinutesRemaining
                        _showCooldownOverrideConfirmation.value = true

                        // Still show a message in the UI
                        _refreshMessage.value = RefreshMessage(
                            type = RefreshMessageType.INFO,
                            message = "It's only been ${result.cooldownMinutesRemaining} minute(s) since your last refresh."
                        )
                    }
                    is TransactionRepository.ManualRefreshResult.Error -> {
                        _refreshMessage.value = RefreshMessage(
                            type = RefreshMessageType.ERROR,
                            message = "Error refreshing: ${result.message}"
                        )
                    }
                    is TransactionRepository.ManualRefreshResult.AlreadyRefreshing -> {
                        _refreshMessage.value = RefreshMessage(
                            type = RefreshMessageType.INFO,
                            message = "Already refreshing transactions."
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("TransactionViewModel", "Error in manual refresh", e)
                _refreshMessage.value = RefreshMessage(
                    type = RefreshMessageType.ERROR,
                    message = "Error refreshing: ${e.message}"
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun confirmCooldownOverride() {
        _showCooldownOverrideConfirmation.value = false
        manualRefreshTransactions(forceCooldownOverride = true)
    }

    fun cancelCooldownOverride() {
        _showCooldownOverrideConfirmation.value = false
    }

    fun refreshAccountTransactions(accountId: String) {
        viewModelScope.launch {
            val userId = getUserIdFromPreferences(context) ?: return@launch

            _isRefreshing.value = true

            try {
                val transactions = transactionRepository.fetchAccountTransactions(accountId, userId)

                if (transactions != null && transactions.isNotEmpty()) {
                    // Merge with existing transactions
                    val currentTransactions = _transactions.value
                    val transactionMap = currentTransactions.associateBy { it.transactionId }.toMutableMap()

                    // Add or update transactions from the account
                    transactions.forEach { transaction ->
                        transactionMap[transaction.transactionId] = transaction
                    }

                    // Update the transactions list
                    _transactions.value = transactionMap.values.toList()
                        .sortedByDescending { it.bookingDateTime }

                    _refreshMessage.value = RefreshMessage(
                        type = RefreshMessageType.SUCCESS,
                        message = "Updated transactions for this account"
                    )
                } else {
                    _refreshMessage.value = RefreshMessage(
                        type = RefreshMessageType.INFO,
                        message = "No new transactions found for this account"
                    )
                }
            } catch (e: Exception) {
                Log.e("TransactionViewModel", "Error refreshing account transactions", e)
                _refreshMessage.value = RefreshMessage(
                    type = RefreshMessageType.ERROR,
                    message = "Error refreshing account: ${e.message}"
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Clear any displayed refresh messages
     */
    fun clearRefreshMessage() {
        _refreshMessage.value = null
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

    /**
     * Load transaction IDs that have already been added to a specific group
     */
    fun loadAddedTransactionIds(groupId: Int) {
        viewModelScope.launch(dispatchers.io) {
            _loadingAddedIds.value = true

            try {
                // Use a proper PaymentRepository function to fetch transaction IDs
                // that have already been added to this group
                val addedIds = paymentRepository.getAddedTransactionIds(groupId).first()

                _addedTransactionIds.value = addedIds.toSet()
                updateFilteredTransactions()
            } catch (e: Exception) {
                Log.e("TransactionViewModel", "Error loading added transaction IDs", e)
                _error.value = "Failed to check for already added transactions: ${e.message}"
            } finally {
                _loadingAddedIds.value = false
            }
        }
    }

    /**
     * Check if a specific transaction has already been added to the current group
     */
    fun isTransactionAdded(transactionId: String): Boolean {
        return transactionId in _addedTransactionIds.value
    }

    /**
     * Toggle visibility of already added transactions
     */
    fun toggleShowAlreadyAdded() {
        _showAlreadyAdded.value = !_showAlreadyAdded.value
        updateFilteredTransactions()
    }

    /**
     * Update filtered transactions list based on filter settings
     */
    private fun updateFilteredTransactions() {
        val allTransactions = _transactions.value
        val added = _addedTransactionIds.value

        _filteredTransactions.value = if (_showAlreadyAdded.value) {
            allTransactions
        } else {
            allTransactions.filter { it.transactionId !in added }
        }
    }

    /**
     * Update filtered transactions whenever main transaction list changes
     */
    init {
        viewModelScope.launch {
            _transactions.collect {
                updateFilteredTransactions()
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