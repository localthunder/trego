package com.splitter.splittr.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.repositories.TransactionRepository
import com.splitter.splittr.model.Transaction
import com.splitter.splittr.utils.CoroutineDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val dispatchers: CoroutineDispatchers
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

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadTransactions(userId: Int) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = true
            try {
                transactionRepository.getTransactionsByUserId(userId).collect { transactionEntities ->
                    val transactionModels = transactionEntities.map { it.toModel() }
                    _transactions.value = transactionModels
                }
            } catch (e: Exception) {
                _error.value = "Failed to load transactions: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
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

    fun saveTransaction(transaction: Transaction) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = (true)
            try {
                val savedTransaction = transactionRepository.saveTransaction(transaction)
                if (savedTransaction != null) {
                    _transaction.value = (savedTransaction)
                } else {
                    _error.value = ("Failed to save transaction")
                }
            } catch (e: Exception) {
                _error.value = ("Error saving transaction: ${e.message}")
            } finally {
                _loading.value = (false)
            }
        }
    }

    fun syncTransactions() {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = (true)
            try {
                transactionRepository.syncTransactions()
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