import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.entities.BankAccountEntity
import com.helgolabs.trego.data.repositories.BankAccountRepository
import com.helgolabs.trego.data.model.BankAccount
import com.helgolabs.trego.data.repositories.TransactionRepository
import com.helgolabs.trego.utils.CoroutineDispatchers
import com.helgolabs.trego.utils.getUserIdFromPreferences
import com.helgolabs.trego.workers.FetchTransactionsForAccountWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class BankAccountViewModel(
    private val bankAccountRepository: BankAccountRepository,
    private val transactionRepository: TransactionRepository,
    private val dispatchers: CoroutineDispatchers,
    val context: Context
) : ViewModel() {
    private val _bankAccounts = MutableStateFlow<List<BankAccount>>(emptyList())
    val bankAccounts: StateFlow<List<BankAccount>> = _bankAccounts

    private val _deleteStatus = MutableStateFlow<Result<Unit>?>(null)
    val deleteStatus: StateFlow<Result<Unit>?> = _deleteStatus

    private val _loading = MutableStateFlow<Boolean>(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val applicationBackgroundScope = (context.applicationContext as MyApplication).persistentBackgroundScope

    fun loadAccountsForRequisition(requisitionId: String, userId: Int) {
        Log.d("BankAccountViewModel", "Loading accounts for requisition: $requisitionId")
        viewModelScope.launch {
            _loading.value = true

            // Create data for the work request
            val data = Data.Builder()
                .putString("requisitionId", requisitionId)
                .putInt("userId", userId)
                .build()

            // Create work request
            val workRequest = OneTimeWorkRequestBuilder<FetchTransactionsForAccountWorker>()
                .setInputData(data)
                .build()

            // Enqueue work
            WorkManager.getInstance(context)
                .enqueueUniqueWork("fetch_accounts_$requisitionId", ExistingWorkPolicy.REPLACE, workRequest)

            // Set a timeout to update UI state after some time
            delay(1500) // Give UI time to render before changing states
            _loading.value = false
        }
    }

//    fun loadAccountsForRequisition(requisitionId: String, userId: Int) {
//        Log.d("BankAccountViewModel", "Loading accounts for requisition: $requisitionId")
//        // Use viewModelScope for UI-related operations
//        viewModelScope.launch {
//            _loading.value = true
//            _error.value = null
//
//            try {
//                // Start a background task that won't be cancelled by navigation
//                applicationBackgroundScope.launch {
//                    try {
//                        Log.d("BankAccountViewModel", "Background task: Fetching accounts for requisition: $requisitionId")
//                        val result = bankAccountRepository.getBankAccounts(requisitionId)
//
//                        result.onSuccess { accounts ->
//                            Log.d("BankAccountViewModel", "Background task: Successfully loaded ${accounts.size} accounts")
//
//                            // Update UI state from the background scope
//                            viewModelScope.launch {
//                                _bankAccounts.value = accounts
//                                _loading.value = false
//                            }
//
//                            // Continue with background processing
//                            if (accounts.isNotEmpty()) {
//                                try {
//                                    Log.d("BankAccountViewModel", "Background task: Loading all user accounts")
//                                    bankAccountRepository.getUserAccounts(userId)
//
//                                    Log.d("BankAccountViewModel", "Background task: Fetching transactions for account: ${accounts.first().accountId}")
//                                    transactionRepository.fetchAccountTransactions(accounts.first().accountId, userId)
//
//                                    Log.d("BankAccountViewModel", "Background tasks completed successfully")
//                                } catch (e: Exception) {
//                                    Log.e("BankAccountViewModel", "Error in background processing", e)
//                                }
//                            }
//                        }.onFailure { e ->
//                            Log.e("BankAccountViewModel", "Background task: Failed to load accounts", e)
//                            viewModelScope.launch {
//                                _error.value = "Failed to load accounts: ${e.message}"
//                                _loading.value = false
//                            }
//                        }
//                    } catch (e: Exception) {
//                        Log.e("BankAccountViewModel", "Background task error", e)
//                        viewModelScope.launch {
//                            _error.value = "Error: ${e.message}"
//                            _loading.value = false
//                        }
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e("BankAccountViewModel", "Error starting background task", e)
//                _error.value = "Error: ${e.message}"
//                _loading.value = false
//            }
//        }
//    }

    // Keep existing functions but add logging
    fun loadBankAccounts(userId: Int) {
        Log.d("BankAccountViewModel", "Loading all accounts for user: $userId")
        viewModelScope.launch {
            try {
                bankAccountRepository.getUserAccounts(userId)
                    .flowOn(dispatchers.io)
                    .catch { e ->
                        Log.e("BankAccountViewModel", "Error loading user accounts", e)
                        _error.value = "Failed to load bank accounts: ${e.message}"
                    }
                    .collect { accounts ->
                        Log.d("BankAccountViewModel", "Received ${accounts.size} user accounts")
                        _bankAccounts.value = accounts
                    }
            } finally {
                _loading.value = false
            }
        }
    }

    suspend fun addBankAccount(account: BankAccountEntity): Boolean {
        _loading.value = (true)
        return try {
            val result = bankAccountRepository.addAccount(account)
            if (result.isSuccess) {
                loadBankAccounts(account.userId) // Update the list after adding
                true
            } else {
                _error.value = ("Failed to add bank account: ${result.exceptionOrNull()?.message}")
                false
            }
        } catch (e: Exception) {
            _error.value = ("Error adding bank account: ${e.message}")
            false
        } finally {
            _loading.value = (false)
        }
    }

    fun updateAccountAfterReauth(accountId: String, newRequisitionId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val result = bankAccountRepository.updateAccountAfterReauth(accountId, newRequisitionId)
                result.onSuccess {
                    // Handle success - maybe refresh some UI state
                    _error.value = null
                }.onFailure { e ->
                    _error.value = "Failed to update account after reauth: ${e.message}"
                }
            } catch (e: Exception) {
                _error.value = "Error updating account: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun deleteBankAccount(accountId: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val result = bankAccountRepository.deleteBankAccount(accountId)
                _deleteStatus.value = result

                if (result.isSuccess) {
                    Log.e("BankAccountViewModel", "Account deleted successfully")

                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "Failed to delete account"
                }
            } catch (e: Exception) {
                Log.e("BankAccountViewModel", "Error deleting bank account", e)
                _error.value = e.message
                _deleteStatus.value = Result.failure(e)
            } finally {
                _loading.value = false
            }
        }
    }

    // Function to clear delete status after handling
    fun clearDeleteStatus() {
        _deleteStatus.value = null
    }

    override fun onCleared() {
        super.onCleared()
        applicationBackgroundScope.cancel()
    }
}