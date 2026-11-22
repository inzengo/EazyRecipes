package nl.eazyrecipes.cook

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nl.eazyrecipes.MainActivity
import nl.eazyrecipes.R
import nl.eazyrecipes.container

/**
 * Houdt kookwekkers in leven als je de app verlaat of het scherm uitgaat: een voorgrond-service met een lopende
 * melding (aftellend), een wake-lock zolang er een timer loopt, en een luide melding als hij afgaat.
 * De service volgt [KitchenTimers]; zijn er geen timers meer, dan stopt hij zichzelf.
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val timers get() = container.timers
    private var wakeLock: PowerManager.WakeLock? = null
    private var ringtone: Ringtone? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
        try {
            ServiceCompat.startForeground(
                this, RUNNING_ID, runningNotification(timers.timers.value),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        } catch (e: IllegalStateException) {
            // Android 12+: geen voorgrond-start toegestaan vanuit de achtergrond. De app start ons weer bij openen.
            stopSelf()
            return
        }
        scope.launch {
            timers.timers.collectLatest { list ->
                if (list.isEmpty()) {
                    stopSelf()
                    return@collectLatest
                }
                alarmSound(list.any { it.ringing })
                notify(RUNNING_ID, runningNotification(list))
                // Wachten tot de eerstvolgende afloopt; een wijziging in de lijst breekt dit af (collectLatest).
                // In een lus: delay() telt uptime, de timers wandkloktijd; verschuift de klok, dan gewoon opnieuw wachten.
                while (true) {
                    val next = timers.nextEnd() ?: break
                    holdWakeLock(next)
                    delay((next - System.currentTimeMillis()).coerceAtLeast(0))
                    val due = timers.markDue()
                    if (due.isNotEmpty()) {
                        due.forEach { notify(it.id.hashCode(), doneNotification(it)) }
                        break
                    }
                }
                if (timers.nextEnd() == null) holdWakeLock(null)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> intent.getStringExtra(EXTRA_ID)?.let { dismiss(this, it) }
            ACTION_DISMISS_RINGING -> timers.timers.value.filter { it.ringing }.forEach { dismiss(this, it.id) }
        }
        // Niet sticky: een herstart vanuit de achtergrond mag op Android 12+ niet. MainActivity start ons weer.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        alarmSound(false)
        holdWakeLock(null)
        super.onDestroy()
    }

    /** Houdt de CPU wakker tot [until] (+ marge), zodat de timer bij een uit-scherm niet te laat afgaat. `null` = loslaten. */
    private fun holdWakeLock(until: Long?) {
        if (until == null) {
            wakeLock?.takeIf { it.isHeld }?.release()
            wakeLock = null
            return
        }
        val lock = wakeLock ?: (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EazyRecipes:kookwekker").also { it.setReferenceCounted(false); wakeLock = it }
        lock.acquire((until - System.currentTimeMillis()).coerceAtLeast(0) + WAKE_MARGIN_MS)
    }

    /**
     * Het alarm zelf: los van meldingen, zodat je het ook hoort als meldingen uit staan.
     * Blijft klinken (in een lus) tot alle afgegane timers zijn weggetikt.
     */
    private fun alarmSound(on: Boolean) {
        if (on && ringtone?.isPlaying != true) {
            ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                if (Build.VERSION.SDK_INT >= 28) isLooping = true
                play()
            }
        } else if (!on) {
            ringtone?.stop()
            ringtone = null
        }
    }

    private fun notify(id: Int, n: Notification) {
        // Zonder meldingstoestemming (Android 13+) lopen de timers gewoon door; alleen de melding blijft weg.
        runCatching { NotificationManagerCompat.from(this).notify(id, n) }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun dismissIntent(id: String): PendingIntent = PendingIntent.getService(
        this, id.hashCode(), Intent(this, TimerService::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun runningNotification(list: List<KitchenTimer>): Notification {
        val running = list.filter { !it.ringing }.sortedBy { it.endAt }
        val first = running.firstOrNull()
        return NotificationCompat.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ph_timer)
            .setContentTitle(first?.label ?: "Kookwekker")
            .setContentText(
                when {
                    first == null -> "Timer afgegaan"
                    running.size == 1 -> "Loopt · ${StepTimers.label(first.seconds)}"
                    else -> "En nog ${running.size - 1} andere timer${if (running.size > 2) "s" else ""}"
                }
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .apply {
                if (first != null) setWhen(first.endAt).setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
                if (list.any { it.ringing }) addAction(0, "Stoppen", PendingIntent.getService(
                    this@TimerService, 0, Intent(this@TimerService, TimerService::class.java).setAction(ACTION_DISMISS_RINGING),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ))
            }
            .build()
    }

    private fun doneNotification(t: KitchenTimer): Notification =
        NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ph_timer)
            .setContentTitle("Timer klaar · ${StepTimers.label(t.seconds)}")
            .setContentText(t.label)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(openApp())
            .setDeleteIntent(dismissIntent(t.id))
            .addAction(0, "Stoppen", dismissIntent(t.id))
            // Niet weg bij aantikken (dat opent de app): het alarm stopt pas met "Stoppen" of wegvegen.
            .setAutoCancel(false)
            .build()

    companion object {
        private const val CHANNEL_RUNNING = "timers_running"
        /** Stil kanaal: het geluid komt van de service zelf (ook zonder meldingen), dit is alleen de melding. */
        private const val CHANNEL_DONE = "timer_alarm"
        private const val RUNNING_ID = 4711
        private const val ACTION_DISMISS = "nl.eazyrecipes.timer.DISMISS"
        private const val ACTION_DISMISS_RINGING = "nl.eazyrecipes.timer.DISMISS_RINGING"
        private const val EXTRA_ID = "id"
        private const val WAKE_MARGIN_MS = 2 * 60 * 1000L

        /** Start de service als er timers zijn (na het starten van een timer, of bij het openen van de app). */
        fun ensureRunning(context: Context) {
            if (context.container.timers.timers.value.isEmpty()) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java)) }
        }

        /** Een timer wegtikken vanuit de app: ook de "klaar"-melding weghalen. */
        fun dismiss(context: Context, id: String) {
            context.container.timers.dismiss(id)
            NotificationManagerCompat.from(context).cancel(id.hashCode())
        }

        private fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_RUNNING, "Lopende timers", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Laat zien hoe lang een kookwekker nog loopt."
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, "Timer klaar", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Melding als een kookwekker klaar is (het alarmgeluid speelt de app zelf)."
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 400, 600)
                }
            )
        }
    }
}
