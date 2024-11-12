package com.splitter.splittr.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.repositories.GroupRepository
import com.splitter.splittr.data.repositories.PaymentRepository
import com.splitter.splittr.data.repositories.PaymentSplitRepository
import com.splitter.splittr.data.model.GroupMember
import com.splitter.splittr.data.model.Payment
import com.splitter.splittr.data.model.PaymentSplit
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

class PaymentsViewModel(
    private val paymentRepository: PaymentRepository,
    private val paymentSplitRepository: PaymentSplitRepository,
    private val groupRepository: GroupRepository,
    context: Context
) : ViewModel() {

    private val _paymentScreenState = MutableStateFlow(PaymentScreenState())
    val paymentScreenState: StateFlow<PaymentScreenState> = _paymentScreenState.asStateFlow()

    // Add a new state for navigation
    sealed class NavigationState {
        object Idle : NavigationState()
        object NavigateBack : NavigationState()
    }

    private val _navigationState = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    private var userId: Int? = null

    init {
        viewModelScope.launch {
            userId = getUserIdFromPreferences(context)
        }
    }

    data class PaymentScreenState(
        val payment: Payment? = null,
        val editablePayment: Payment? = null,
        val splits: List<PaymentSplit> = emptyList(),
        val editableSplits: List<PaymentSplit> = emptyList(),
        val groupMembers: List<GroupMember> = emptyList(),
        val paymentOperationStatus: PaymentOperationStatus = PaymentOperationStatus.Idle,
        val paidByUser: Int? = null,
        val paidToUser: Int? = null,
        val institutionName: String? = null,
        val isTransaction: Boolean = false,
        val expandedPaidByUserList: Boolean = false,
        val expandedPaidToUserList: Boolean = false,
        val expandedPaymentTypeList: Boolean = false,
        val showDeleteDialog: Boolean = false
    )

    sealed class PaymentOperationStatus {
        object Idle : PaymentOperationStatus()
        object Loading : PaymentOperationStatus()
        object Success : PaymentOperationStatus()
        data class Error(val message: String) : PaymentOperationStatus()
    }

    sealed class PaymentAction {
        data class UpdateAmount(val amount: Double) : PaymentAction()
        data class UpdateDescription(val description: String) : PaymentAction()
        data class UpdateNotes(val notes: String) : PaymentAction()
        data class UpdatePaymentType(val paymentType: String) : PaymentAction()
        data class UpdatePaymentDate(val paymentDate: String) : PaymentAction()
        data class UpdateCurrency(val currency: String) : PaymentAction()
        data class UpdateSplitMode(val splitMode: String) : PaymentAction()
        data class UpdatePaidByUser(val userId: Int) : PaymentAction()
        data class UpdatePaidToUser(val userId: Int) : PaymentAction()
        data class UpdateSplit(val userId: Int, val amount: Double) : PaymentAction()
        object ToggleExpandedPaidByUserList : PaymentAction()
        object ToggleExpandedPaidToUserList : PaymentAction()
        object ToggleExpandedPaymentTypeList : PaymentAction()
        object ShowDeleteDialog : PaymentAction()
        object HideDeleteDialog : PaymentAction()
    }

    data class TransactionDetails(
        val transactionId: String?,
        val amount: Double?,
        val description: String?,
        val creditorName: String?,
        val currency: String?,
        val bookingDateTime: String?
    )

    fun initializePaymentScreen(paymentId: Int, groupId: Int, transactionDetails: TransactionDetails?) {
        val currentUserId = userId ?: return
        viewModelScope.launch {
            when {
                paymentId != 0 -> loadExistingPayment(paymentId)
                transactionDetails?.transactionId != null -> initializeFromTransaction(transactionDetails, groupId)
                else -> initializeNewPayment(groupId, currentUserId)
            }
            recalculateSplits(currentUserId)
        }
    }

    private suspend fun loadExistingPayment(paymentId: Int) {
        val payment = paymentRepository.getPaymentById(paymentId).firstOrNull()
        val splits = paymentSplitRepository.getPaymentSplitsByPayment(paymentId).firstOrNull() ?: emptyList()
        val groupMembers = payment?.let {
            groupRepository.getGroupMembers(it.groupId)
                .map { entities -> entities.map { it.toModel() } }
                .first()
        } ?: emptyList()

        _paymentScreenState.value = _paymentScreenState.value.copy(
            payment = payment,
            editablePayment = payment?.copy(),
            splits = splits,
            editableSplits = splits.map { it.copy() },
            groupMembers = groupMembers,
            isTransaction = payment?.transactionId != null
        )
    }

    private fun initializeFromTransaction(transactionDetails: TransactionDetails, groupId: Int) {
        val currentUserId = userId ?: return
        val newPayment = Payment(
            id = 0,
            groupId = groupId,
            paidByUserId = currentUserId,
            transactionId = transactionDetails.transactionId,
            amount = transactionDetails.amount ?: 0.0,
            description = transactionDetails.description,
            notes = "",
            paymentDate = transactionDetails.bookingDateTime ?: System.currentTimeMillis()
                .toString(),
            currency = transactionDetails.currency,
            splitMode = "equally",
            paymentType = "spent",
            institutionName = transactionDetails.creditorName,
            createdBy = currentUserId,
            updatedBy = currentUserId,
            createdAt = System.currentTimeMillis().toString(),
            updatedAt = System.currentTimeMillis().toString(),
            deletedAt = null
        )
        viewModelScope.launch {
            loadGroupMembers(groupId)
            _paymentScreenState.update { currentState ->
                currentState.copy(
                    editablePayment = newPayment,
                    payment = newPayment,
                    isTransaction = true
                )
            }
            recalculateSplits(currentUserId)
        }
    }

    //Sort default currency here
    private fun initializeNewPayment(groupId: Int, userId: Int) {
        val newPayment = Payment(
            id = 0,
            groupId = groupId,
            paidByUserId = _paymentScreenState.value.paidByUser ?: userId,
            transactionId = null,
            amount = 0.0,
            description = "",
            notes = "",
            paymentDate = System.currentTimeMillis().toString(),
            currency = "GBP",
            splitMode = "equally",
            paymentType = "spent",
            institutionName = null,
            createdBy = userId,
            updatedBy = userId,
            createdAt = System.currentTimeMillis().toString(),
            updatedAt = System.currentTimeMillis().toString(),
            deletedAt = null
        )
        viewModelScope.launch {
            loadGroupMembers(groupId)
            _paymentScreenState.update { currentState ->
                currentState.copy(
                    editablePayment = newPayment,
                    payment = newPayment
                )
            }
            recalculateSplits(userId)
        }
    }

    private suspend fun loadGroupMembers(groupId: Int) {
        try {
            val members = groupRepository.getGroupMembers(groupId)
                .map { entities -> entities.map { it.toModel() } }
                .first()

            _paymentScreenState.update { currentState ->
                currentState.copy(groupMembers = members)
            }
        } catch (e: Exception) {
            Log.e("PaymentsViewModel", "Error loading group members", e)
        }
    }

    fun processAction(action: PaymentAction) {
        val currentUserId = userId ?: return
        when (action) {
            is PaymentAction.UpdateAmount -> updateEditablePayment { it.copy(amount = action.amount) }
            is PaymentAction.UpdateDescription -> updateEditablePayment { it.copy(description = action.description) }
            is PaymentAction.UpdateNotes -> updateEditablePayment { it.copy(notes = action.notes) }
            is PaymentAction.UpdatePaymentType -> updateEditablePayment { it.copy(paymentType = action.paymentType) }
            is PaymentAction.UpdatePaymentDate -> updateEditablePayment { it.copy(paymentDate = action.paymentDate) }
            is PaymentAction.UpdateCurrency -> updateEditablePayment { it.copy(currency = action.currency) }
            is PaymentAction.UpdateSplitMode -> {
                updateEditablePayment { it.copy(splitMode = action.splitMode) }
                recalculateSplits(currentUserId)
            }
            is PaymentAction.UpdatePaidByUser -> updateEditablePayment { it.copy(paidByUserId = action.userId) }
            is PaymentAction.UpdatePaidToUser -> {
                _paymentScreenState.value = _paymentScreenState.value.copy(paidToUser = action.userId)
            }
            is PaymentAction.UpdateSplit -> updateSplit(action.userId, action.amount)
            is PaymentAction.ToggleExpandedPaidByUserList -> {
                _paymentScreenState.value = _paymentScreenState.value.copy(
                    expandedPaidByUserList = !_paymentScreenState.value.expandedPaidByUserList
                )
            }
            is PaymentAction.ToggleExpandedPaidToUserList -> {
                _paymentScreenState.value = _paymentScreenState.value.copy(
                    expandedPaidToUserList = !_paymentScreenState.value.expandedPaidToUserList
                )
            }
            is PaymentAction.ToggleExpandedPaymentTypeList -> {
                _paymentScreenState.value = _paymentScreenState.value.copy(
                    expandedPaymentTypeList = !_paymentScreenState.value.expandedPaymentTypeList
                )
            }
            is PaymentAction.ShowDeleteDialog -> {
                _paymentScreenState.value = _paymentScreenState.value.copy(showDeleteDialog = true)
            }
            is PaymentAction.HideDeleteDialog -> {
                _paymentScreenState.value = _paymentScreenState.value.copy(showDeleteDialog = false)
            }
        }
    }

    private fun updateEditablePayment(update: (Payment) -> Payment) {
        val currentUserId = userId ?: return
        _paymentScreenState.value = _paymentScreenState.value.copy(
            editablePayment = _paymentScreenState.value.editablePayment?.let(update)
        )
        if (_paymentScreenState.value.editablePayment?.splitMode == "equally") {
            recalculateSplits(currentUserId)
        }
    }

    private fun updateSplit(userId: Int, amount: Double) {
        val updatedSplits = _paymentScreenState.value.editableSplits.map { split ->
            if (split.userId == userId) split.copy(amount = amount) else split
        }
        _paymentScreenState.value = _paymentScreenState.value.copy(
            editableSplits = updatedSplits
        )
    }

    private fun recalculateSplits(userId: Int) {
        val editablePayment = _paymentScreenState.value.editablePayment ?: return
        val groupMembers = _paymentScreenState.value.groupMembers

        val newSplits = when (editablePayment.splitMode) {
            "equally" -> calculateEqualSplits(editablePayment.amount, groupMembers, userId)
            "unequally" -> _paymentScreenState.value.editableSplits // Keep existing unequal splits
            else -> emptyList()
        }

        _paymentScreenState.update { currentState ->
            currentState.copy(editableSplits = newSplits)
        }
    }

    private fun calculateEqualSplits(amount: Double, members: List<GroupMember>, userId: Int): List<PaymentSplit> {
        if (members.isEmpty()) return emptyList()

        val perPerson = if (amount != 0.0) amount / members.size else 0.0

        return members.map { member ->
            PaymentSplit(
                id = 0,
                paymentId = _paymentScreenState.value.editablePayment?.id ?: 0,
                userId = member.userId,
                amount = perPerson,
                currency = _paymentScreenState.value.editablePayment?.currency ?: "GBP",
                createdAt = System.currentTimeMillis().toString(),
                updatedAt = System.currentTimeMillis().toString(),
                createdBy = userId,
                updatedBy = userId,
                deletedAt = null
            )
        }
    }

    fun savePayment() {
        viewModelScope.launch {
            val editablePayment = _paymentScreenState.value.editablePayment ?: return@launch
            val editableSplits = _paymentScreenState.value.editableSplits

            _paymentScreenState.value = _paymentScreenState.value.copy(
                paymentOperationStatus = PaymentOperationStatus.Loading
            )

            // Format the payment date
            val formattedDate = formatPaymentDate(editablePayment.paymentDate)
            val paymentToSave = editablePayment.copy(paymentDate = formattedDate)

            val result = if (paymentToSave.id == 0) {
                paymentRepository.createPaymentWithSplits(paymentToSave, editableSplits)
            } else {
                paymentRepository.updatePaymentWithSplits(paymentToSave, editableSplits)
            }

            result.fold(
                onSuccess = { savedPayment ->
                    _paymentScreenState.value = _paymentScreenState.value.copy(
                        payment = savedPayment,
                        editablePayment = savedPayment,
                        splits = editableSplits,
                        editableSplits = editableSplits,
                        paymentOperationStatus = PaymentOperationStatus.Success
                    )
                    // Emit navigation state
                    _navigationState.value = NavigationState.NavigateBack
                },
                onFailure = { error ->
                    _paymentScreenState.value = _paymentScreenState.value.copy(
                        paymentOperationStatus = PaymentOperationStatus.Error(error.message ?: "Unknown error")
                    )
                }
            )
        }
    }

    // Add this function to reset navigation state
    fun resetNavigationState() {
        _navigationState.value = NavigationState.Idle
    }

    private fun formatPaymentDate(date: String): String {
        return try {
            // Assuming the input is a Unix timestamp in milliseconds
            val instant = Instant.ofEpochMilli(date.toLong())
            // Format to ISO 8601 string
            DateTimeFormatter.ISO_INSTANT.format(instant)
        } catch (e: Exception) {
            // If parsing fails, return the current time as ISO 8601 string
            Instant.now().toString()
        }
    }

    fun archivePayment() {
        viewModelScope.launch {
            val paymentId = _paymentScreenState.value.payment?.id ?: return@launch

            _paymentScreenState.value = _paymentScreenState.value.copy(
                paymentOperationStatus = PaymentOperationStatus.Loading
            )

            val result = paymentRepository.archivePayment(paymentId)

            result.fold(
                onSuccess = {
                    _paymentScreenState.value = _paymentScreenState.value.copy(
                        paymentOperationStatus = PaymentOperationStatus.Success,
                        showDeleteDialog = false
                    )
                },
                onFailure = { error ->
                    _paymentScreenState.value = _paymentScreenState.value.copy(
                        paymentOperationStatus = PaymentOperationStatus.Error(error.message ?: "Unknown error"),
                        showDeleteDialog = false
                    )
                }
            )
        }
    }

    fun resetEditableState(userId: Int) {
        _paymentScreenState.value = _paymentScreenState.value.copy(
            editablePayment = _paymentScreenState.value.payment?.copy(),
            editableSplits = _paymentScreenState.value.splits.map { it.copy() }
        )
        recalculateSplits(userId)
    }

    suspend fun restorePayment(paymentId: Int): Result<Unit> {
        return try {
            // Your implementation here
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncPayments(): Result<Unit> {
        return try {
            // Your implementation here
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}