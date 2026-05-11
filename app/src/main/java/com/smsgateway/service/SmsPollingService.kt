package com.smsgateway.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import com.smsgateway.api.PendingSms
import com.smsgateway.api.RetrofitClient
import com.smsgateway.utils.Prefs
import kotlinx.coroutines.*

class SmsPollingService : Service() {

    companion object {
        const val CHANNEL_ID    = "asg_service"
        const val NOTIF_ID      = 1001
        const val ACTION_STOP   = "com.smsgateway.STOP"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Connecting…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startPolling()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        Prefs.isConnected = false
        super.onDestroy()
    }

    // ── Polling loop ──────────────────────────────────────────────────────────

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            val api = RetrofitClient.get(Prefs.serverUrl)
            val key = Prefs.deviceKey

            while (isActive) {
                try {
                    // 1. Heartbeat
                    api.heartbeat(key, Build.MODEL)

                    // 2. Fetch pending SMS
                    val resp = api.pendingSms(key)
                    if (resp.isSuccessful) {
                        val list = resp.body()?.data ?: emptyList()
                        updateNotification("Online · ${list.size} pending")
                        Prefs.isConnected = true
                        list.forEach { sms -> sendSms(sms, api, key) }
                    }
                } catch (e: Exception) {
                    Prefs.isConnected = false
                    updateNotification("Reconnecting…")
                }

                delay(Prefs.pollingInterval * 1000L)
            }
        }
    }

    // ── SMS send via SmsManager ───────────────────────────────────────────────

    private suspend fun sendSms(sms: PendingSms, api: com.smsgateway.api.GatewayApi, key: String) {
        withContext(Dispatchers.Main) {
            try {
                val manager = getSmsManager(sms.simSlot)

                // Pending intents for sent / delivery
                val sentIntent = android.app.PendingIntent.getBroadcast(
                    this@SmsPollingService,
                    sms.id,
                    Intent(this@SmsPollingService, DeliveryReceiver::class.java).apply {
                        action = "SMS_SENT"
                        putExtra("queue_id", sms.id)
                        putExtra("device_key", key)
                        putExtra("server_url", Prefs.serverUrl)
                    },
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                val deliveryIntent = android.app.PendingIntent.getBroadcast(
                    this@SmsPollingService,
                    sms.id + 10000,
                    Intent(this@SmsPollingService, DeliveryReceiver::class.java).apply {
                        action = "SMS_DELIVERED"
                        putExtra("queue_id", sms.id)
                        putExtra("server_url", Prefs.serverUrl)
                    },
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                // Split long messages automatically
                val parts = manager.divideMessage(sms.message)
                val sentIntents    = ArrayList<android.app.PendingIntent>().apply { if (parts.size > 1) repeat(parts.size) { add(sentIntent) } }
                val deliveryIntents = ArrayList<android.app.PendingIntent>().apply { if (parts.size > 1) repeat(parts.size) { add(deliveryIntent) } }

                if (parts.size > 1) {
                    manager.sendMultipartTextMessage(sms.receiver, null, parts, sentIntents, deliveryIntents)
                } else {
                    manager.sendTextMessage(sms.receiver, null, sms.message, sentIntent, deliveryIntent)
                }
            } catch (e: Exception) {
                // Report failure back to server
                withContext(Dispatchers.IO) {
                    try { api.updateStatus(key, sms.id, "failed", e.message ?: "Send error") } catch (_: Exception) {}
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getSmsManager(simSlot: Int): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val subIds = android.telephony.SubscriptionManager.from(this)
                .activeSubscriptionInfoList?.map { it.subscriptionId } ?: emptyList()
            val subId = subIds.getOrNull(simSlot) ?: SmsManager.getDefaultSmsSubscriptionId()
            SmsManager.getSmsManagerForSubscriptionId(subId)
        } else {
            SmsManager.getDefault()
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "SMS Gateway Service", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Keeps SMS Gateway running in background"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, SmsPollingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Gateway")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }
}
