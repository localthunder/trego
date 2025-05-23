package com.helgolabs.trego.ui.viewmodels

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helgolabs.trego.data.calculators.DefaultSplitCalculator
import com.helgolabs.trego.data.calculators.SplitCalculator
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dataClasses.PaymentEntityWithSplits
import com.helgolabs.trego.data.local.dataClasses.SettlingInstruction
import com.helgolabs.trego.data.local.dataClasses.UserInvolvement
import com.helgolabs.trego.data.local.entities.GroupEntity
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.data.local.entities.PaymentEntity
import com.helgolabs.trego.data.local.entities.PaymentSplitEntity
import com.helgolabs.trego.data.local.entities.UserEntity
import com.helgolabs.trego.data.repositories.GroupRepository
import com.helgolabs.trego.data.repositories.PaymentRepository
import com.helgolabs.trego.data.repositories.PaymentSplitRepository
import com.helgolabs.trego.data.model.GroupMember
import com.helgolabs.trego.data.model.Payment
import com.helgolabs.trego.data.model.PaymentSplit
import com.helgolabs.trego.data.model.Transaction
import com.helgolabs.trego.data.model.User
import com.helgolabs.trego.data.network.ExchangeRateService
import com.helgolabs.trego.data.repositories.InstitutionRepository
import com.helgolabs.trego.data.repositories.TransactionRepository
import com.helgolabs.trego.data.repositories.UserRepository
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.InstitutionLogoManager
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PaymentsViewModel(
    private val paymentRepository: PaymentRepository,
    private val paymentSplitRepository: PaymentSplitRepository,
    private val groupRepository: GroupRepository,
    private val transactionRepository: TransactionRepository,
    private val institutionRepository: InstitutionRepository,
    private val userRepository: UserRepository,
    private val splitCalculator: SplitCalculator,
    context: Context
) : ViewModel() {

    private val _paymentScreenState = MutableStateFlow(PaymentScreenState())
    val paymentScreenState: StateFlow<PaymentScreenState> = _paymentScreenState.asStateFlow()

    private val _paymentItemInfo = MutableStateFlow<Map<Int, PaymentItemInfo>>(emptyMap())
    val paymentItemInfo: StateFlow<Map<Int, PaymentItemInfo>> = _paymentItemInfo.asStateFlow()

    private val _navigationState = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    private val _users = MutableStateFlow<List<UserEntity>>(emptyList())
    val users: StateFlow<List<UserEntity>> = _users.asStateFlow()

    private val _groupPaymentsAndSplits = MutableStateFlow<List<PaymentEntityWithSplits>>(emptyList())
    val groupPaymentsAndSplits = _groupPaymentsAndSplits.asStateFlow()

    private val _exchangeRate = MutableStateFlow<Double?>(null)
    val exchangeRate: StateFlow<Double?> = _exchangeRate.asStateFlow()

    private val _exchangeRateDate = MutableStateFlow<String?>(null)
    val exchangeRateDate: StateFlow<String?> = _exchangeRateDate.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private var userId: Int? = null

    init {
        viewModelScope.launch {
            userId = getUserIdFromPreferences(context)
            loadUsers()
        }
    }

    private suspend fun loadUsers() {
        _paymentScreenState.value.groupMembers.map { it.userId }.let { userIds ->
            try {
                val loadedUsers = userRepository.getUsersByIds(userIds).first() // Add .first() here
                _users.value = loadedUsers
                Log.d("PaymentsVM", "Loaded users: $loadedUsers")
            } catch (e: Exception) {
                Log.e("PaymentsVM", "Failed to load users", e)
            }
        }
    }

    sealed class NavigationState {
        object Idle : NavigationState()
        object NavigateBack : NavigationState()
    }

    data class PaymentScreenState(
        val payment: PaymentEntity? = null,
        val editablePayment: PaymentEntity? = null,
        val splits: List<PaymentSplitEntity> = emptyList(),
        val editableSplits: List<PaymentSplitEntity> = emptyList(),
        val groupMembers: List<GroupMemberEntity> = emptyList(),
        val paymentOperationStatus: PaymentOperationStatus = PaymentOperationStatus.Idle,
        val paidByUser: Int? = null,
        val paidToUser: Int? = null,
        val institutionName: String? = null,
        val isTransaction: Boolean = false,
        val hasBeenConverted: Boolean = false, //meaning it has an entry in the currency conversion table
        val expandedPaidByUserList: Boolean = false,
        val expandedPaidToUserList: Boolean = false,
        val expandedPaymentTypeList: Boolean = false,
        val expandedSplitTypeList: Boolean = false,
        val showDeleteDialog: Boolean = false,
        val selectedMembers: Set<GroupMemberEntity> = emptySet(),
        val group: GroupEntity? = null,
        val originalCurrency: String? = null,
        val conversionId: Int? = null,
        val isConverting: Boolean = false,
        val conversionError: String? = null,
        val editOrderMap: Map<Int, Long> = emptyMap() // Maps userId to timestamp of last edit


    ) {
        val shouldShowSplitUI: Boolean
            get() = editablePayment?.paymentType != "transferred"

        val shouldLockUI: Boolean  // Add this computed property
            get() = isTransaction || hasBeenConverted

        val effectiveSplits: List<PaymentSplitEntity>
            get() = when {
                editablePayment?.paymentType == "transferred" && paidToUser != null -> {
                    // For transfers, always use a single split for the paidToUser
                    listOf(
                        PaymentSplitEntity(
                            id = editableSplits.firstOrNull()?.id ?: 0,
                            paymentId = editablePayment.id,
                            userId = paidToUser,
                            amount = editablePayment.amount,
                            currency = editablePayment.currency ?: "GBP",
                            createdAt = editableSplits.firstOrNull()?.createdAt ?: DateUtils.getCurrentTimestamp(),
                            updatedAt = DateUtils.getCurrentTimestamp(),
                            createdBy = editableSplits.firstOrNull()?.createdBy ?: 0,
                            updatedBy = editableSplits.firstOrNull()?.updatedBy ?: 0,
                            deletedAt = null
                        )
                    )
                }
                else -> editableSplits
            }
    }

    sealed class PaymentImage {
        data class Logo(val logoInfo: InstitutionLogoManager.LogoInfo) : PaymentImage()
        data class Placeholder(val type: String) : PaymentImage() // You can define placeholder types later
        object None : PaymentImage()
    }

    data class PaymentItemInfo(
        val paymentImage: PaymentImage = PaymentImage.None,
        val paidByUsername: String? = null,
        val transaction: Transaction? = null,
        val isLoading: Boolean = false,
        val error: String? = null
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
        data class UpdateSplitPercentage(val userId: Int, val percentage: Double, val isAutoCalculated: Boolean = false) : PaymentAction()
        data class UpdatePaidByUser(val userId: Int) : PaymentAction()
        data class UpdatePaidToUser(val userId: Int) : PaymentAction()
        data class UpdateSplit(val userId: Int, val amount: Double, val isAutoCalculated: Boolean = false) : PaymentAction()
        data class UpdateSelectedMembers(val members: Set<GroupMemberEntity>) : PaymentAction()
        data class TrackSplitEdit(val userId: Int) : PaymentAction()

        object ToggleExpandedPaidByUserList : PaymentAction()
        object ToggleExpandedPaidToUserList : PaymentAction()
        object ToggleExpandedPaymentTypeList : PaymentAction()
        object ToggleExpandedSplitTypeList : PaymentAction()
        object ShowDeleteDialog : PaymentAction()
        object HideDeleteDialog : PaymentAction()
    }

    fun processAction(action: PaymentAction) {
        val currentUserId = userId ?: return
        when (action) {
            is PaymentAction.UpdateAmount -> {
                updateEditablePayment { it.copy(amount = action.amount) }
                    recalculateSplits(currentUserId)
            }
            is PaymentAction.UpdateDescription -> updateEditablePayment { it.copy(description = action.description) }
            is PaymentAction.UpdateNotes -> updateEditablePayment { it.copy(notes = action.notes) }
            is PaymentAction.UpdatePaymentType -> {
                Log.d("PaymentsVM", "Updating payment type to: ${action.paymentType}")

                // Check if switching from transferred to another payment type
                val isChangingFromTransfer = _paymentScreenState.value.editablePayment?.paymentType == "transferred" &&
                        action.paymentType != "transferred"

                // Update payment type first
                updateEditablePayment { it.copy(paymentType = action.paymentType) }

                if (action.paymentType == "transferred") {
                    // Transfer payment handling code (your existing code)
                    val currentPaidToUser = _paymentScreenState.value.paidToUser
                    val currentPaidBy = _paymentScreenState.value.editablePayment?.paidByUserId

                    val newPaidToUser = if (currentPaidToUser == null || currentPaidToUser == currentPaidBy) {
                        _paymentScreenState.value.groupMembers
                            .firstOrNull { it.userId != currentPaidBy }?.userId
                    } else currentPaidToUser

                    if (newPaidToUser != null) {
                        // Update paidToUser and other settings first
                        _paymentScreenState.update { currentState ->
                            currentState.copy(
                                paidToUser = newPaidToUser,
                                selectedMembers = currentState.groupMembers
                                    .filter { it.userId == newPaidToUser }
                                    .toSet(),
                                editableSplits = emptyList()
                                // Note: We don't update description here - that will happen in loadUsersAndUpdateDescription
                            )
                        }

                        // Then load users and update the description
                        loadUsersAndUpdateDescription(currentPaidBy, newPaidToUser)
                    }
                } else if (isChangingFromTransfer) {
                    // When changing from transfer to another type, reset the selected members
                    // and prepare to recalculate splits with group defaults
                    _paymentScreenState.update { currentState ->
                        currentState.copy(
                            // Select all group members when switching away from transfer
                            selectedMembers = currentState.groupMembers.toSet(),
                            // Clear the paidToUser to prevent confusion
                            paidToUser = null,
                            // Clear existing splits to force recalculation
                            editableSplits = emptyList(),
                            // Reset edit history to ensure fresh calculation
                            editOrderMap = emptyMap()
                        )
                    }

                    // Get the group to check default split mode
                    val groupId = _paymentScreenState.value.group?.id
                    val defaultSplitMode = _paymentScreenState.value.group?.defaultSplitMode

                    if (groupId != null && defaultSplitMode == "percentage") {
                        // If the group uses percentage splitting by default, load those
                        viewModelScope.launch {
                            loadGroupDefaultSplits(groupId)
                        }
                    }
                }

                recalculateSplits(currentUserId)
            }
            is PaymentAction.UpdatePaymentDate -> updateEditablePayment { it.copy(paymentDate = action.paymentDate) }
            is PaymentAction.UpdateCurrency -> updateEditablePayment { it.copy(currency = action.currency) }
            is PaymentAction.UpdateSplitMode -> {
                updateEditablePayment { it.copy(splitMode = action.splitMode) }
                recalculateSplits(currentUserId)
            }
            is PaymentAction.UpdatePaidByUser -> {
                updateEditablePayment { it.copy(paidByUserId = action.userId) }

                // Update description for transfer payments
                if (_paymentScreenState.value.editablePayment?.paymentType == "transferred") {
                    loadUsersAndUpdateDescription(action.userId, _paymentScreenState.value.paidToUser)
                }
            }
            is PaymentAction.UpdatePaidToUser -> {
                _paymentScreenState.value = _paymentScreenState.value.copy(paidToUser = action.userId)

                // Update description for transfer payments
                if (_paymentScreenState.value.editablePayment?.paymentType == "transferred") {
                    loadUsersAndUpdateDescription(_paymentScreenState.value.editablePayment?.paidByUserId, action.userId)
                }
            }
            is PaymentAction.UpdateSplit -> {
                updateSplit(action.userId, action.amount)
                // Also track this split as being edited
                processAction(PaymentAction.TrackSplitEdit(action.userId))
            }
            is PaymentAction.UpdateSelectedMembers -> {
                _paymentScreenState.update { currentState ->
                    currentState.copy(selectedMembers = action.members)
                }
                // Recalculate splits based on new selection
                recalculateSplits(currentUserId)
            }
            is PaymentAction.UpdateSplitPercentage -> {
                updateSplitPercentage(action.userId, action.percentage)
                // Also track this split as being edited
                processAction(PaymentAction.TrackSplitEdit(action.userId))
            }
            is PaymentAction.TrackSplitEdit -> {
                _paymentScreenState.update { currentState ->
                    val currentTime = System.currentTimeMillis()
                    val updatedMap = currentState.editOrderMap + (action.userId to currentTime)
                    currentState.copy(editOrderMap = updatedMap)
                }
            }
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
            is PaymentAction.ToggleExpandedSplitTypeList -> {
                _paymentScreenState.value = _paymentScreenState.value.copy(
                    expandedSplitTypeList = !_paymentScreenState.value.expandedSplitTypeList
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

    private fun updateTransferDescription(paidByUserId: Int?, paidToUserId: Int?) {
        if (paidByUserId == null || paidToUserId == null) {
            Log.d("PaymentsVM", "Cannot update transfer description - missing user IDs")
            return
        }

        // Get current state
        val currentState = _paymentScreenState.value
        val currentUsers = users.value

        // Debug current state
        Log.d("PaymentsVM", "Updating transfer description with paidBy: $paidByUserId, paidTo: $paidToUserId")
        Log.d("PaymentsVM", "Available users: ${currentUsers.map { "${it.userId}:${it.username}" }}")
        Log.d("PaymentsVM", "Group members: ${currentState.groupMembers.map { it.userId }}")

        // We need to first find the corresponding group members
        val paidByMember = currentState.groupMembers.find { it.userId == paidByUserId }
        val paidToMember = currentState.groupMembers.find { it.userId == paidToUserId }

        if (paidByMember == null || paidToMember == null) {
            Log.d("PaymentsVM", "Cannot find group members for users")
            return
        }

        // Then find the users
        val paidByUser = currentUsers.find { it.userId == paidByUserId }
        val paidToUser = currentUsers.find { it.userId == paidToUserId }

        // Get usernames for description
        val paidByUsername = paidByUser?.username ?: "Unknown user $paidByUserId"
        val paidToUsername = paidToUser?.username ?: "Unknown user $paidToUserId"

        Log.d("PaymentsVM", "Found paidBy username: $paidByUsername")
        Log.d("PaymentsVM", "Found paidTo username: $paidToUsername")

        // Format description with the new format
        val transferDescription = "Transfer: From $paidByUsername to $paidToUsername"

        // Update the payment description
        updateEditablePayment { it.copy(description = transferDescription) }

        Log.d("PaymentsVM", "Updated description to: $transferDescription")
    }

    // Add this function to ensure users are loaded before updating descriptions
    private fun loadUsersAndUpdateDescription(paidByUserId: Int?, paidToUserId: Int?) {
        if (users.value.isEmpty()) {
            // If users not loaded yet, load them and then update
            viewModelScope.launch {
                val userIds = listOfNotNull(paidByUserId, paidToUserId)
                userRepository.getUsersByIds(userIds).collect { loadedUsers ->
                    if (loadedUsers.isNotEmpty()) {
                        _users.value = loadedUsers
                        updateTransferDescription(paidByUserId, paidToUserId)
                    }
                }
            }
        } else {
            // Users already loaded, just update
            updateTransferDescription(paidByUserId, paidToUserId)
        }
    }

    private fun updateSplitPercentage(userId: Int, percentage: Double) {
        val updatedSplits = _paymentScreenState.value.editableSplits.map { split ->
            if (split.userId == userId) split.copy(percentage = percentage) else split
        }
        _paymentScreenState.value = _paymentScreenState.value.copy(
            editableSplits = updatedSplits
        )
        // Recalculate monetary amounts based on new percentages
        recalculateSplits(userId ?: 0)
    }

    data class TransactionDetails(
        val transactionId: String?,
        val amount: Double?,
        val description: String?,
        val creditorName: String?,
        val currency: String?,
        val bookingDateTime: String?,
        val institutionId: String?
    )

    fun getGroup(): GroupEntity? = _paymentScreenState.value.group

    fun initializePaymentScreen(paymentId: Int, groupId: Int, transactionDetails: TransactionDetails?) {
        val currentUserId = userId ?: return
        viewModelScope.launch {
            try {
                // Load the group first
                val group = groupRepository.getGroupById(groupId).first()
                _paymentScreenState.update { it.copy(group = group) }

                when {
                    paymentId != 0 -> {
                        // Load existing payment
                        loadExistingPayment(paymentId)
                        _paymentScreenState.update { currentState ->
                            val membersWithSplits = currentState.groupMembers.filter { member ->
                                currentState.editableSplits.any { split -> split.userId == member.userId }
                            }
                            currentState.copy(selectedMembers = membersWithSplits.toSet())
                        }
                        loadUsers()
                    }

                    transactionDetails?.transactionId != null -> {
                        initializeFromTransaction(transactionDetails, groupId)
                        recalculateSplits(currentUserId)  // Only for new payments
                    }

                    else -> {
                        initializeNewPayment(groupId, currentUserId)

                        // If group default split mode is percentage, load default percentages
                        if (group?.defaultSplitMode == "percentage") {
                            loadGroupDefaultSplits(groupId)
                        } else {
                            recalculateSplits(currentUserId)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing payment screen", e)
            }
        }
    }

    suspend fun loadExistingPayment(paymentId: Int) {
        try {
            Log.d("PaymentsViewModel", "Loading existing payment: $paymentId")

            // Get payment entity and wait for actual data
            val paymentEntity = paymentRepository.getPaymentById(paymentId).first()
                ?: throw Exception("Payment not found")
            Log.d("PaymentsViewModel", "Found payment: $paymentEntity")

            // Use local ID for splits query
            val splits = paymentSplitRepository.getPaymentSplitsByPayment(paymentEntity.id).first()
            Log.d("PaymentsViewModel", "Found splits: ${splits.size} - $splits")

            // Get group members as entities
            val groupMembers = groupRepository.getGroupMembers(paymentEntity.groupId).first()
            Log.d("PaymentsViewModel", "Found group members: ${groupMembers.size} - $groupMembers")

            // Find members who have splits
            val selectedMembers = groupMembers.filter { member ->
                splits.any { split -> split.userId == member.userId }
            }.toSet()
            Log.d("PaymentsViewModel", "Selected members based on splits: ${selectedMembers.size} - $selectedMembers")

            // Check if payment has any currency conversions
            val hasConversions = paymentRepository.getConversionCountForPayment(paymentId) > 0

            val latestConversion = paymentRepository.getLatestConversionForPayment(paymentEntity.id)


            _paymentScreenState.update { currentState ->
                currentState.copy(
                    payment = paymentEntity,
                    editablePayment = paymentEntity.copy(),
                    splits = splits,
                    editableSplits = splits.map { it.copy() },
                    groupMembers = groupMembers,
                    selectedMembers = if (splits.isEmpty()) groupMembers.toSet() else selectedMembers,
                    isTransaction = paymentEntity.transactionId != null,
                    hasBeenConverted = hasConversions,
                    originalCurrency = latestConversion?.originalCurrency,
                    conversionId = latestConversion?.id
                ).also {
                    Log.d("PaymentsViewModel", "Updated state - editableSplits: ${it.editableSplits}")
                    Log.d("PaymentsViewModel", "Updated state - selectedMembers: ${it.selectedMembers}")
                }
            }
        } catch (e: Exception) {
            Log.e("PaymentsViewModel", "Error loading payment", e)
            _paymentScreenState.update { currentState ->
                currentState.copy(
                    paymentOperationStatus = PaymentOperationStatus.Error(
                        e.message ?: "Error loading payment"
                    )
                )
            }
        }
    }

    private fun initializeFromTransaction(transactionDetails: TransactionDetails, groupId: Int) {
        val currentUserId = userId ?: return
        val paymentType = if ((transactionDetails.amount ?: 0.0) > 0) "received" else "spent"

        // Get the group's default split mode
        val group = _paymentScreenState.value.group
        val defaultSplitMode = group?.defaultSplitMode ?: "equally"

        Log.d(TAG, "group = $group")
        Log.d(TAG, "default split mode = $defaultSplitMode")

        val newPayment = PaymentEntity(
            id = 0,
            groupId = groupId,
            paidByUserId = currentUserId,
            transactionId = transactionDetails.transactionId,
            amount = transactionDetails.amount ?: 0.0,
            description = transactionDetails.description,
            notes = "",
            paymentDate = transactionDetails.bookingDateTime ?: DateUtils.getCurrentTimestamp(),
            currency = transactionDetails.currency,
            splitMode = defaultSplitMode,
            paymentType = paymentType,
            institutionId = transactionDetails.institutionId,
            createdBy = currentUserId,
            updatedBy = currentUserId,
            createdAt = DateUtils.getCurrentTimestamp(),
            updatedAt = DateUtils.getCurrentTimestamp(),
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

        // Get the group's default split mode
        val group = _paymentScreenState.value.group
        val defaultSplitMode = group?.defaultSplitMode ?: "equally"

        Log.d(TAG, "group = $group")
        Log.d(TAG, "default split mode = $defaultSplitMode")


        val newPayment = PaymentEntity(
            id = 0,
            groupId = groupId,
            paidByUserId = _paymentScreenState.value.paidByUser ?: userId,
            transactionId = null,
            amount = 0.0,
            description = "",
            notes = "",
            paymentDate = DateUtils.getCurrentTimestamp(),
            currency = "GBP",
            splitMode = defaultSplitMode,
            paymentType = "spent",
            institutionId = null,
            createdBy = userId,
            updatedBy = userId,
            createdAt = DateUtils.getCurrentTimestamp(),
            updatedAt = DateUtils.getCurrentTimestamp(),
            deletedAt = null
        )
        viewModelScope.launch {
            loadGroupMembers(groupId)
            _paymentScreenState.update { currentState ->
                currentState.copy(
                    editablePayment = newPayment,
                    payment = newPayment,
                    selectedMembers = currentState.groupMembers.toSet()
                )
            }
            recalculateSplits(userId)
        }
    }

    private suspend fun loadGroupMembers(groupId: Int) {
        try {
            val members = groupRepository.getGroupMembers(groupId)
                .first()

            _paymentScreenState.update { currentState ->
                currentState.copy(
                    groupMembers = members,
                    selectedMembers = members.toSet()  // Select all members by default

                )
            }
        } catch (e: Exception) {
            Log.e("PaymentsViewModel", "Error loading group members", e)
        }
    }

    // In PaymentsViewModel.kt
    private fun loadGroupDefaultSplits(groupId: Int) {
        viewModelScope.launch {
            try {
                // Get default percentages from the group
                val defaultSplitEntities = groupRepository.getGroupDefaultSplits(groupId).first()

                if (defaultSplitEntities.isNotEmpty()) {
                    Log.d(TAG, "Loaded ${defaultSplitEntities.size} default splits from group")

                    // Get current group members
                    val currentMembers = _paymentScreenState.value.groupMembers

                    // Create NEW payment splits with percentages from the default splits
                    val splitEntities = currentMembers.map { member ->
                        // Find the default percentage for this user, or use equal split
                        val defaultSplit = defaultSplitEntities.find { it.userId == member.userId }
                        val percentage = defaultSplit?.percentage ?: (100.0 / currentMembers.size)

                        // Create a payment split with this percentage
                        PaymentSplitEntity(
                            id = 0,
                            paymentId = _paymentScreenState.value.editablePayment?.id ?: 0,
                            userId = member.userId,
                            amount = 0.0, // Will be calculated later based on percentage
                            currency = _paymentScreenState.value.editablePayment?.currency ?: "GBP",
                            percentage = percentage, // Set the percentage from defaults
                            createdAt = DateUtils.getCurrentTimestamp(),
                            updatedAt = DateUtils.getCurrentTimestamp(),
                            createdBy = userId ?: 0,
                            updatedBy = userId ?: 0,
                            deletedAt = null
                        )
                    }

                    // Update the state with these new splits
                    _paymentScreenState.update { currentState ->
                        currentState.copy(
                            editableSplits = splitEntities,
                            selectedMembers = currentMembers.toSet()
                        )
                    }

                    // Calculate the actual monetary amounts
                    calculateSplitAmountsFromPercentages()
                } else {
                    Log.d(TAG, "No default splits found for group, using equal splits")
                    // Fall back to equal percentages
                    val equalPercentage = 100.0 / _paymentScreenState.value.groupMembers.size

                    val equalSplits = _paymentScreenState.value.groupMembers.map { member ->
                        PaymentSplitEntity(
                            id = 0,
                            paymentId = _paymentScreenState.value.editablePayment?.id ?: 0,
                            userId = member.userId,
                            amount = 0.0,
                            currency = _paymentScreenState.value.editablePayment?.currency ?: "GBP",
                            percentage = equalPercentage,
                            createdAt = DateUtils.getCurrentTimestamp(),
                            updatedAt = DateUtils.getCurrentTimestamp(),
                            createdBy = userId ?: 0,
                            updatedBy = userId ?: 0,
                            deletedAt = null
                        )
                    }

                    _paymentScreenState.update { currentState ->
                        currentState.copy(
                            editableSplits = equalSplits,
                            selectedMembers = currentState.groupMembers.toSet()
                        )
                    }

                    calculateSplitAmountsFromPercentages()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading group default splits", e)
                recalculateSplits(userId ?: 0)
            }
        }
    }

    // Make sure this function is implemented correctly
    private fun calculateSplitAmountsFromPercentages() {
        val editablePayment = _paymentScreenState.value.editablePayment ?: return
        val editableSplits = _paymentScreenState.value.editableSplits
        val totalAmount = editablePayment.amount

        if (editableSplits.isEmpty()) return

        val updatedSplits = editableSplits.map { split ->
            val percentage = split.percentage ?: 0.0
            val amount = (totalAmount * percentage / 100.0)
            split.copy(amount = amount)
        }

        _paymentScreenState.update { currentState ->
            currentState.copy(editableSplits = updatedSplits)
        }
    }


    private fun updateEditablePayment(update: (PaymentEntity) -> PaymentEntity) {
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
        val selectedMembers = _paymentScreenState.value.selectedMembers
        val paidToUser = _paymentScreenState.value.paidToUser
        val existingSplits = _paymentScreenState.value.editableSplits

        Log.d("PaymentsVM", "Recalculating splits")
        Log.d("PaymentsVM", "Payment type: ${editablePayment.paymentType}")
        Log.d("PaymentsVM", "Selected members: $selectedMembers")
        Log.d("PaymentsVM", "PaidToUser: $paidToUser")
        Log.d("PaymentsVM", "Payment amount: ${editablePayment.amount}")

        val newSplits = when {
            editablePayment.paymentType == "transferred" && paidToUser != null -> {
                Log.d("PaymentsVM", "Creating single split for transfer payment")
                listOf(PaymentSplitEntity(
                    id = 0,
                    paymentId = editablePayment.id,
                    userId = paidToUser,
                    amount = editablePayment.amount,
                    currency = editablePayment.currency ?: "GBP",
                    createdAt = DateUtils.getCurrentTimestamp(),
                    updatedAt = DateUtils.getCurrentTimestamp(),
                    createdBy = userId,
                    updatedBy = userId,
                    deletedAt = null
                ))
            }
            editablePayment.splitMode == "equally" -> {
                if (selectedMembers.isEmpty()) emptyList()
                else {
                    val totalAmount = editablePayment.amount
                    val memberCount = selectedMembers.size
                    val baseAmount = (totalAmount * 100).toLong() / memberCount
                    val remainder = ((totalAmount * 100).toLong() % memberCount).toInt()

                    Log.d("PaymentsVM", "Equal split calculation:")
                    Log.d("PaymentsVM", "Total amount: $totalAmount")
                    Log.d("PaymentsVM", "Member count: $memberCount")
                    Log.d("PaymentsVM", "Base amount (cents): $baseAmount")
                    Log.d("PaymentsVM", "Remainder (cents): $remainder")

                    val remainderRecipients = selectedMembers.shuffled().take(kotlin.math.abs(remainder.toInt()))

                    selectedMembers.map { member ->
                        val extra = if (remainderRecipients.contains(member)) {
                            if (remainder > 0) 0.01 else -0.01  // Handle negative amounts
                        } else {
                            0.0
                        }

                        val memberAmount = (baseAmount / 100.0) + extra

                        Log.d("PaymentsVM", "Member ${member.userId} amount: $memberAmount")

                        PaymentSplitEntity(
                            id = 0,
                            paymentId = editablePayment.id,
                            userId = member.userId,
                            amount = memberAmount,
                            currency = editablePayment.currency ?: "GBP",
                            createdAt = DateUtils.getCurrentTimestamp(),
                            updatedAt = DateUtils.getCurrentTimestamp(),
                            createdBy = userId,
                            updatedBy = userId,
                            deletedAt = null
                        )
                    }
                }
            }
            editablePayment.splitMode == "percentage" -> {
                // Prepare input splits for the calculator
                val splitsForCalculation = if (existingSplits.any { it.percentage != null }) {
                    // Filter existing splits for selected members
                    val existingSplitsForMembers = existingSplits
                        .filter { split -> selectedMembers.any { it.userId == split.userId } }

                    // For newly selected members, create splits with default percentage
                    val existingUserIds = existingSplitsForMembers.map { it.userId }.toSet()
                    val remainingMembers = selectedMembers.filter { it.userId !in existingUserIds }

                    if (remainingMembers.isNotEmpty()) {
                        val usedPercentage = existingSplitsForMembers.sumOf { it.percentage ?: 0.0 }
                        val defaultPercentage = (100.0 - usedPercentage) / remainingMembers.size

                        existingSplitsForMembers + remainingMembers.map { member ->
                            PaymentSplitEntity(
                                id = 0,
                                paymentId = editablePayment.id,
                                userId = member.userId,
                                amount = 0.0,  // Will be calculated by the calculator
                                percentage = defaultPercentage,
                                currency = editablePayment.currency ?: "GBP",
                                createdAt = DateUtils.getCurrentTimestamp(),
                                updatedAt = DateUtils.getCurrentTimestamp(),
                                createdBy = userId,
                                updatedBy = userId,
                                deletedAt = null
                            )
                        }
                    } else {
                        existingSplitsForMembers
                    }
                } else {
                    // Create new splits with equal percentages
                    val equalPercentage = 100.0 / selectedMembers.size
                    selectedMembers.map { member ->
                        PaymentSplitEntity(
                            id = 0,
                            paymentId = editablePayment.id,
                            userId = member.userId,
                            amount = 0.0,  // Will be calculated by the calculator
                            percentage = equalPercentage,
                            currency = editablePayment.currency ?: "GBP",
                            createdAt = DateUtils.getCurrentTimestamp(),
                            updatedAt = DateUtils.getCurrentTimestamp(),
                            createdBy = userId,
                            updatedBy = userId,
                            deletedAt = null
                        )
                    }
                }

                // Use the splitCalculator to calculate the actual amounts
                splitCalculator.calculateSplits(
                    payment = editablePayment,
                    splits = splitsForCalculation,
                    targetAmount = BigDecimal.valueOf(editablePayment.amount),
                    targetCurrency = editablePayment.currency ?: "GBP",
                    userId = userId,
                    currentTime = DateUtils.getCurrentTimestamp()
                )
            }
            editablePayment.splitMode == "unequally" -> {
                _paymentScreenState.value.editableSplits.filter { split ->
                    selectedMembers.any { it.userId == split.userId }
                }
            }
            else -> emptyList()
        }

        Log.d("PaymentsVM", "New splits created: $newSplits")
        _paymentScreenState.update { currentState ->
            currentState.copy(editableSplits = newSplits)
        }
    }

    private fun calculateEqualSplits(amount: Double, members: List<GroupMemberEntity>, userId: Int): List<PaymentSplitEntity> {
        val selectedMembers = _paymentScreenState.value.selectedMembers
        if (selectedMembers.isEmpty()) return emptyList()

        val perPerson = if (amount != 0.0) amount / selectedMembers.size else 0.0

        return selectedMembers.map { member ->
            PaymentSplitEntity(
                id = 0,
                paymentId = _paymentScreenState.value.editablePayment?.id ?: 0,
                userId = member.userId,
                amount = perPerson,
                currency = _paymentScreenState.value.editablePayment?.currency ?: "GBP",
                createdAt = DateUtils.getCurrentTimestamp(),
                updatedAt = DateUtils.getCurrentTimestamp(),
                createdBy = userId,
                updatedBy = userId,
                deletedAt = null
            )
        }
    }

    fun createPaymentFromTransaction(transaction: Transaction) {
        viewModelScope.launch {
            val editablePayment = _paymentScreenState.value.editablePayment ?: return@launch
            val editableSplits = _paymentScreenState.value.editableSplits

            _paymentScreenState.value = _paymentScreenState.value.copy(
                paymentOperationStatus = PaymentOperationStatus.Loading
            )

            // Format the payment date
            val formattedDate = DateUtils.formatToStorageFormat(editablePayment.paymentDate)
            val paymentToSave = editablePayment.copy(
                paymentDate = formattedDate,
                transactionId = transaction.transactionId
            )

            val result = paymentRepository.createPaymentFromTransaction(
                transaction = transaction,
                payment = paymentToSave,
                splits = editableSplits
            )

            // Handle the result
            result.fold(
                onSuccess = { savedPayment ->
                    _paymentScreenState.value = _paymentScreenState.value.copy(
                        payment = savedPayment,
                        editablePayment = savedPayment,
                        splits = editableSplits,
                        editableSplits = editableSplits,
                        paymentOperationStatus = PaymentOperationStatus.Success
                    )
                    _navigationState.value = NavigationState.NavigateBack
                },
                onFailure = { error ->
                    _paymentScreenState.value = _paymentScreenState.value.copy(
                        paymentOperationStatus = PaymentOperationStatus.Error(
                            error.message ?: "Failed to create payment from transaction"
                        )
                    )
                }
            )
        }
    }

    fun savePayment() {
        viewModelScope.launch {
            Log.d("PaymentsVM", "Starting save payment")
            val editablePayment = _paymentScreenState.value.editablePayment ?: return@launch
            val paidToUser = _paymentScreenState.value.paidToUser

            Log.d("PaymentsVM", "Payment type: ${editablePayment.paymentType}")
            Log.d("PaymentsVM", "Paid to user: $paidToUser")
            Log.d("PaymentsVM", "Current state: ${_paymentScreenState.value}")

            // Validate transfer payments
            if (editablePayment.paymentType == "transferred") {
                // Check if paid by and paid to are the same user
                if (editablePayment.paidByUserId == paidToUser) {
                    // Show validation error
                    _paymentScreenState.value = _paymentScreenState.value.copy(
                        paymentOperationStatus = PaymentOperationStatus.Error(
                            "Cannot transfer money to and from the same person"
                        )
                    )
                    return@launch
                }

                // Check if paid to user is null
                if (paidToUser == null) {
                    _paymentScreenState.value = _paymentScreenState.value.copy(
                        paymentOperationStatus = PaymentOperationStatus.Error(
                            "Please select a recipient for the transfer"
                        )
                    )
                    return@launch
                }
            }

            // Use effectiveSplits instead of editableSplits directly
            val splitsToSave = if (editablePayment.paymentType == "transferred") {
                if (paidToUser != null) {
                    Log.d("PaymentsVM", "Creating single transfer split for user $paidToUser")
                    listOf(PaymentSplitEntity(
                        id = 0,  // New split for transfer
                        paymentId = editablePayment.id,
                        userId = paidToUser,
                        amount = editablePayment.amount,
                        currency = editablePayment.currency ?: "GBP",
                        createdAt = DateUtils.getCurrentTimestamp(),
                        updatedAt = DateUtils.getCurrentTimestamp(),
                        createdBy = userId ?: 0,
                        updatedBy = userId ?: 0,
                        deletedAt = null
                    ))
                } else {
                    Log.e("PaymentsVM", "No paidToUser set for transfer payment")
                    emptyList()
                }
            } else {
                _paymentScreenState.value.editableSplits
            }

            Log.d("PaymentsVM", "Payment type: ${editablePayment.paymentType}")
            Log.d("PaymentsVM", "Splits to save: $splitsToSave")

            _paymentScreenState.value = _paymentScreenState.value.copy(
                paymentOperationStatus = PaymentOperationStatus.Loading
            )

            val formattedDate = DateUtils.formatToStorageFormat(editablePayment.paymentDate)
            val paymentToSave = editablePayment.copy(paymentDate = formattedDate)

            val result = when {
                paymentToSave.id == 0 && _paymentScreenState.value.isTransaction -> {
                    val transactionId = paymentToSave.transactionId

                    val transaction = paymentToSave.transactionId?.let { transactionId ->
                        transactionRepository.getTransactionById(transactionId).firstOrNull()?.toModel()
                    }
                    if (transaction != null) {
                        paymentRepository.createPaymentFromTransaction(
                            transaction = transaction,
                            payment = paymentToSave,
                            splits = splitsToSave  // Use splitsToSave here
                        )
                    } else {
                        Result.failure(Exception("Transaction not found"))
                    }
                }
                paymentToSave.id == 0 -> {
                    paymentRepository.createPaymentWithSplits(paymentToSave, splitsToSave)  // Use splitsToSave here
                }
                else -> {
                    paymentRepository.updatePaymentWithSplits(paymentToSave, splitsToSave)  // Use splitsToSave here
                }
            }

            result.fold(
                onSuccess = { savedPayment ->
                    Log.d("PaymentsVM", "Payment saved successfully")
                    Log.d("PaymentsVM", "Saved payment: $savedPayment")
                    Log.d("PaymentsVM", "Saved splits: $splitsToSave")
                    _paymentScreenState.value = _paymentScreenState.value.copy(
                        payment = savedPayment,
                        editablePayment = savedPayment,
                        splits = splitsToSave,  // Use splitsToSave here
                        editableSplits = splitsToSave,  // Use splitsToSave here
                        paymentOperationStatus = PaymentOperationStatus.Success
                    )
                    _navigationState.value = NavigationState.NavigateBack
                },
                onFailure = { error ->
                    Log.e("PaymentsVM", "Failed to save payment", error)
                    _paymentScreenState.value = _paymentScreenState.value.copy(
                        paymentOperationStatus = PaymentOperationStatus.Error(error.message ?: "Unknown error")
                    )
                }
            )
        }
    }

    fun recordSettlementPayment(instruction: SettlingInstruction) {
        viewModelScope.launch {
            try {
                _paymentScreenState.update { it.copy(
                    paymentOperationStatus = PaymentOperationStatus.Loading
                )}

                // Create a payment entity with type "transferred"
                val paymentToCreate = PaymentEntity(
                    id = 0,
                    groupId = instruction.groupId,
                    paidByUserId = instruction.fromId,  // The person sending the money
                    transactionId = null,
                    amount = instruction.amount,
                    description = "Settlement payment from ${instruction.fromName} to ${instruction.toName}",
                    notes = "Record of a settlement payment that was completed outside of this app",
                    paymentDate = DateUtils.getCurrentTimestamp(),
                    currency = instruction.currency,
                    splitMode = "equally",
                    paymentType = "transferred",
                    institutionId = null,
                    createdBy = userId ?: 0,
                    updatedBy = userId ?: 0,
                    createdAt = DateUtils.getCurrentTimestamp(),
                    updatedAt = DateUtils.getCurrentTimestamp(),
                    deletedAt = null
                )

                // Create a single split for the recipient
                val recipientSplit = PaymentSplitEntity(
                    id = 0,
                    paymentId = 0,  // Will be set by createPaymentWithSplits
                    userId = instruction.toId,  // The person receiving the money
                    amount = instruction.amount,
                    currency = instruction.currency,
                    createdAt = DateUtils.getCurrentTimestamp(),
                    updatedAt = DateUtils.getCurrentTimestamp(),
                    createdBy = userId ?: 0,
                    updatedBy = userId ?: 0,
                    deletedAt = null
                )

                paymentRepository.createPaymentWithSplits(paymentToCreate, listOf(recipientSplit))
                    .onSuccess { savedPayment ->
                        _paymentScreenState.update { currentState ->
                            currentState.copy(
                                paymentOperationStatus = PaymentOperationStatus.Success
                            )
                        }

                        // Notify that the payment was successful
                        _navigationState.value = NavigationState.NavigateBack
                    }
                    .onFailure { error ->
                        Log.e("PaymentsVM", "Failed to save settlement payment", error)
                        _paymentScreenState.update { currentState ->
                            currentState.copy(
                                paymentOperationStatus = PaymentOperationStatus.Error(error.message ?: "Unknown error")
                            )
                        }
                    }
            } catch (e: Exception) {
                Log.e("PaymentsVM", "Exception recording settlement payment", e)
                _paymentScreenState.update { currentState ->
                    currentState.copy(
                        paymentOperationStatus = PaymentOperationStatus.Error(e.message ?: "Unknown error")
                    )
                }
            }
        }
    }

    // Add this function to reset navigation state
    fun resetNavigationState() {
        _navigationState.value = NavigationState.Idle
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

    fun loadPaymentItemInfo(payment: PaymentEntity) {
        viewModelScope.launch {
            updatePaymentItemInfo(payment.id) {
                it?.copy(isLoading = true) ?: PaymentItemInfo(isLoading = true)
            }

            try {
                // Load transaction if available
                val transaction = payment.transactionId?.let { transactionId ->
                    transactionRepository.getTransactionById(transactionId)
                        .firstOrNull()?.toModel()
                }

                // Load username
                val username = try {
                    val user = userRepository.getUserById(payment.paidByUserId)
                    user.firstOrNull()?.username
                } catch (e: Exception) {
                    null
                }

                // Load payment image (logo or placeholder)
                val paymentImage = loadPaymentImage(transaction, payment)

                // Update state with all info
                updatePaymentItemInfo(payment.id) {
                    PaymentItemInfo(
                        paymentImage = paymentImage,
                        paidByUsername = username,
                        transaction = transaction,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                updatePaymentItemInfo(payment.id) {
                    PaymentItemInfo(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun getUserInvolvementInPayment(paymentId: Int, userId: Int): Flow<UserInvolvement> = flow {
        try {
            Log.d("PaymentsVM", "Checking user involvement for payment $paymentId and user $userId")

            // Use suspend function to get payment with splits
            val paymentWithSplits = paymentRepository.getPaymentWithSplits(paymentId)

            if (paymentWithSplits != null) {
                val payment = paymentWithSplits.payment
                val splits = paymentWithSplits.splits

                Log.d("PaymentsVM", "Found payment: $payment")
                Log.d("PaymentsVM", "Found splits: $splits")
                Log.d("PaymentsVM", "Payment type: ${payment.paymentType}")
                Log.d("PaymentsVM", "Paid by user ID: ${payment.paidByUserId}")

                when {
                    // SPENT scenarios
                    payment.paymentType == "spent" -> {
                        if (payment.paidByUserId == userId) {
                            // Scenario a: The user spent the money and is owed by other(s)
                            Log.d("PaymentsVM", "User paid for this expense")

                            // Find the user's split (if exists)
                            val userSplit = splits.find { it.userId == userId }
                            val userSplitAmount = userSplit?.amount ?: 0.0
                            val totalAmount = Math.abs(payment.amount)

                            // Calculate how much the user actually paid for others
                            val userPortion = Math.abs(userSplitAmount)
                            val amountPaidForOthers = totalAmount - userPortion

                            Log.d("PaymentsVM", "Total amount: $totalAmount")
                            Log.d("PaymentsVM", "User's share: $userPortion")
                            Log.d("PaymentsVM", "Amount paid for others: $amountPaidForOthers")

                            emit(UserInvolvement.YouPaidAndAreOwed(amountPaidForOthers))
                        } else {
                            // Check if user has a split
                            val userSplit = splits.find { it.userId == userId }

                            if (userSplit != null) {
                                // Scenario b: Another user spent the money and the user owes them
                                val splitAmount = Math.abs(userSplit.amount)
                                Log.d("PaymentsVM", "User owes for this expense: $splitAmount")
                                emit(UserInvolvement.SomeoneElsePaidAndYouOwe(splitAmount))
                            } else {
                                // Scenario c: Another user spent the money and the user wasn't involved
                                Log.d("PaymentsVM", "User is not involved in this expense")
                                emit(UserInvolvement.NotInvolved)
                            }
                        }
                    }

                    // RECEIVED scenarios
                    payment.paymentType == "received" -> {
                        if (payment.paidByUserId == userId) {
                            // Scenario d: The user received the money and is sharing it with other(s)
                            Log.d("PaymentsVM", "User received this income and is sharing it")

                            val totalAmount = Math.abs(payment.amount)
                            // Find the user's split to calculate their portion
                            val userSplit = splits.find { it.userId == userId }
                            val userPortion = userSplit?.amount?.let { Math.abs(it) } ?: totalAmount

                            // The amount being shared is the total minus the user's portion
                            val sharingAmount = totalAmount - userPortion

                            Log.d("PaymentsVM", "Total received: $totalAmount")
                            Log.d("PaymentsVM", "User's portion: $userPortion")
                            Log.d("PaymentsVM", "Amount sharing with others: $sharingAmount")

                            // FIX: Use sharingAmount instead of totalAmount to show what the user is sharing
                            emit(UserInvolvement.YouReceivedAndAreSharing(sharingAmount))
                        } else {
                            // Check if user has a split
                            val userSplit = splits.find { it.userId == userId }

                            if (userSplit != null) {
                                // Scenario e: Another user received the money and is sharing it with the user
                                val shareAmount = Math.abs(userSplit.amount)
                                Log.d("PaymentsVM", "User's share of received money: $shareAmount")
                                emit(UserInvolvement.SomeoneElseReceivedAndIsSharing(shareAmount))
                            } else {
                                // Scenario f: Another user received the money and the user isn't involved
                                Log.d("PaymentsVM", "User is not involved in this received payment")
                                emit(UserInvolvement.NotInvolved)
                            }
                        }
                    }

                    // TRANSFERRED scenarios
                    payment.paymentType == "transferred" -> {
                        val transferAmount = Math.abs(payment.amount)

                        if (payment.paidByUserId == userId) {
                            // Scenario g: The user transferred money to another user
                            Log.d("PaymentsVM", "User sent this transfer: $transferAmount")
                            emit(UserInvolvement.YouSentMoney(transferAmount))
                        } else {
                            // Check if user has a split (indicating they received the transfer)
                            val userSplit = splits.find { it.userId == userId }

                            if (userSplit != null) {
                                // Scenario h: Another user transferred money to the user
                                Log.d("PaymentsVM", "User received this transfer: $transferAmount")
                                emit(UserInvolvement.YouReceivedMoney(transferAmount))
                            } else {
                                // Scenario i: Another user transferred money to another, different user
                                Log.d("PaymentsVM", "User is not involved in this transfer")
                                emit(UserInvolvement.NotInvolved)
                            }
                        }
                    }

                    // Default case
                    else -> {
                        Log.d("PaymentsVM", "Unknown payment type or user not involved")
                        emit(UserInvolvement.NotInvolved)
                    }
                }
            } else {
                Log.d("PaymentsVM", "Payment with splits not found")
                emit(UserInvolvement.NotInvolved)
            }
        } catch (e: Exception) {
            Log.e("PaymentsVM", "Error determining user involvement", e)
            emit(UserInvolvement.NotInvolved)
        }
    }

    private suspend fun loadPaymentImage(
        transaction: Transaction?,
        payment: PaymentEntity
    ): PaymentImage {
        val effectiveInstitutionId = transaction?.institutionId ?: payment.institutionId

        return try {
            effectiveInstitutionId?.let { id ->
                institutionRepository.getLocalInstitutionLogo(id)?.let {
                    PaymentImage.Logo(it)
                }
            } ?: PaymentImage.Placeholder("default")
        } catch (e: Exception) {
            PaymentImage.Placeholder("error")
        }
    }

    private fun updatePaymentItemInfo(
        paymentId: Int,
        update: (PaymentItemInfo?) -> PaymentItemInfo
    ) {
        _paymentItemInfo.update { currentMap ->
            currentMap + (paymentId to update(currentMap[paymentId]))
        }
    }

    fun clearPaymentItemInfo(paymentId: Int) {
        _paymentItemInfo.update { currentMap ->
            currentMap - paymentId
        }
    }

    fun fetchGroupPayments(groupId: Int) {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                Log.d("PaymentsViewModel", "Starting payment fetch for group: $groupId")

                paymentRepository.getGroupPayments(groupId)
                    .catch { e ->
                        Log.e("PaymentsViewModel", "Error fetching payments", e)
                        _error.value = e.message
                        _loading.value = false
                    }
                    .collect { payments ->
                        Log.d("PaymentsViewModel", "Received ${payments.size} payments")
                        _groupPaymentsAndSplits.value = payments
                        _loading.value = false
                    }
            } catch (e: Exception) {
                Log.e("PaymentsViewModel", "Error in fetchGroupPayments", e)
                _error.value = e.message
                _loading.value = false
            }
        }
    }

    fun refreshPayment(paymentId: Int) {
        viewModelScope.launch {
            try {
                // Re-load the payment and its splits
                loadExistingPayment(paymentId)
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing payment", e)
            }
        }
    }

    fun convertPaymentCurrency(useCustomRate: Boolean = false, customRate: Double? = null) {
        viewModelScope.launch {
            try {
                val currentPayment = _paymentScreenState.value.editablePayment ?: return@launch
                val userId = this@PaymentsViewModel.userId ?: return@launch
                val targetCurrency = _paymentScreenState.value.group?.defaultCurrency ?: return@launch

                _paymentScreenState.update { it.copy(
                    isConverting = true,
                    conversionError = null
                )}

                val result = paymentRepository.convertCurrency(
                    amount = currentPayment.amount,
                    fromCurrency = currentPayment.currency ?: "GBP",
                    toCurrency = targetCurrency,
                    paymentId = currentPayment.id,
                    userId = userId,
                    paymentDate = currentPayment.paymentDate,
                    customExchangeRate = customRate
                )

                result.fold(
                    onSuccess = { conversion ->
                        // Update payment with new amount and currency
                        val updatedPayment = currentPayment.copy(
                            amount = conversion.convertedAmount,
                            currency = conversion.targetCurrency
                        )

                        // Update state
                        _paymentScreenState.update { state ->
                            state.copy(
                                editablePayment = updatedPayment,
                                payment = updatedPayment,
                                isConverting = false
                            )
                        }

                        // Navigate back
                        _navigationState.value = NavigationState.NavigateBack
                    },
                    onFailure = { error ->
                        _paymentScreenState.update { it.copy(
                            isConverting = false,
                            conversionError = error.message ?: "Failed to convert currency"
                        )}
                    }
                )
            } catch (e: Exception) {
                _paymentScreenState.update { it.copy(
                    isConverting = false,
                    conversionError = e.message ?: "Failed to convert currency"
                )}
            }
        }
    }

    fun undoCurrencyConversion() {
        viewModelScope.launch {
            try {
                val payment = _paymentScreenState.value.payment ?: return@launch

                _paymentScreenState.update { it.copy(
                    paymentOperationStatus = PaymentOperationStatus.Loading
                )}

                paymentRepository.undoCurrencyConversion(payment.id)
                    .onSuccess {
                        // Reload payment data
                        loadExistingPayment(payment.id)
                    }
                    .onFailure { error ->
                        _paymentScreenState.update { it.copy(
                            paymentOperationStatus = PaymentOperationStatus.Error(
                                error.message ?: "Failed to undo currency conversion"
                            )
                        )}
                    }
            } catch (e: Exception) {
                Log.e("PaymentsViewModel", "Error in undoCurrencyConversion", e)
                _paymentScreenState.update { it.copy(
                    paymentOperationStatus = PaymentOperationStatus.Error(
                        e.message ?: "An unexpected error occurred"
                    )
                )}
            }
        }
    }

    fun resetOperationStatus() {
        _paymentScreenState.update { it.copy(
            paymentOperationStatus = PaymentOperationStatus.Idle
        ) }
    }

    fun fetchExchangeRate(fromCurrency: String, toCurrency: String, paymentDate: String?) {
        viewModelScope.launch {
            try {
                _exchangeRate.value = null
                _exchangeRateDate.value = null

                // Call your existing repository method to get exchange rate information
                paymentRepository.getExchangeRateInfo(
                    fromCurrency = fromCurrency,
                    toCurrency = toCurrency,
                    paymentDate = paymentDate
                ).onSuccess { rateInfo ->
                    _exchangeRate.value = rateInfo.rate
                    _exchangeRateDate.value = rateInfo.date
                }
            } catch (e: Exception) {
                Log.e("PaymentsViewModel", "Error fetching exchange rate", e)
            }
        }
    }

    /**
     * Process multiple transactions at once, creating payments with default settings
     */
    fun addMultipleTransactions(
        transactions: List<Transaction>,
        groupId: Int,
        onComplete: (Result<Int>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Processing ${transactions.size} transactions as batch")
                _paymentScreenState.update { it.copy(paymentOperationStatus = PaymentOperationStatus.Loading) }

                // Get group info for defaults
                val group = groupRepository.getGroupById(groupId).firstOrNull()
                val defaultSplitMode = group?.defaultSplitMode ?: "equally"
                val defaultCurrency = group?.defaultCurrency ?: "GBP"

                // Get current user ID
                val userId = userId ?: return@launch

                // Process each transaction
                var successCount = 0

                for (transaction in transactions) {
                    try {
                        // Create a copy of the transaction with the user ID set
                        val transactionWithUser = transaction.copy(
                            userId = userId,  // Set the user ID here
                            createdAt = DateUtils.getCurrentTimestamp(),
                            updatedAt = DateUtils.getCurrentTimestamp()
                        )

                        // Save the transaction with userId properly set
                        transactionRepository.saveTransaction(transactionWithUser)

                        // Determine payment type
                        val paymentType = if (transaction.getEffectiveAmount() > 0) "received" else "spent"
                        val paymentAmount = kotlin.math.abs(transaction.getEffectiveAmount())

                        // Create payment entity
                        val description = transaction.creditorName
                            ?: transaction.remittanceInformationUnstructured
                            ?: "Transaction ${transaction.transactionId?.takeLast(4) ?: ""}"

                        val payment = PaymentEntity(
                            id = 0,
                            groupId = groupId,
                            paidByUserId = userId,
                            transactionId = transaction.transactionId,
                            amount = paymentAmount,
                            description = description,
                            notes = transaction.remittanceInformationUnstructured ?: "",
                            paymentDate = transaction.bookingDateTime ?: DateUtils.getCurrentTimestamp(),
                            currency = transaction.getEffectiveCurrency() ?: defaultCurrency,
                            splitMode = defaultSplitMode,
                            paymentType = paymentType,
                            institutionId = transaction.institutionId,
                            createdBy = userId,
                            updatedBy = userId,
                            createdAt = DateUtils.getCurrentTimestamp(),
                            updatedAt = DateUtils.getCurrentTimestamp(),
                            deletedAt = null
                        )

                        // Create payment with empty splits (the repository will handle default splitting)
                        paymentRepository.createPaymentWithSplits(payment, emptyList())
                            .onSuccess {
                                successCount++
                            }
                            .onFailure { error ->
                                Log.e(TAG, "Failed to process transaction ${transaction.transactionId}", error)
                            }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing transaction ${transaction.transactionId}", e)
                    }
                }

                Log.d(TAG, "Batch processing complete. Processed $successCount/${transactions.size} transactions")

                _paymentScreenState.update { it.copy(paymentOperationStatus = PaymentOperationStatus.Success) }

                if (successCount > 0) {
                    onComplete(Result.success(successCount))
                } else {
                    onComplete(Result.failure(Exception("Failed to process any transactions")))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in batch transaction processing", e)
                _paymentScreenState.update { it.copy(
                    paymentOperationStatus = PaymentOperationStatus.Error(e.message ?: "Unknown error")
                )}
                onComplete(Result.failure(e))
            }
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