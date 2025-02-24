package com.helgolabs.trego.data.managers

import android.app.Activity
import android.content.IntentSender
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages in-app updates using the Play Core library.
 * Supports both immediate (forced) and flexible update flows.
 */
class InAppUpdateManager(private val activity: Activity) {

    companion object {
        private const val TAG = "InAppUpdateManager"
        const val DAYS_FOR_FLEXIBLE_UPDATE = 1 // Days to wait before suggesting a flexible update
        const val DAYS_FOR_IMMEDIATE_UPDATE = 7 // Days to wait before requiring an immediate update
        const val HIGH_PRIORITY_UPDATE = 4 // Priority level for critical updates
    }

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    private val _updateProgress = MutableStateFlow(0)
    val updateProgress: StateFlow<Int> = _updateProgress

    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                val bytesDownloaded = state.bytesDownloaded()
                val totalBytesToDownload = state.totalBytesToDownload()
                val progress = if (totalBytesToDownload > 0) {
                    (bytesDownloaded * 100 / totalBytesToDownload).toInt()
                } else {
                    0
                }
                _updateProgress.value = progress
                Log.d(TAG, "Download progress: $progress%")
            }
            InstallStatus.DOWNLOADED -> {
                Log.d(TAG, "Download completed")
                _updateProgress.value = 100
                // We don't automatically call completeUpdate() here anymore
                // Instead, we'll show a UI prompt to the user when ready to install
            }
            InstallStatus.INSTALLED -> {
                Log.d(TAG, "Installation completed")
                // Update completed successfully
                _updateProgress.value = 0
            }
            InstallStatus.FAILED -> {
                Log.e(TAG, "Installation failed")
                _updateProgress.value = 0
            }
            else -> {
                Log.d(TAG, "Install status: ${state.installStatus()}")
            }
        }
    }

    init {
        appUpdateManager.registerListener(installStateUpdatedListener)
    }

    /**
     * Check if an update is available and prompt user based on update type
     *
     * @param updateType AppUpdateType.IMMEDIATE for forced updates or AppUpdateType.FLEXIBLE for optional updates
     * @param onUpdateAvailable Callback when update is available but not initiated yet
     * @param onNoUpdateAvailable Callback when no update is available
     */
    fun checkForUpdates(
        updateType: Int = AppUpdateType.FLEXIBLE,
        onUpdateAvailable: (AppUpdateInfo) -> Unit = {},
        onNoUpdateAvailable: () -> Unit = {}
    ) {
        Log.d(TAG, "Checking for updates...")

        // Using the Kotlin extension
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            Log.d(TAG, "Update availability: ${appUpdateInfo.updateAvailability()}")
            Log.d(TAG, "Available version code: ${appUpdateInfo.availableVersionCode()}")
            Log.d(TAG, "Is update allowed: ${appUpdateInfo.isUpdateTypeAllowed(updateType)}")
            Log.d(TAG, "Update staleness days: ${appUpdateInfo.clientVersionStalenessDays() ?: 0}")
            Log.d(TAG, "Update priority: ${appUpdateInfo.updatePriority()}")

            when {
                // Case 1: Critical update (high priority)
                appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                        && appUpdateInfo.updatePriority() >= HIGH_PRIORITY_UPDATE
                        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> {
                    Log.d(TAG, "Critical update available")
                    onUpdateAvailable(appUpdateInfo)
                }

                // Case 2: Update is very stale, require immediate update
                appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                        && (appUpdateInfo.clientVersionStalenessDays() ?: 0) >= DAYS_FOR_IMMEDIATE_UPDATE
                        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> {
                    Log.d(TAG, "Stale update available, suggesting immediate update")
                    onUpdateAvailable(appUpdateInfo)
                }

                // Case 3: Standard update, allow flexible update
                appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                        && (appUpdateInfo.clientVersionStalenessDays() ?: 0) >= DAYS_FOR_FLEXIBLE_UPDATE
                        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                    Log.d(TAG, "Update available, suggesting flexible update")
                    onUpdateAvailable(appUpdateInfo)
                }

                // Case 4: Update available but not required yet
                appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE -> {
                    Log.d(TAG, "Update available but not suggesting yet")
                    onNoUpdateAvailable()
                }

                // No update available
                else -> onNoUpdateAvailable()
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to check for updates", e)
            onNoUpdateAvailable()
        }
    }

    /**
     * Start the update flow with a specific update type using the activity result launcher
     *
     * @param appUpdateInfo The AppUpdateInfo from the check
     * @param updateType AppUpdateType.IMMEDIATE for forced updates or AppUpdateType.FLEXIBLE for optional updates
     * @param activityResultLauncher The launcher registered with registerForActivityResult
     * @param allowAssetPackDeletion Whether to allow deleting asset packs if device storage is limited
     * @return True if update started successfully, false otherwise
     */
    fun startUpdate(
        appUpdateInfo: AppUpdateInfo,
        updateType: Int,
        activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>,
        allowAssetPackDeletion: Boolean = false
    ): Boolean {
        return try {
            // Build update options with the new builder pattern
            val updateOptions = AppUpdateOptions.newBuilder(updateType)
                .setAllowAssetPackDeletion(allowAssetPackDeletion)
                .build()

            // Use the new API for startUpdateFlowForResult which is designed to work with the activity result API
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                updateOptions,
                0  // This requestCode is now unused with the new Activity Result API
            )

            true
        } catch (e: IntentSender.SendIntentException) {
            Log.e(TAG, "Failed to start update flow", e)
            false
        }
    }

    /**
     * Resume any updates that were in progress if the activity was restarted
     *
     * @param activityResultLauncher The launcher registered with registerForActivityResult
     */
    fun resumeUpdates(activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            // If there's a downloaded update waiting to be installed - we don't automatically call completeUpdate()
            // Instead, we should check for this state and let the UI decide when to prompt the user
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                Log.d(TAG, "An update has been downloaded and is waiting to be installed")
                // UI should call completeUpdate() when the user consents
            }

            // If there was an update in progress but the activity was restarted
            // (e.g., device rotation), we need to resume the IMMEDIATE update
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                Log.d(TAG, "Resuming update that was interrupted")
                try {
                    val updateOptions = AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()

                    // Use the existing API that works with the Activity directly
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        activity,
                        updateOptions,
                        0  // This requestCode is now unused with the new Activity Result API
                    )
                } catch (e: IntentSender.SendIntentException) {
                    Log.e(TAG, "Failed to resume update flow", e)
                }
            }
        }
    }

    /**
     * Check if there's a downloaded update waiting to be installed
     *
     * @param onUpdateDownloaded Callback when an update is downloaded and waiting
     */
    fun checkIfUpdateDownloaded(onUpdateDownloaded: () -> Unit) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                onUpdateDownloaded()
            }
        }
    }

    /**
     * Complete the update installation (should be called after user consents)
     */
    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }

    /**
     * Call this when the component is being destroyed to prevent memory leaks
     */
    fun cleanup() {
        appUpdateManager.unregisterListener(installStateUpdatedListener)
    }
}