package com.neochildclinic.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.neochildclinic.domain.repository.ReminderRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderRepository: ReminderRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val reminderId = intent.getStringExtra("reminderId")

        when (action) {
            "ACTION_DISMISS" -> {
                if (reminderId != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        reminderRepository.markCompleted(reminderId)
                        reminderRepository.triggerImmediateCheck()
                    }
                }
            }
        }
    }
}
