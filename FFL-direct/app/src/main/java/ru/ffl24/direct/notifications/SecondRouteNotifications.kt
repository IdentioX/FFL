package ru.ffl24.direct.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import ru.ffl24.direct.DirectApplication
import ru.ffl24.direct.MainActivity
import ru.ffl24.direct.R
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.local.WorkspacePreferences

object SecondRouteNotifications {
    const val WORK = "ffl-direct-second-route"
    const val CHANNEL = "second_route_v1"
    const val TAP_DRIVER = "ffl_notification_driver"
    private const val ID = 1001
    fun schedule(context: Context, driver: Long) {
        val work = PeriodicWorkRequestBuilder<SecondRouteWorker>(15,TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("driver" to driver)).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK,ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,work)
    }
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ID)
    }
    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) &&
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()
    fun post(context: Context, driver: Long, count: Int) {
        if(!allowed(context)) return
        val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel(CHANNEL,"Второй рейс",NotificationManager.IMPORTANCE_DEFAULT))
        val intent=Intent(context,MainActivity::class.java).putExtra(TAP_DRIVER,driver)
            .setAction("ru.ffl24.direct.SECOND_ROUTE.$driver")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(ID,NotificationCompat.Builder(context,CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Новые заказы второго рейса")
            .setContentText("Новых заказов: $count. Откройте приложение для проверки.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(pending).setAutoCancel(true).build())
    }
}

class SecondRouteWorker(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val graph=(applicationContext as DirectApplication).graph
        val local=WorkspacePreferences(applicationContext,graph.profileScope)
        if(!local.notifications() || !SecondRouteNotifications.allowed(applicationContext)) return Result.success()
        val expected=inputData.getLong("driver",-1)
        if(expected<=0) return Result.failure()
        try {
            val current=graph.sessions.current()
            val access=if(current.session==null) graph.authRepository.restore() else graph.authRepository.checkAccess()
            if(access is AuthResult.Failed) return Result.failure() // bounded polling, no automatic retries
            val session=(access as? AuthResult.Success)?.value
            if (access == AuthResult.DriverDisabled || access == AuthResult.DriverMissing || (access is AuthResult.Success && session == null)) {
                WorkManager.getInstance(applicationContext).cancelWorkById(id)
                return Result.success()
            }
            if(session?.driverId!=expected) return Result.success() // never cancel a replacement driver's unique work
            val snapshot=graph.sessions.current()
            val date=local.date()
            val result=graph.workspaceRepository.summary(date)
            if(result==DetailsResult.DriverDisabled || result==DetailsResult.SessionRequired) {
                if (graph.sessions.current().session == null) WorkManager.getInstance(applicationContext).cancelWorkById(id)
                return Result.success()
            }
            val summary=(result as? DetailsResult.Success)?.value ?: return Result.failure()
            if((applicationContext as DirectApplication).graph !== graph || graph.sessions.current()!=snapshot || !local.notifications() || date!=local.date()) return Result.success()
            val scope="$expected:$date"
            // Persist before posting: never duplicate a delivered notification after process death.
            // A crash between persistence and notify may miss a notification; UI refresh remains authoritative.
            val fresh=local.observeSecondRoute(scope,summary.secondIds)
            if((applicationContext as DirectApplication).graph === graph && graph.sessions.current()==snapshot && local.notifications() && date==local.date() && fresh.isNotEmpty())
                SecondRouteNotifications.post(applicationContext,expected,fresh.size)
            return Result.success()
        } catch(e: CancellationException) { throw e }
        catch(_: Exception) { return Result.failure() }
    }
}
