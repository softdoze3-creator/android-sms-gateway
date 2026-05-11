package com.smsgateway.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.smsgateway.api.RetrofitClient
import kotlinx.coroutines.*

/**
 * Handles SMS_SENT and SMS_DELIVERED broadcasts from SmsManager
 * and reports back to the WordPress server.
 */
class DeliveryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val queueId   = intent.getIntExtra("queue_id", 0)
        val serverUrl = intent.getStringExtra("server_url") ?: return
        val deviceKey = intent.getStringExtra("device_key") ?: ""

        when (intent.action) {
            "SMS_SENT" -> {
                val status = if (resultCode == Activity.RESULT_OK) "sent" else "failed"
                val error  = when (resultCode) {
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
                    SmsManager.RESULT_ERROR_NO_SERVICE      -> "No service"
                    SmsManager.RESULT_ERROR_NULL_PDU        -> "Null PDU"
                    SmsManager.RESULT_ERROR_RADIO_OFF       -> "Radio off"
                    else -> if (resultCode != Activity.RESULT_OK) "Unknown error ($resultCode)" else ""
                }
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        RetrofitClient.get(serverUrl).updateStatus(deviceKey, queueId, status, error)
                    } catch (_: Exception) {}
                }
            }
            "SMS_DELIVERED" -> {
                val status = if (resultCode == Activity.RESULT_OK) "delivered" else "failed"
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        RetrofitClient.get(serverUrl).deliveryReport(queueId, status)
                    } catch (_: Exception) {}
                }
            }
        }
    }
}

/** Restart the service automatically after device reboot */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            com.smsgateway.utils.Prefs.init(context)
            if (com.smsgateway.utils.Prefs.deviceKey.isNotEmpty()) {
                context.startForegroundService(
                    Intent(context, SmsPollingService::class.java)
                )
            }
        }
    }
}
