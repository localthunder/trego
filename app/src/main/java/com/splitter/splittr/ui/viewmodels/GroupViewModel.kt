package com.splitter.splittr.ui.viewmodels

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import com.splitter.splittr.data.extensions.toModel
import com.splitter.splittr.data.local.DataClasses.UserGroupListItem
import com.splitter.splittr.data.local.entities.GroupMemberEntity
import com.splitter.splittr.data.repositories.GroupRepository
import com.splitter.splittr.data.repositories.PaymentRepository
import com.splitter.splittr.data.repositories.UserRepository
import com.splitter.splittr.data.model.Group
import com.splitter.splittr.data.model.GroupMember
import com.splitter.splittr.data.model.Payment
import com.splitter.splittr.ui.screens.UserBalanceWithCurrency
import com.splitter.splittr.utils.ImageUtils
import com.splitter.splittr.utils.getUserIdFromPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GroupViewModel(
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository,
    private val paymentRepository: PaymentRepository,
    private val context: Context
) : ViewModel() {

    data class GroupDetailsState(
        val group: Group? = null,
        val groupMembers: List<GroupMember> = emptyList(),
        val usernames: Map<Int, String> = emptyMap(),
        val payments: List<Payment> = emptyList(),
        val groupImage: String? = null,
        val uploadStatus: UploadStatus = UploadStatus.Idle,
        val isLoading: Boolean = false,
        val error: String? = null,
        val imageLoadingState: ImageLoadingState = ImageLoadingState.Idle,
        val localImagePath: String? = null,
        val isDownloadingImage: Boolean = false
    )

    // Add this sealed class inside your GroupViewModel
    sealed class ImageLoadingState {
        object Idle : ImageLoadingState()
        object Loading : ImageLoadingState()
        object Success : ImageLoadingState()
        data class Error(val message: String) : ImageLoadingState()
    }


    private val _groupDetailsState = MutableStateFlow(GroupDetailsState())
    val groupDetailsState: StateFlow<GroupDetailsState> = _groupDetailsState.asStateFlow()

    private val _groupCreationStatus = MutableLiveData<Result<Pair<Group, GroupMember>>>()
    val groupCreationStatus: LiveData<Result<Pair<Group, GroupMember>>> = _groupCreationStatus

    private val _groupUpdateStatus = MutableLiveData<Result<Group>>()
    val groupUpdateStatus: LiveData<Result<Group>> = _groupUpdateStatus

    private val _payments = MutableStateFlow<List<Payment>>(emptyList())
    val sortedPayments: StateFlow<List<Payment>> = _payments
        .map { payments -> payments.sortedByDescending { it.paymentDate } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _usernames = MutableLiveData<Map<Int, String>>()
    val usernames: LiveData<Map<Int, String>> = _usernames

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _addMemberResult = MutableLiveData<Result<GroupMember>?>()
    val addMemberResult: LiveData<Result<GroupMember>?> = _addMemberResult

    private val _userGroups = MutableStateFlow<List<Group>>(emptyList())
    val userGroups: StateFlow<List<Group>> = _userGroups.asStateFlow()

    private val _group = MutableStateFlow<Group?>(null)
    val group: StateFlow<Group?> = _group

    private val _groupMembers = MutableStateFlow<List<GroupMember>>(emptyList())
    val groupMembers: StateFlow<List<GroupMember>> = _groupMembers.asStateFlow()

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

    fun loadGroupDetails(groupId: Int) {
        viewModelScope.launch {
            _groupDetailsState.update { it.copy(isLoading = true, error = null) }
            try {
                // Use coroutines to load data concurrently
                val groupDeferred = async { loadGroup(groupId) }
                val membersDeferred = async { loadGroupMembers(groupId) }
                val paymentsDeferred = async { loadPayments(groupId) }

                // Wait for all data to be loaded
                groupDeferred.await()
                membersDeferred.await()
                paymentsDeferred.await()
            } catch (e: Exception) {
                Log.e("GroupViewModel", "Error loading group details", e)
                _groupDetailsState.update { it.copy(error = e.message) }
            } finally {
                _groupDetailsState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadGroup(groupId: Int) {
        groupRepository.getGroupById(groupId).collect { group ->
            _groupDetailsState.update { currentState ->
                currentState.copy(
                    group = group ?: currentState.group,
                    groupImage = group?.groupImg ?: currentState.groupImage,
                    // Reset loading state when loading new group
                    imageLoadingState = if (group?.groupImg != null) {
                        ImageLoadingState.Loading
                    } else {
                        ImageLoadingState.Idle
                    }
                )
            }

            // If group has an image, update the loading state
            if (group?.groupImg != null) {
                try {
                    val imageUrl = ImageUtils.getFullImageUrl(group.groupImg)
                    Log.d("GroupViewModel", "Loading group image from: $imageUrl")

                    _groupDetailsState.update { currentState ->
                        currentState.copy(
                            imageLoadingState = ImageLoadingState.Success,
                            groupImage = group.groupImg
                        )
                    }
                } catch (e: Exception) {
                    Log.e("GroupViewModel", "Error loading group image", e)
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

    private suspend fun loadGroupMembers(groupId: Int) {
        groupRepository.getGroupMembers(groupId).collect { members ->
            _groupDetailsState.update { currentState ->
                val newMembers = members.map { it.toModel() }
                currentState.copy(
                    groupMembers = if (newMembers.isNotEmpty()) newMembers else currentState.groupMembers
                )
            }
            fetchUsernamesFromRepository(members.map { it.userId })
        }
    }

    private suspend fun loadPayments(groupId: Int) {
        paymentRepository.getPaymentsByGroup(groupId)
            .collect { paymentEntities ->
                val newPayments = paymentEntities.map { it.toModel() }
                if (newPayments != _payments.value) {
                    _payments.value = newPayments
                    _groupDetailsState.update { currentState ->
                        currentState.copy(payments = newPayments)
                    }
                }
            }
    }

    fun loadUserGroupsList(userId: Int) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                groupRepository.getGroupListItems(userId)
                    .collect { groups ->
                        _userGroupItems.value = groups
                        _loading.value = false
                    }
            } catch (e: Exception) {
                _error.value = e.message
                _loading.value = false
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

    suspend fun getGroupById(groupId: Int): Flow<Group?> = groupRepository.getGroupById(groupId)


    fun loadUserGroups(userId: Int) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                groupRepository.getGroupsByUserId(userId)
                    .collect { groups ->
                        _userGroups.value = groups.map { it.toModel() }
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
                    _groupMembers.value = members.map { it.toModel() }
                }
        }
    }

    fun createGroup(group: Group, creatorUserId: Int) {
        viewModelScope.launch {
            val result = groupRepository.createGroupWithMember(group, creatorUserId)
            _groupCreationStatus.value = result
        }
    }

    fun updateGroup(group: Group) {
        viewModelScope.launch {
            val result = groupRepository.updateGroup(group)
            _groupUpdateStatus.value = result
        }
    }

    fun removeMemberFromGroup(groupId: Int, userId: Int) {
        viewModelScope.launch {
            groupRepository.removeMemberFromGroup(groupId, userId)
        }
    }

    suspend fun getGroupInviteLink(groupId: Int): Result<String> =
        groupRepository.getGroupInviteLink(groupId)

//    fun syncGroups() {
//        viewModelScope.launch {
//            groupRepository.syncGroups()
//        }
//    }

    fun fetchUsernamesForInvitation(groupId: Int) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val userId = getUserIdFromPreferences(context)
                if (userId != null) {
                    groupRepository.getGroupsByUserId(userId)
                        .flatMapLatest { groups ->
                            if (groups.isEmpty()) {
                                flowOf(emptyList<GroupMemberEntity>())
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
                            val uniqueUserIds = allMembers.map { it.userId }.distinct()
                            userRepository.getUsersByIds(uniqueUserIds)
                                .collect { users ->
                                    _usernames.value = users.associateBy({ it.userId }, { it.username })
                                    _loading.value = false
                                }
                        }
                } else {
                    _error.value = "User ID not found"
                    _loading.value = false
                }
            } catch (e: Exception) {
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
                _groupDetailsState.update { it.copy(
                    uploadStatus = UploadStatus.Loading,
                    imageLoadingState = ImageLoadingState.Loading
                ) }

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
                                imageLoadingState = ImageLoadingState.Error(error.message ?: "Upload failed")
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
            val result = groupRepository.calculateGroupBalances(groupId)
            if (result.isSuccess) {
                _groupBalances.value = result.getOrDefault(emptyList())
            } else {
                // Handle error
            }
        }
    }
}
