import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.repositories.BankAccountRepository
import com.splitter.splittr.model.BankAccount
import com.splitter.splittr.utils.CoroutineDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BankAccountViewModel(
    private val bankAccountRepository: BankAccountRepository,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {
    private val _bankAccounts = MutableStateFlow<List<BankAccount>>(emptyList())
    val bankAccounts: StateFlow<List<BankAccount>> = _bankAccounts

    private val _loading = MutableStateFlow<Boolean>(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadBankAccounts(userId: Int) {
        viewModelScope.launch(dispatchers.io) {
            _loading.value = (true)
            try {
                bankAccountRepository.getUserAccounts(userId).collect { bankAccountEntities ->
                    val bankAccountModels = bankAccountEntities.map { it.toModel() }
                    _bankAccounts.value = (bankAccountModels)
                }
            } catch (e: Exception) {
                _error.value = ("Failed to load bank accounts: ${e.message}")
            } finally {
                _loading.value = (false)
            }
        }
    }

    suspend fun addBankAccount(account: BankAccount): Boolean {
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
}