package kr.koasis.travelyunnan.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kr.koasis.travelyunnan.MainActivity
import kr.koasis.travelyunnan.R
import kr.koasis.travelyunnan.TravelApp
import kr.koasis.travelyunnan.data.json.TripNotification
import kotlinx.coroutines.flow.first
import kr.koasis.travelyunnan.domain.TRIP_ZONE
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** 일정 알림(S4-4). data/notifications.json 의 시각(현지)을 AlarmManager 에 등록한다. */
object TripNotifier {
    const val CHANNEL = "trip_alerts"
    private const val EXTRA_ID = "id"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_BODY = "body"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "여행 일정 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "열차·픽업·빵차·공항 출발 알림"
                }
            )
        }
    }

    fun hasPostPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    fun epochMillis(at: String): Long = ZonedDateTime.of(LocalDateTime.parse(at), TRIP_ZONE).toInstant().toEpochMilli()

    /** 전체 재등록: 끈 항목과 지난 항목은 취소한다. */
    fun registerAll(ctx: Context, items: List<TripNotification>, disabled: Set<String>) {
        ensureChannel(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        items.forEach { n ->
            val pi = pending(ctx, n)
            val t = epochMillis(n.at)
            if (n.id in disabled || t <= now) {
                am.cancel(pi); return@forEach
            }
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        }
    }

    private fun pending(ctx: Context, n: TripNotification): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_ID, n.id).putExtra(EXTRA_TITLE, n.title).putExtra(EXTRA_BODY, n.body)
        return PendingIntent.getBroadcast(ctx, n.id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun show(ctx: Context, id: String, title: String, body: String) {
        if (!hasPostPermission(ctx)) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(open)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id.hashCode(), n)
    }

    class AlarmReceiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            show(ctx, intent.getStringExtra(EXTRA_ID) ?: "n", intent.getStringExtra(EXTRA_TITLE) ?: "여행 알림", intent.getStringExtra(EXTRA_BODY) ?: "")
        }
    }

    /** 재부팅·시간 변경 후 재등록 */
    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val app = ctx.applicationContext as? TravelApp ?: return
            val pending = goAsync()
            Thread {
                runCatching {
                    val data = app.container.tripData
                    val disabled = kotlinx.coroutines.runBlocking { app.container.settings.settings.first() }.disabledNotifications
                    registerAll(ctx, data.notifications, disabled)
                }
                pending.finish()
            }.start()
        }
    }
}
