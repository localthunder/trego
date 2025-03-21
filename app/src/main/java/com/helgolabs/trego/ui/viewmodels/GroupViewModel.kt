package com.helgolabs.trego.ui.viewmodels

import android.content.ContentValues.TAG
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.helgolabs.trego.data.extensions.toModel
import com.helgolabs.trego.data.local.dataClasses.BatchConversionResult
import com.helgolabs.trego.data.local.dataClasses.CurrencySettlingInstructions
import com.helgolabs.trego.data.local.dataClasses.SettlingInstruction
import com.helgolabs.trego.data.local.dataClasses.UserBalanceWithCurrency
import com.helgolabs.trego.data.local.dataClasses.UserGroupListItem
import com.helgolabs.trego.data.local.entities.GroupDefaultSplitEntity
import com.helgolabs.trego.data.local.entities.GroupEntity
import com.helgolabs.trego.data.local.entities.GroupMemberEntity
import com.helgolabs.trego.data.local.entities.PaymentEntity
import com.helgolabs.trego.data.local.entities.UserEntity
import com.helgolabs.trego.data.repositories.GroupRepository
import com.helgolabs.trego.data.repositories.PaymentRepository
import com.helgolabs.trego.data.repositories.UserRepository
import com.helgolabs.trego.data.sync.SyncStatus
import com.helgolabs.trego.utils.DateUtils
import com.helgolabs.trego.utils.ImageUtils
import com.helgolabs.trego.utils.PlaceholderImageGenerator
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class GroupViewModel(
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    private val paymentRepository: PaymentRepository,
    private val context: Context
) : ViewModel() {

    data class GroupDetailsState(
        val group: GroupEntity? = null,
        val groupMembers: List<GroupMemberEntity> = emptyList(),
        val usernames: Map<Int, String> = emptyMap(),
        val payments: List<PaymentEntity> = emptyList(),
        val groupImage: String? = null,
        val uploadStatus: UploadStatus = UploadStatus.Idle,
        val isLoading: Boolean = false,
        val error: String? = null,
        val imageLoadingState: ImageLoadingState = ImageLoadingState.Idle,
        val localImagePath: String? = null,
        val isDownloadingImage: Boolean = false,
        val isArchived: Boolean = false,
        val isConverting: Boolean = false,
        val isPlaceholderImage: Boolean = false,
        val imageUpdateTimestamp: Long = System.currentTimeMillis(),
        val conversionResult: BatchConversionResult? = null,
        val conversionError: String? = null,
        val generatedInviteLink: String? = null,
        val users: List<UserEntity> = emptyList()
        )


    // Add this sealed class inside your GroupViewModel
    sealed class ImageLoadingState {
        object Idle : ImageLoadingState()
        object Loading : ImageLoadingState()
        object Success : ImageLoadingState()
        data class Error(val message: String) : ImageLoadingState()
    }

    sealed class ArchiveGroupState {
        object Idle : ArchiveGroupState()
        object Loading : ArchiveGroupState()
        object Success : ArchiveGroupState()
        data class Error(val message: String) : ArchiveGroupState()
    }

    sealed class RestoreGroupState {
        object Idle : RestoreGroupState()
        object Loading : RestoreGroupState()
        object Success : RestoreGroupState()
        data class Error(val message: String) : RestoreGroupState()
    }

    // Define operation states
    sealed class OperationState {
        object Idle : OperationState()
        object Loading : OperationState()
        object Success : OperationState()
        data class Error(val message: String) : OperationState()
    }

    private val _groupDetailsState = MutableStateFlow(GroupDetailsState())
    val groupDetailsState: StateFlow<GroupDetailsState> = _groupDetailsState.asStateFlow()

    private val _groupCreationStatus =
        MutableLiveData<Result<Pair<GroupEntity, GroupMemberEntity>>>()
    val groupCreationStatus: LiveData<Result<Pair<GroupEntity, GroupMemberEntity>>> =
        _groupCreationStatus

    private val _groupUpdateStatus = MutableLiveData<Result<GroupEntity>>()
    val groupUpdateStatus: LiveData<Result<GroupEntity>> = _groupUpdateStatus

    private val _payments = MutableStateFlow<List<PaymentEntity>>(emptyList())
    val sortedPayments: StateFlow<List<PaymentEntity>> = _payments
        .map { payments -> payments.sortedByDescending { it.paymentDate } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _usernames = MutableLiveData<Map<Int, String>>()
    val usernames: LiveData<Map<Int, String>> = _usernames

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _addMemberResult = MutableLiveData<Result<GroupMemberEntity>?>()
    val addMemberResult: LiveData<Result<GroupMemberEntity>?> = _addMemberResult

    private val _userGroups = MutableStateFlow<List<GroupEntity>>(emptyList())
    val userGroups: StateFlow<List<GroupEntity>> = _userGroups.asStateFlow()

    private val _group = MutableStateFlow<GroupEntity?>(null)
    val group: StateFlow<GroupEntity?> = _group

    private val _groupMembers = MutableStateFlow<List<GroupMemberEntity>>(emptyList())
    val groupMembers: StateFlow<List<GroupMemberEntity>> = _groupMembers.asStateFlow()

    private val _groupImage = MutableStateFlow<String?>(null)
    val groupImage: StateFlow<String?> = _groupImage

    private val _uploadStatus = MutableStateFlow<UploadStatus>(UploadStatus.Idle)
    val uploadStatus: StateFlow<UploadStatus> = _uploadStatus

    private val _uploadError = MutableStateFlow<String?>(null)
    val uploadError: StateFlow<String?> = _uploadError

    private val _groupBalances = MutableStateFlow<List<UserBalanceWithCurrency>>(emptyList())
    val groupBalances: StateFlow<List<UserBalanceWithCurrency>> = _groupBalances.asStateFlow()

    private val _userGroupItems = MutableStateFlow<List<UserGroupListItem>>(emptyList())
    val userGroupItems: StateFlow<List<UserGroupListItem>> = _userGroupItems.asStateFlow()

    private val _restoreGroupState = MutableStateFlow<RestoreGroupState>(RestoreGroupState.Idle)
    val restoreGroupState: StateFlow<RestoreGroupState> = _restoreGroupState.asStateFlow()

    private val _archiveGroupState = MutableStateFlow<ArchiveGroupState>(ArchiveGroupState.Idle)
    val archiveGroupState: StateFlow<ArchiveGroupState> = _archiveGroupState.asStateFlow()

    private val _archivedGroupItems = MutableStateFlow<List<UserGroupListItem>>(emptyList())
    val archivedGroupItems: StateFlow<List<UserGroupListItem>> = _archivedGroupItems.asStateFlow()

    private val _currentUserBalance = MutableStateFlow<UserBalanceWithCurrency?>(null)
    val currentUserBalance: StateFlow<UserBalanceWithCurrency?> = _currentUserBalance.asStateFlow()

    private val _settlingInstructions = MutableStateFlow<List<CurrencySettlingInstructions>>(emptyList())
    val settlingInstructions: StateFlow<List<CurrencySettlingInstructions>> = _settlingInstructions.asStateFlow()

    private val _paymentsUpdateTrigger = MutableStateFlow(0)
    val paymentsUpdateTrigger: StateFlow<Int> = _paymentsUpdateTrigger.asStateFlow()

    // State for default splits
    private val _groupDefaultSplits = MutableStateFlow<List<GroupDefaultSplitEntity>>(emptyList())
    val groupDefaultSplits: StateFlow<List<GroupDefaultSplitEntity>> = _groupDefaultSplits.asStateFlow()

    // State for default split operations
    private val _defaultSplitOperationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val defaultSplitOperationState: StateFlow<OperationState> = _defaultSplitOperationState.asStateFlow()

    private val _imageUpdateEvent = MutableStateFlow(0L)
    val imageUpdateEvent: StateFlow<Long> = _imageUpdateEvent.asStateFlow()

    init {
        viewModelScope.launch {
            groupRepository.ensureGroupImagesDownloaded()
        }
    }

    private var currentLoadJob: Job? = null
    private var hasLoadedGroups: Boolean = false

    var hasLoadedGroup = false
    var hasLoadedMembers = false
    var hasLoadedPayments = false

    fun checkInitialLoadComplete() {
        if (hasLoadedGroup && hasLoadedMembers && hasLoadedPayments) {
            _groupDetailsState.update { it.copy(isLoading = false) }
        }
    }

    fun initializeGroupDetails(groupId: Int) {
        viewModelScope.launch {
            loadGroupDetails(groupId)
            fetchGroupBalances(groupId)
        }
    }

    fun loadGroupDetails(groupId: Int, forceRefresh: Boolean = false) {
        currentLoadJob?.cancel()
        currentLoadJob = viewModelScope.launch {
            _groupDetailsState.update { it.copy(isLoading = true, error = null) }

            try {
                // Force refresh from server if requested
                if (forceRefresh) {
                    try {
                        val refreshResult = groupRepository.forceRefreshGroup(groupId)
                        if (refreshResult.isFailure) {
                            Log.e("GroupViewModel", "Failed to force refresh: ${refreshResult.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.e("GroupViewModel", "Error during force refresh", e)
                    }
                }

                // Launch the existing loading functions
                launch {
                    try {
                        loadGroup(groupId)
                    } catch (e: Exception) {
                        Log.e("GroupViewModel", "Error loading group", e)
                        _groupDetailsState.update {
                            it.copy(
                                error = "Failed to load group details: ${e.message}"
                            )
                        }
                    } finally {
                        hasLoadedGroup = true
                        checkInitialLoadComplete()
                    }
                }
                launch {
                    try {
                        loadGroupMembers(groupId)
                    } catch (e: Exception) {
                        Log.e("GroupViewModel", "Error loading members", e)
                    } finally {
                        hasLoadedMembers = true
                        checkInitialLoadComplete()
                    }
                }
                launch {
                    try {
                        loadPayments(groupId)
                    } catch (e: Exception) {
                        Log.e("GroupViewModel", "Error loading payments", e)
                    } finally {
                        hasLoadedPayments = true
                        checkInitialLoadComplete()
                    }
                }
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Error loading group details", e)
                _groupDetailsState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load group details"
                    )
                }
            }
        }
    }

    private suspend fun loadGroup(groupId: Int) {
        try {
            val userId = getUserIdFromPreferences(context) ?: return

            // Create a flow that combines group data with archive status
            combine(
                groupRepository.getGroupById(groupId),
                groupRepository.isGroupArchived(groupId, userId)
            ) { group, isArchived ->
                // Return a pair of the group and archive status
                group to isArchived
            }.collect { (group, isArchived) ->
                // Check if this is a placeholder image from the server
                val groupImage = group?.groupImg
                val isPlaceholder = PlaceholderImageGenerator.isPlaceholderImage(groupImage)

                // If it's a placeholder, ensure we have a local file for it
                val localPath = if (isPlaceholder && groupImage != null) {
                    try {
                        // This will either retrieve or generate the placeholder image
                        val path = PlaceholderImageGenerator.getImageForPath(context, groupImage)
                        Log.d(TAG, "Generated/retrieved placeholder image at: $path for reference: $groupImage")
                        path
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to generate placeholder image", e)
                        null
                    }
                } else null

                // Update state with the combined data
                _groupDetailsState.update { currentState ->
                    currentState.copy(
                        group = group ?: currentState.group,
                        groupImage = groupImage,
                        isArchived = isArchived,
                        localImagePath = localPath,
                        isPlaceholderImage = isPlaceholder,
                        error = if (group == null && currentState.group == null)
                            "Unable to load group" else null,
                        imageLoadingState = when {
                            groupImage != null -> ImageLoadingState.Success
                            else -> ImageLoadingState.Idle
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("GroupViewModel", "Error loading group", e)
            _groupDetailsState.update { currentState ->
                currentState.copy(
                    error = if (currentState.group == null)
                        "Unable to load group: ${e.message}" else null
                )
            }
        }
    }

    private suspend fun loadGroupMembers(groupId: Int) {
        groupRepository.getGroupMembers(groupId).collect { members ->
            _groupDetailsState.update { currentState ->
                val newMembers = members
                currentState.copy(
                    groupMembers = if (newMembers.isNotEmpty()) newMembers else currentState.groupMembers
                )
            }
            fetchUsernamesFromRepository(members.map { it.userId })
        }
    }

    fun loadGroupMembersWithUsers(groupId: Int) {
        viewModelScope.launch {
            try {
                // First load group members
                groupRepository.getGroupMembers(groupId).collect { members ->
                    // Get all user IDs from the members
                    val userIds = members.map { it.userId }

                    // Load user data for these IDs
                    userRepository.getUsersByIds(userIds).collect { userEntities ->
                        _groupDetailsState.update { currentState ->
                            currentState.copy(
                                groupMembers = members,
                                users = userEntities
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading group members with users", e)
                _groupDetailsState.update { it.copy(error = e.message) }
            }
        }
    }

    private suspend fun loadPayments(groupId: Int) {
        paymentRepository.getPaymentsByGroup(groupId)
            .collect { paymentEntities ->
                if (paymentEntities != _payments.value) {
                    _payments.value = paymentEntities
                    _groupDetailsState.update { currentState ->
                        currentState.copy(payments = paymentEntities)
                    }
                }
            }
    }

    fun loadUserGroupsList(userId: Int, forceRefresh: Boolean = false) {
        // Only reload if we haven't loaded or are forced to refresh
        if (!hasLoadedGroups || forceRefresh) {
            viewModelScope.launch {
                Log.d("GroupViewModel", "Starting to load groups for user $userId")
                _loading.value = true
                _error.value = null
                try {
                    // Use combine to collect both streams together for group lists
                    val groupsFlow = combine(
                        groupRepository.getNonArchivedGroupsByUserId(userId),
                        groupRepository.getArchivedGroupsByUserId(userId)
                    ) { nonArchived, archived ->
                        Pair(nonArchived, archived)
                    }

                    // Collect the groups first
                    val (nonArchived, archived) = groupsFlow.first()

                    // Create lists to hold the final items with balances
                    val nonArchivedWithBalances = mutableListOf<UserGroupListItem>()
                    val archivedWithBalances = mutableListOf<UserGroupListItem>()

                    // Process each group to get its balance
                    coroutineScope {
                        // Process non-archived groups
                        val nonArchivedJobs = nonArchived.map { group ->
                            async {
                                try {
                                    // Calculate balances for this group
                                    val balances = groupRepository.calculateGroupBalances(group.id).getOrNull()

                                    // Find the current user's balance
                                    val userBalance = balances?.find { it.userId == userId }

                                    // Create a new UserGroupListItem with the balance
                                    group.copy(userBalance = userBalance)
                                } catch (e: Exception) {
                                    Log.e("GroupViewModel", "Error calculating balances for group ${group.id}", e)
                                    group // Return original if there's an error
                                }
                            }
                        }

                        // Process archived groups
                        val archivedJobs = archived.map { group ->
                            async {
                                try {
                                    // Calculate balances for this group
                                    val balances = groupRepository.calculateGroupBalances(group.id).getOrNull()

                                    // Find the current user's balance
                                    val userBalance = balances?.find { it.userId == userId }

                                    // Create a new UserGroupListItem with the balance
                                    group.copy(userBalance = userBalance)
                                } catch (e: Exception) {
                                    Log.e("GroupViewModel", "Error calculating balances for group ${group.id}", e)
                                    group // Return original if there's an error
                                }
                            }
                        }

                        // Await all jobs and collect results
                        nonArchivedWithBalances.addAll(nonArchivedJobs.awaitAll())
                        archivedWithBalances.addAll(archivedJobs.awaitAll())
                    }

                    // Update the state flows with the enriched data
                    _userGroupItems.value = nonArchivedWithBalances
                    _archivedGroupItems.value = archivedWithBalances

                    Log.d(
                        "GroupViewModel",
                        "Collected ${nonArchivedWithBalances.size} non-archived and ${archivedWithBalances.size} archived groups with balances"
                    )

                    _loading.value = false
                    hasLoadedGroups = true
                } catch (e: Exception) {
                    Log.e("GroupViewModel", "Error loading groups", e)
                    _error.value = e.message
                    _loading.value = false
                }
            }
        }
    }

    private fun fetchUsernamesFromRepository(userIds: List<Int>) {
        viewModelScope.launch {
            try {
                Log.d("GroupViewModel", "Fetching usernames for ${userIds.size} users")
                userRepository.getUsersByIds(userIds).collect { users ->
                    val usernameMap = users.associate { it.userId to it.username }
                    Log.d("GroupViewModel", "Usernames fetched: ${usernameMap.size}")
                    _groupDetailsState.update { currentState ->
                        currentState.copy(
                            usernames = currentState.usernames + usernameMap
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Error fetching usernames", e)
                _groupDetailsState.update { it.copy(error = "Failed to fetch usernames: ${e.message}") }
            }
        }
    }

    suspend fun getGroupById(groupId: Int): Flow<GroupEntity?> =
        groupRepository.getGroupById(groupId)


    fun loadUserGroups(userId: Int) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                groupRepository.getGroupsByUserId(userId)
                    .collect { groups ->
                        _userGroups.value = groups
                        _loading.value = false

                        // Load members for each group
                        groups.forEach { group ->
                            loadGroupMembers(group.id)
                        }
                    }
            } catch (e: Exception) {
                _error.value = e.message
                _loading.value = false
            }
        }
    }

    fun getGroupMembers(groupId: Int) {
        Log.d("GroupViewModel", "getGroupMembers called with groupId: $groupId")
        viewModelScope.launch {
            groupRepository.getGroupMembers(groupId)
                .catch { e ->
                    Log.e("GroupViewModel", "Error collecting group members", e)
                }
                .collect { members ->
                    Log.d("GroupViewModel", "Collected ${members.size} group members")
                    _groupMembers.value = members
                }
        }
    }


    fun createGroup(group: GroupEntity, creatorUserId: Int) {
        Log.d("GroupViewModel", "Creating group: ${group.name} for user: $creatorUserId")
        viewModelScope.launch {
            try {
                Log.d("GroupViewModel", "Starting group creation in coroutine")
                val result = groupRepository.createGroupWithMember(group, creatorUserId)
                Log.d("GroupViewModel", "Group creation result: ${result.isSuccess}")
                _groupCreationStatus.value = result
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Error creating group", e)
                _groupCreationStatus.value = Result.failure(e)
            }
        }
    }

    fun updateGroup(group: GroupEntity) {
        viewModelScope.launch {
            val result = groupRepository.updateGroup(group)
            _groupUpdateStatus.value = result
        }
    }

    fun removeMember(memberId: Int) {
        viewModelScope.launch {
            _groupDetailsState.update { it.copy(isLoading = true) }

            groupRepository.removeMemberFromGroup(memberId)
                .onSuccess {
                    _groupDetailsState.update {
                        it.copy(
                            isLoading = false,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    val errorMessage = when {
                        error.message?.contains("non-zero balance") == true ->
                            "Cannot remove member with outstanding balance"

                        else -> "Failed to remove member: ${error.message}"
                    }
                    _groupDetailsState.update {
                        it.copy(
                            isLoading = false,
                            error = errorMessage
                        )
                    }
                }
        }
    }

    suspend fun getGroupInviteLink(groupId: Int): Result<String> =
        groupRepository.getGroupInviteLink(groupId)

    fun generateProvisionalUserInviteLink(provisionalUserId: Int, emailOverride: String? = null) {
        viewModelScope.launch {
            _groupDetailsState.update { it.copy(isLoading = true) }

            try {
                // Update email if provided
                if (!emailOverride.isNullOrBlank()) {
                    userRepository.updateInvitationEmail(provisionalUserId, emailOverride)
                        .onFailure { error ->
                            Log.e(TAG, "Failed to update invitation email", error)
                            _groupDetailsState.update { currentState ->
                                currentState.copy(
                                    isLoading = false,
                                    error = "Failed to update email: ${error.message}"
                                )
                            }
                            return@launch
                        }
                }

                val currentGroup = _groupDetailsState.value.group
                val groupId = currentGroup?.id

                // Rest of your existing link generation code...
                val groupInviteCode = groupId?.let {
                    groupRepository.getGroupInviteLink(it).getOrNull()
                }

                val inviteUrl = buildString {
                    append("trego://users/invite/")
                    append("?userId=$provisionalUserId")
                    if (groupInviteCode != null) {
                        append("&groupCode=$groupInviteCode")
                    }
                }

                _groupDetailsState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        generatedInviteLink = inviteUrl,
                        error = null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating invite link", e)
                _groupDetailsState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = "Failed to generate invite link: ${e.message}"
                    )
                }
            }
        }
    }

    fun fetchUsernamesForInvitation(groupId: Int) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            try {
                val userId = getUserIdFromPreferences(context)
                if (userId != null) {
                    // First get current group members to exclude
                    val currentGroupMembers = groupRepository.getGroupMembers(groupId)
                        .first()
                        .map { it.userId }
                        .toSet()

                    // Then get all users from groups
                    groupRepository.getGroupsByUserId(userId)
                        .flatMapLatest { groups ->
                            if (groups.isEmpty()) {
                                flowOf(emptyList())
                            } else {
                                combine(
                                    groups.map { group ->
                                        groupRepository.getGroupMembers(group.id)
                                    }
                                ) { membersList ->
                                    membersList.toList().flatten()
                                }
                            }
                        }
                        .collect { allMembers ->
                            val uniqueUserIds = allMembers
                                .map { it.userId }
                                .distinct()
                                .filter { it !in currentGroupMembers } // Exclude current group members

                            userRepository.getUsersByIds(uniqueUserIds)
                                .collect { users ->
                                    _usernames.value = users.associateBy(
                                        { it.userId },
                                        { it.username }
                                    )
                                    _loading.value = false
                                }
                        }
                } else {
                    _error.value = "User ID not found"
                    _loading.value = false
                }
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Error fetching usernames", e)
                _error.value = e.message
                _loading.value = false
            }
        }
    }


    fun addMemberToGroup(groupId: Int, userId: Int) {
        viewModelScope.launch {
            _addMemberResult.value = groupRepository.addMemberToGroup(groupId, userId)
        }
    }

    fun resetAddMemberResult() {
        _addMemberResult.value = null
    }

    fun reloadGroupImage() {
        viewModelScope.launch {
            val currentGroup = _groupDetailsState.value.group
            if (currentGroup?.groupImg != null) {
                _groupDetailsState.update { currentState ->
                    currentState.copy(
                        imageLoadingState = ImageLoadingState.Loading,
                        groupImage = currentGroup.groupImg
                    )
                }

                try {
                    val imageUrl = ImageUtils.getFullImageUrl(currentGroup.groupImg)
                    _groupDetailsState.update { currentState ->
                        currentState.copy(
                            imageLoadingState = ImageLoadingState.Success
                        )
                    }
                } catch (e: Exception) {
                    _groupDetailsState.update { currentState ->
                        currentState.copy(
                            imageLoadingState = ImageLoadingState.Error(
                                e.message ?: "Failed to load group image"
                            )
                        )
                    }
                }
            }
        }
    }

    fun uploadGroupImage(groupId: Int, imageUri: Uri) {
        viewModelScope.launch {
            try {
                Log.d("GroupViewModel", "Starting image upload for group $groupId")
                _groupDetailsState.update {
                    it.copy(
                        uploadStatus = UploadStatus.Loading,
                        imageLoadingState = ImageLoadingState.Loading
                    )
                }

                val result = groupRepository.handleGroupImageUpload(groupId, imageUri)

                result.fold(
                    onSuccess = { uploadResult ->
                        val imagePath = uploadResult.serverPath
                        Log.d("GroupViewModel", "Image upload successful. Path: $imagePath")
                        _groupDetailsState.update { currentState ->
                            currentState.copy(
                                uploadStatus = UploadStatus.Success(
                                    imagePath = imagePath,
                                    message = uploadResult.message
                                ),
                                groupImage = imagePath,
                                imageLoadingState = ImageLoadingState.Success,
                                group = currentState.group?.copy(
                                    groupImg = imagePath
                                )
                            )
                        }
                        // Trigger a refresh of the group details to ensure we have the latest data
                        loadGroupDetails(groupId)
                    },
                    onFailure = { error ->
                        Log.e("GroupViewModel", "Image upload failed", error)
                        _groupDetailsState.update {
                            it.copy(
                                uploadStatus = UploadStatus.Error(error.message ?: "Upload failed"),
                                imageLoadingState = ImageLoadingState.Error(
                                    error.message ?: "Upload failed"
                                )
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Exception during image upload", e)
                _groupDetailsState.update {
                    it.copy(
                        uploadStatus = UploadStatus.Error(e.message ?: "Unknown error"),
                        imageLoadingState = ImageLoadingState.Error(e.message ?: "Unknown error")
                    )
                }
            }
        }
    }

    fun setGroupImageFromLocalPath(groupId: Int, localPath: String) {
        viewModelScope.launch {
            try {
                _groupDetailsState.update { it.copy(imageLoadingState = ImageLoadingState.Loading) }

                // Create a URI from the local path
                val imageFile = File(localPath)
                val imageUri = Uri.fromFile(imageFile)

                // Upload the image using existing method
                uploadGroupImage(groupId, imageUri)
            } catch (e: Exception) {
                _groupDetailsState.update {
                    it.copy(
                        imageLoadingState = ImageLoadingState.Error(e.message ?: "Failed to set image"),
                        error = e.message
                    )
                }
            }
        }
    }

    // Add this method to detect placeholder images
    private fun isPlaceholderImage(imagePath: String?): Boolean {
        return PlaceholderImageGenerator.isPlaceholderImage(imagePath ?: "")
    }

    fun regeneratePlaceholderImage(groupId: Int) {
        viewModelScope.launch {
            try {
                // First, get the latest group data from the repository to ensure we have the most current image path
                val latestGroup = groupRepository.getGroupById(groupId).firstOrNull()
                    ?: throw Exception("Group not found")

                Log.d("GroupViewModel", "Starting placeholder regeneration for groupId: $groupId")
                Log.d("GroupViewModel", "Initial group details state: ${groupDetailsState.value}")

                _groupDetailsState.update { it.copy(
                    uploadStatus = UploadStatus.Loading,
                    imageLoadingState = ImageLoadingState.Loading
                ) }

                // Always generate a new seed and pattern for regeneration
                // This will create a completely different image than before
                val placeholderReference = PlaceholderImageGenerator.generatePlaceholderReference(
                    groupId = groupId,
                    groupName = latestGroup.name,
                    useTimestamp = true  // This forces a new seed
                )

                // Verify it's in the correct format before proceeding
                if (!placeholderReference.startsWith("placeholder://")) {
                    Log.e("GroupViewModel", "Generated reference is not in the expected format: $placeholderReference")
                    throw IllegalStateException("Invalid placeholder reference generated")
                }

                Log.d("GroupViewModel", "Generated new reference: $placeholderReference")

                // Generate a local image for immediate display
                val localPath = PlaceholderImageGenerator.getImageForPath(context, placeholderReference)
                Log.d("GroupViewModel", "Local image path: $localPath")

                // First, update the database entry (this will be synced to the server later)
                val result = groupRepository.updateGroup(
                    latestGroup.copy(
                        groupImg = placeholderReference,
                        localImagePath = localPath,
                        imageLastModified = DateUtils.getCurrentTimestamp(),
                        updatedAt = DateUtils.getCurrentTimestamp(),
                        syncStatus = SyncStatus.PENDING_SYNC
                    )
                )
                Log.d("GroupViewModel", "Resulting group: $result")

                result.fold(
                    onSuccess = {
                        Log.d("GroupViewModel", "Group image updated on server: $placeholderReference")
                        Log.d("GroupViewModel", "Successfully set placeholder image: $placeholderReference")

                        // Update the state to reflect the new image
                        _groupDetailsState.update { currentState ->
                            currentState.copy(
                                uploadStatus = UploadStatus.Success(
                                    imagePath = placeholderReference,
                                    message = "Generated new image"
                                ),
                                groupImage = placeholderReference,  // Use the placeholder reference directly
                                imageLoadingState = ImageLoadingState.Success,
                                isPlaceholderImage = true,
                                imageUpdateTimestamp = System.currentTimeMillis(), // Force recomposition
                                group = currentState.group?.copy(
                                    groupImg = placeholderReference,
                                    localImagePath = localPath,
                                    imageLastModified = DateUtils.getCurrentTimestamp(),
                                    updatedAt = DateUtils.getCurrentTimestamp(),
                                    syncStatus = SyncStatus.PENDING_SYNC
                                )
                            )
                        }

                        // Force a full reload of group details with force refresh
                        loadGroupDetails(groupId, forceRefresh = true)

                        Log.d("GroupViewModel", "Final group details state: ${groupDetailsState.value}")

                        // Notify any listeners that the image has been updated
                        _imageUpdateEvent.value = System.currentTimeMillis()
                    },
                    onFailure = { error ->
                        Log.e("GroupViewModel", "Failed to set placeholder image", error)
                        _groupDetailsState.update {
                            it.copy(
                                uploadStatus = UploadStatus.Error(error.message ?: "Failed to set placeholder image"),
                                imageLoadingState = ImageLoadingState.Error(error.message ?: "Failed to set placeholder image")
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Error generating placeholder image", e)
                _groupDetailsState.update {
                    it.copy(
                        uploadStatus = UploadStatus.Error(e.message ?: "Unknown error"),
                        imageLoadingState = ImageLoadingState.Error(e.message ?: "Unknown error")
                    )
                }
            }
        }
    }

    fun updateImageLoadingState(newState: ImageLoadingState) {
        _groupDetailsState.update {
            it.copy(imageLoadingState = newState)
        }
    }

    sealed class UploadStatus {
        object Idle : UploadStatus()
        object Loading : UploadStatus()
        data class Success(val imagePath: String?, val message: String?) : UploadStatus()
        data class Error(val message: String) : UploadStatus()
    }

    fun fetchGroupBalances(groupId: Int) {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                val result = groupRepository.calculateGroupBalances(groupId)
                result.onSuccess { balances ->
                    _groupBalances.value = balances
                }.onFailure { error ->
                    _error.value = error.message
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun batchConvertCurrencies(groupId: Int) {
        viewModelScope.launch {
            val userId = getUserIdFromPreferences(context) ?: return@launch

            _groupDetailsState.update { it.copy(isConverting = true, conversionError = null) }

            paymentRepository.batchConvertGroupCurrencies(groupId, userId)
                .fold(
                    onSuccess = { result ->
                        _groupDetailsState.update { current ->
                            current.copy(
                                conversionResult = result,
                                isConverting = false
                            )
                        }
                        // Reload group details to refresh payment data
                        loadGroupDetails(groupId)
                    },
                    onFailure = { error ->
                        _groupDetailsState.update { current ->
                            current.copy(
                                conversionError = error.message,
                                isConverting = false
                            )
                        }
                    }
                )
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentLoadJob?.cancel()
    }


    fun archiveGroup(groupId: Int) {
        viewModelScope.launch {
            try {
                _archiveGroupState.value = ArchiveGroupState.Loading
                Log.d(TAG, "Starting group archive process for group $groupId")

                val result = groupRepository.archiveGroup(groupId)

                result.fold(
                    onSuccess = {
                        Log.d(TAG, "Successfully archived group $groupId")
                        _archiveGroupState.value = ArchiveGroupState.Success

                        // Refresh user groups list if being displayed
                        getUserIdFromPreferences(context)?.let { userId ->
                            loadUserGroupsList(userId, true)
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to archive group $groupId", error)
                        _archiveGroupState.value = ArchiveGroupState.Error(
                            error.message ?: "Unknown error occurred while archiving group"
                        )

                        _groupDetailsState.update { currentState ->
                            currentState.copy(
                                error = error.message,
                                isLoading = false
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception while archiving group $groupId", e)
                _archiveGroupState.value = ArchiveGroupState.Error(
                    e.message ?: "An unexpected error occurred"
                )

                _groupDetailsState.update { currentState ->
                    currentState.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun restoreGroup(groupId: Int) {
        viewModelScope.launch {
            try {
                _restoreGroupState.value = RestoreGroupState.Loading
                Log.d(TAG, "Starting group restore process for group $groupId")

                val result = groupRepository.restoreGroup(groupId)

                result.fold(
                    onSuccess = {
                        Log.d(TAG, "Successfully restored group $groupId")
                        _restoreGroupState.value = RestoreGroupState.Success

                        // Refresh user groups list if being displayed
                        getUserIdFromPreferences(context)?.let { userId ->
                            loadUserGroupsList(userId, true)
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to restore group $groupId", error)
                        _restoreGroupState.value = RestoreGroupState.Error(
                            error.message ?: "Unknown error occurred while restoring group"
                        )

                        _groupDetailsState.update { currentState ->
                            currentState.copy(
                                error = error.message,
                                isLoading = false
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception while restoring group $groupId", e)
                _restoreGroupState.value = RestoreGroupState.Error(
                    e.message ?: "An unexpected error occurred"
                )

                _groupDetailsState.update { currentState ->
                    currentState.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun resetArchiveGroupState() {
        _archiveGroupState.value = ArchiveGroupState.Idle
    }

    fun resetRestoreGroupState() {
        _restoreGroupState.value = RestoreGroupState.Idle
    }

    fun getCurrentMemberId(): Int? {
        val currentUserId = getUserIdFromPreferences(context) ?: return null
        return groupMembers.value.find { it.userId == currentUserId }?.id
    }

    fun checkCurrentUserBalance(groupId: Int? = null) {
        viewModelScope.launch {
            Log.d("GroupViewModel", "Starting checkCurrentUserBalance")

            // Use the provided groupId parameter, or fall back to the one in the state
            val effectiveGroupId = groupId ?: groupDetailsState.value.group?.id
            Log.d("GroupViewModel", "Current groupId: $effectiveGroupId")

            if (effectiveGroupId == null) {
                Log.e("GroupViewModel", "No groupId found, returning early")
                return@launch
            }

            // Load balances first
            groupRepository.calculateGroupBalances(effectiveGroupId)
                .onSuccess { balances ->
                    Log.d("GroupViewModel", "Successfully loaded balances: $balances")
                    _groupBalances.value = balances

                    // Now check user balance
                    val userId = getUserIdFromPreferences(context)
                    Log.d("GroupViewModel", "Retrieved userId: $userId")

                    if (userId == null) {
                        Log.e("GroupViewModel", "No userId found")
                        return@onSuccess
                    }

                    val userBalance = balances.find { it.userId == userId }
                    Log.d("GroupViewModel", "Found userBalance: $userBalance")

                    _currentUserBalance.value = userBalance
                    Log.d("GroupViewModel", "Updated _currentUserBalance to: ${_currentUserBalance.value}")
                }
                .onFailure { error ->
                    Log.e("GroupViewModel", "Failed to load balances", error)
                }
        }
    }

    fun fetchSettlingInstructions(groupId: Int) {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                groupRepository.calculateSettlingInstructions(groupId)
                    .onSuccess { instructions ->
                        _settlingInstructions.value = instructions
                    }
                    .onFailure { error ->
                        _error.value = error.message
                    }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    suspend fun joinGroupByInvite(inviteCode: String): Result<Int> {
        return try {
            val userId = getUserIdFromPreferences(context) ?:
            return Result.failure(Exception("User not logged in"))

            val result = groupRepository.joinGroupByInvite(inviteCode, userId)
            result.onSuccess { groupId ->
                loadGroupDetails(groupId)
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Load the default splits for a group
    fun loadGroupDefaultSplits(groupId: Int) {
        viewModelScope.launch {
            try {
                _defaultSplitOperationState.value = OperationState.Loading
                groupRepository.getGroupDefaultSplits(groupId).collect { splits ->
                    _groupDefaultSplits.value = splits
                    _defaultSplitOperationState.value = OperationState.Success
                }
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Error loading default splits", e)
                _defaultSplitOperationState.value = OperationState.Error(e.message ?: "Failed to load default splits")
            }
        }
    }

    // Create or update a single default split
    fun createOrUpdateDefaultSplit(defaultSplit: GroupDefaultSplitEntity) {
        viewModelScope.launch {
            try {
                _defaultSplitOperationState.value = OperationState.Loading

                groupRepository.createOrUpdateDefaultSplit(defaultSplit)
                    .onSuccess { split ->
                        // Update the list with the new/updated split
                        val currentSplits = _groupDefaultSplits.value.toMutableList()
                        val index = currentSplits.indexOfFirst { it.id == split.id }

                        if (index >= 0) {
                            currentSplits[index] = split
                        } else {
                            currentSplits.add(split)
                        }

                        _groupDefaultSplits.value = currentSplits
                        _defaultSplitOperationState.value = OperationState.Success
                    }
                    .onFailure { error ->
                        _defaultSplitOperationState.value = OperationState.Error(
                            error.message ?: "Failed to save default split"
                        )
                    }
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Error saving default split", e)
                _defaultSplitOperationState.value = OperationState.Error(e.message ?: "Failed to save default split")
            }
        }
    }

    // Update all default splits for a group
    fun updateGroupDefaultSplits(groupId: Int, splits: List<GroupDefaultSplitEntity>) {
        viewModelScope.launch {
            try {
                _defaultSplitOperationState.value = OperationState.Loading

                groupRepository.updateGroupDefaultSplits(groupId, splits)
                    .onSuccess { updatedSplits ->
                        _groupDefaultSplits.value = updatedSplits
                        _defaultSplitOperationState.value = OperationState.Success
                    }
                    .onFailure { error ->
                        _defaultSplitOperationState.value = OperationState.Error(
                            error.message ?: "Failed to update default splits"
                        )
                    }
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Error updating default splits", e)
                _defaultSplitOperationState.value = OperationState.Error(e.message ?: "Failed to update default splits")
            }
        }
    }

    // Delete all default splits for a group
    fun deleteAllGroupDefaultSplits(groupId: Int) {
        viewModelScope.launch {
            try {
                _defaultSplitOperationState.value = OperationState.Loading

                groupRepository.deleteGroupDefaultSplits(groupId)
                    .onSuccess {
                        _groupDefaultSplits.value = emptyList()
                        _defaultSplitOperationState.value = OperationState.Success
                    }
                    .onFailure { error ->
                        _defaultSplitOperationState.value = OperationState.Error(
                            error.message ?: "Failed to delete default splits"
                        )
                    }
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Error deleting default splits", e)
                _defaultSplitOperationState.value = OperationState.Error(e.message ?: "Failed to delete default splits")
            }
        }
    }

    // Delete a specific default split
    fun deleteDefaultSplit(groupId: Int, splitId: Int) {
        viewModelScope.launch {
            try {
                _defaultSplitOperationState.value = OperationState.Loading

                groupRepository.deleteGroupDefaultSplit(groupId, splitId)
                    .onSuccess {
                        // Remove the split from our local list
                        val currentSplits = _groupDefaultSplits.value.toMutableList()
                        currentSplits.removeIf { it.id == splitId }
                        _groupDefaultSplits.value = currentSplits

                        _defaultSplitOperationState.value = OperationState.Success
                    }
                    .onFailure { error ->
                        _defaultSplitOperationState.value = OperationState.Error(
                            error.message ?: "Failed to delete split"
                        )
                    }
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Error deleting split", e)
                _defaultSplitOperationState.value = OperationState.Error(e.message ?: "Failed to delete split")
            }
        }
    }

    // Manually trigger sync of pending default splits
    fun syncPendingDefaultSplits() {
        viewModelScope.launch {
            try {
                _defaultSplitOperationState.value = OperationState.Loading

                groupRepository.syncPendingDefaultSplits()
                    .onSuccess {
                        // Reload splits after sync
                        _groupDefaultSplits.value.firstOrNull()?.groupId?.let { groupId ->
                            loadGroupDefaultSplits(groupId)
                        }
                        _defaultSplitOperationState.value = OperationState.Success
                    }
                    .onFailure { error ->
                        _defaultSplitOperationState.value = OperationState.Error(
                            error.message ?: "Failed to sync default splits"
                        )
                    }
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Error syncing default splits", e)
                _defaultSplitOperationState.value = OperationState.Error(e.message ?: "Failed to sync default splits")
            }
        }
    }

    // Reset operation state
    fun resetDefaultSplitOperationState() {
        _defaultSplitOperationState.value = OperationState.Idle
    }
}
