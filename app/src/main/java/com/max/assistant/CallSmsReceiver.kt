package com.max.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.provider.Telephony

class CallSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "unknown"
                if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                    WakeService.announceCall(context, number)
                }
            }
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isNotEmpty()) {
                    val sender = messages[0].originatingAddress ?: "unknown"
                    val body = messages.joinToString(" ") { it.messageBody ?: "" }
                    WakeService.announceSms(context, sender, body)
                }
            }
        }
    }
}
