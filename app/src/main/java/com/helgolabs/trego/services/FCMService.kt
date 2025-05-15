package com.helgolabs.trego.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.helgolabs.trego.MainActivity
import com.helgolabs.trego.MyApplication
import com.helgolabs.trego.R
import com.helgolabs.trego.utils.getUserIdFromPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FCMService : FirebaseMessagingService() {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val myApplication by lazy {
        applicationContext as MyApplication
    }

    override fun onCreate() {
        super.onCreate()
        // Check if we have a user and token but no device token record
        checkAndRegisterToken()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Get repository from your application class
        val notificationRepository = myApplication.notificationRepository
        val userId = getUserIdFromPreferences(applicationContext)

        if (userId != null) {
            scope.launch {
                notificationRepository.registerDeviceToken(token, userId)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Create notification channel for Android O and above
        createNotificationChannel()

        // Create the notification builder
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(message.data["title"])
            .setContentText(message.data["body"])
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // Extract entity information from the deepLink and trigger sync
        val deepLink = message.data["deepLink"] ?: return
        handleDeepLinkSync(deepLink)

        // Add deep linking if needed
        message.data["deepLink"]?.let { link ->
            val intent = Intent(this, MainActivity::class.java).apply {
                data = Uri.parse(link)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent =
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

            notificationBuilder.setContentIntent(pendingIntent)
        }

        // Show the notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            System.currentTimeMillis().toInt(),
            notificationBuilder.build()
        )
    }

    private fun createNotificationChannel() {
        val name = "Trego Notifications"
        val descriptionText = "Receive notifications about group activities and expenses"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun checkAndRegisterToken() {
        scope.launch {
            val userId = getUserIdFromPreferences(applicationContext)
            if (userId != null) {
                // Get current token and register it
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        scope.launch {
                            try {
                                val notificationRepository = myApplication.notificationRepository
                                notificationRepository.registerDeviceToken(task.result!!, userId)
                            } catch (e: Exception) {
                                Log.e("FCMService", "Failed to register token", e)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleDeepLinkSync(deepLink: String) {

        val groupRepository = myApplication.groupRepository
        val paymentRepository = myApplication.paymentRepository

        // Parse the deepLink to extract entity types and IDs
        val uri = Uri.parse(deepLink)

        // Skip if URI can't be parsed or doesn't match expected pattern
        if (uri.scheme != "trego") return

        myApplication.applicationScope.launch {
            // Always sync group data first, since most we want the most recently updated group on top
            groupRepository.sync()

            when (uri.host) {
                "paymentDetails" -> {
                    // Format: trego://paymentDetails/{groupId}/{paymentId}
                    paymentRepository.sync()
                }
                "groupDetails" -> {
                    // Format: trego://groupDetails/{groupId}
                    // Already synced group, so do nothing
                }
                "groupSettings" -> {
                    // Format: trego://groupSettings/{groupId}
                    // Already synced group, so do nothing
                }
                "settleUp" -> {
                    // Format: trego://settleUp/{groupId}
                    paymentRepository.sync()
                }
                // Add other deepLink types as needed
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "trego_notifications"
    }
}