package ru.ffl24.direct

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.work.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.ffl24.direct.data.mysql.*
import ru.ffl24.direct.domain.AuthResult
import ru.ffl24.direct.local.WorkspacePreferences
import ru.ffl24.direct.notifications.*

/** A real PendingIntent owns its activity/task; ActivityScenario must not own that handoff. */
class ConfiguredNotificationTest {
    @Test fun realWorkManagerPollsMysqlDeduplicatesAndOpensSecondRoute() = runScenario(false)

    /** Fixture only: the host must independently click the real OS notification and verify the screen. */
    @Test fun prepareSystemNotificationForNativeHostTap() = runScenario(true)

    private fun runScenario(hostTap: Boolean) {
        val db = OwnedTestDatabase()
        if(hostTap) check(db.restored && db.driver==2000000001L && db.notification==2000000004L)
        val context = db.context
        val graph = (context.applicationContext as DirectApplication).graph
        val local = WorkspacePreferences(context)
        runBlocking {
            assertTrue(graph.authRepository.login(db.driver, db.password.toCharArray()) is AuthResult.Success)
            local.setDate(db.date); local.clearSeen(); local.setNotifications(false)
        }
        val manager = WorkManager.getInstance(context)
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        SecondRouteNotifications.cancel(context)
        if(!hostTap) context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun waitFor(timeout: Long = 60_000, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + timeout
            while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(150)
            assertTrue("Timed out waiting for an owned local runtime condition", condition())
        }
        fun texts(node: AccessibilityNodeInfo?): String {
            if (node == null) return ""
            val result = buildString {
                append(node.text ?: ""); append('\n')
                for (i in 0 until node.childCount) append(texts(node.getChild(i)))
            }
            @Suppress("DEPRECATION") node.recycle()
            return result
        }
        fun visibleText() = texts(db.instrumentation.uiAutomation.rootInActiveWindow)
        if(!hostTap) waitFor { visibleText().contains(if (db.restored) "FFL E2E Driver" else "Fixture driver") }
        // Exercise the actual worker once at a time, independently of 15-minute scheduling.
        runBlocking { local.setNotifications(true) }
        fun poll() {
            val work = OneTimeWorkRequestBuilder<SecondRouteWorker>().setInputData(workDataOf("driver" to db.driver)).build()
            manager.enqueue(work).result.get(30, java.util.concurrent.TimeUnit.SECONDS)
            waitFor { manager.getWorkInfoById(work.id).get()?.state?.isFinished == true }
            assertEquals(WorkInfo.State.SUCCEEDED, manager.getWorkInfoById(work.id).get()?.state)
        }
        try {
            poll()
            waitFor(10_000) { notifications.activeNotifications.isEmpty() }
            val scope = "${db.driver}:${db.date}"
            assertTrue(runBlocking { local.seen(scope) } == setOf("reys_den:${db.order}"))
            MySqlConnectionFactory().open(db.jdbc, ConnectionPolicy()).use { connection ->
                connection.isReadOnly = false
                connection.prepareStatement("UPDATE reys_den SET st_vv=0,date_att_log=NULL WHERE id=? AND id_v=?").use {
                    it.setLong(1, db.notification); it.setLong(2, db.driver); assertEquals(1, it.executeUpdate())
                }
            }
            poll()
            waitFor(10_000) { notifications.activeNotifications.size == 1 }
            val posted = notifications.activeNotifications.single()
            assertEquals("Новые заказы второго рейса", posted.notification.extras.getString(android.app.Notification.EXTRA_TITLE))
            assertTrue(posted.notification.extras.getString(android.app.Notification.EXTRA_TEXT).orEmpty().startsWith("Новых заказов: 1."))
            assertTrue(runBlocking { local.seen(scope) } == setOf("reys_den:${db.order}", "reys_den:${db.notification}"))
            poll()
            assertEquals(posted.postTime, notifications.activeNotifications.single().postTime)
            if(hostTap) return // Setup success is not a notification-tap E2E claim.
            posted.notification.contentIntent.send()
            try {
                waitFor { visibleText().contains("Второй рейс") }
                waitFor { visibleText().contains(if (db.restored) "FFL-E2E-NOTIFICATION" else "Notification-fixture") }
            } catch (failure: AssertionError) {
                val directory=java.io.File(context.filesDir,"notification-diagnostics").apply{mkdirs()}
                java.io.File(directory,"native-text-private.txt").writeText(visibleText())
                java.io.File(directory,"screen-private.png").outputStream().use {
                    db.instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)
                }
                val activityState=org.json.JSONObject()
                db.instrumentation.runOnMainSync {
                    val activities=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    activityState.put("resumed_count",activities.size)
                    activityState.put("resumed_main",activities.any{it is MainActivity})
                    val models=org.json.JSONArray()
                    fun inspect(value:Any,key:String,depth:Int) {
                        if(depth>8)return
                        if(value is ru.ffl24.direct.presentation.WorkspaceViewModel) {
                            val read=value.javaClass.getDeclaredField("read").apply{isAccessible=true}.get(value) as? kotlinx.coroutines.Job
                            models.put(org.json.JSONObject().put("second",key.startsWith("second-"))
                                .put("state",value.state.value?.javaClass?.simpleName?:"null")
                                .put("refreshing",value.refreshing.value).put("read_active",read?.isActive?:false)
                                .put("read_cancelled",read?.isCancelled?:false))
                        } else if(value is androidx.lifecycle.ViewModelStore || value.javaClass.name.endsWith("NavControllerViewModel")) {
                            value.javaClass.declaredFields.filter{java.util.Map::class.java.isAssignableFrom(it.type)}.forEach {field->
                                field.isAccessible=true
                                (field.get(value) as? Map<*,*>)?.forEach{(k,v)->if(v!=null)inspect(v,k.toString(),depth+1)}
                            }
                        }
                    }
                    activities.filterIsInstance<MainActivity>().forEach{inspect(it.viewModelStore,"activity",0)}
                    activityState.put("workspace_models",models)
                    activities.filterIsInstance<MainActivity>().firstOrNull()?.let {
                        activityState.put("tap_extra_present",it.intent.hasExtra(SecondRouteNotifications.TAP_DRIVER))
                        activityState.put("notification_action",it.intent.action=="ru.ffl24.direct.SECOND_ROUTE.${db.driver}")
                    }
                }
                java.io.File(directory,"activity-state.json").writeText(activityState.toString())
                throw failure
            }
            if (db.restored) {
                val directory = java.io.File(context.filesDir, "restored-private").apply { mkdirs() }
                java.io.File(directory, "controlled-second-route.png").outputStream().use {
                    db.instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        } finally {
            runBlocking { local.setNotifications(false) }
            if(!hostTap) SecondRouteNotifications.cancel(context)
        }
    }
}
