package ir.jrweb.jrguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import ir.jrweb.jrguard.service.GuardSessionService

class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            val svcIntent = Intent(context, GuardSessionService::class.java).apply {
                putExtra("unlock_time", System.currentTimeMillis())
            }
            ContextCompat.startForegroundService(context, svcIntent)
        }
    }
}
