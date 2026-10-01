package com.max.assistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telephony.TelephonyManager

class CallSmsReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        try {
            when (intent.action) {

                TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                    handlePhoneState(context, intent)
                }

                Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                    handleSms(context, intent)
                }
            }
        } catch (_: Exception) {
            // Receiver must never crash the process because of a
            // malformed broadcast or unavailable telephony data.
        }
    }

    private fun handlePhoneState(
        context: Context,
        intent: Intent
    ) {
        val state =
            intent.getStringExtra(
                TelephonyManager.EXTRA_STATE
            )

        if (state != TelephonyManager.EXTRA_STATE_RINGING) {
            return
        }

        /*
         * Incoming-number access can be restricted when the required
         * phone-state permission is not granted.
         */
        val number =
            if (
                context.checkSelfPermission(
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                intent.getStringExtra(
                    TelephonyManager.EXTRA_INCOMING_NUMBER
                )
            } else {
                null
            }

        val safeNumber =
            number
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "unknown"

        WakeService.announceCall(
            context,
            safeNumber
        )
    }

    private fun handleSms(
        context: Context,
        intent: Intent
    ) {
        /*
         * SMS_RECEIVED is delivered only when RECEIVE_SMS has
         * been granted. Still check it here so the receiver
         * fails safely if the permission state changes.
         */
        if (
            context.checkSelfPermission(
                Manifest.permission.RECEIVE_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val messages =
            try {
                Telephony.Sms.Intents
                    .getMessagesFromIntent(intent)
            } catch (_: Exception) {
                emptyArray()
            }

        if (messages.isEmpty()) {
            return
        }

        val sender =
            messages
                .firstNotNullOfOrNull {
                    it.originatingAddress
                        ?.trim()
                        ?.takeIf { value ->
                            value.isNotEmpty()
                        }
                }
                ?: "unknown"

        val body =
            messages
                .mapNotNull {
                    it.messageBody
                }
                .joinToString(" ")
                .trim()

        if (body.isEmpty()) {
            return
        }

        /*
         * Keep announcements bounded so a very large/malformed
         * SMS cannot create an unnecessarily long TTS request.
         */
        val safeBody =
            body.take(500)

        WakeService.announceSms(
            context,
            sender,
            safeBody
        )
    }
}
