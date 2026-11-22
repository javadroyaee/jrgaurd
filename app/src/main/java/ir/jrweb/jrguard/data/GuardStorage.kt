package ir.jrweb.jrguard.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppVisit(
    val packageName: String,
    val appLabel: String,
    val durationSec: Long
)

data class UnlockSession(
    val id: String,
    val timestampMs: Long,
    val formattedTime: String,
    val photoPath: String?,
    val colleagueTag: String,
    val visits: List<AppVisit>
)

object GuardStorage {
    private const val PREFS_NAME = "jrguard_prefs"
    private const val KEY_HOST_URL = "host_ingest_url"
    private const val KEY_API_TOKEN = "host_api_token"
    private const val LOGS_FILE = "unlock_sessions.json"

    fun getSessionsDir(context: Context): File {
        val dir = File(context.filesDir, "jrguard_sessions")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun formatTimestamp(ms: Long): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd - HH:mm:ss", Locale("fa"))
        return sdf.format(Date(ms))
    }

    fun saveSession(context: Context, session: UnlockSession) {
        val all = loadSessions(context).toMutableList()
        all.add(0, session)
        val arr = JSONArray()
        all.take(150).forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("timestampMs", s.timestampMs)
                put("formattedTime", s.formattedTime)
                put("photoPath", s.photoPath ?: "")
                put("colleagueTag", s.colleagueTag)
                val vArr = JSONArray()
                s.visits.forEach { v ->
                    vArr.put(JSONObject().apply {
                        put("packageName", v.packageName)
                        put("appLabel", v.appLabel)
                        put("durationSec", v.durationSec)
                    })
                }
                put("visits", vArr)
            }
            arr.put(obj)
        }
        File(getSessionsDir(context), LOGS_FILE).writeText(arr.toString(), Charsets.UTF_8)
    }

    fun loadSessions(context: Context): List<UnlockSession> {
        val file = File(getSessionsDir(context), LOGS_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val vArr = obj.optJSONArray("visits") ?: JSONArray()
                val visits = (0 until vArr.length()).map { j ->
                    val vo = vArr.getJSONObject(j)
                    AppVisit(
                        packageName = vo.optString("packageName"),
                        appLabel = vo.optString("appLabel"),
                        durationSec = vo.optLong("durationSec", 0L)
                    )
                }
                UnlockSession(
                    id = obj.optString("id"),
                    timestampMs = obj.optLong("timestampMs"),
                    formattedTime = obj.optString("formattedTime"),
                    photoPath = obj.optString("photoPath").ifBlank { null },
                    colleagueTag = obj.optString("colleagueTag", "نامشخص"),
                    visits = visits
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun updateColleagueTag(context: Context, sessionId: String, tag: String) {
        val updated = loadSessions(context).map {
            if (it.id == sessionId) it.copy(colleagueTag = tag) else it
        }
        val arr = JSONArray()
        updated.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("timestampMs", s.timestampMs)
                put("formattedTime", s.formattedTime)
                put("photoPath", s.photoPath ?: "")
                put("colleagueTag", s.colleagueTag)
                val vArr = JSONArray()
                s.visits.forEach { v ->
                    vArr.put(JSONObject().apply {
                        put("packageName", v.packageName)
                        put("appLabel", v.appLabel)
                        put("durationSec", v.durationSec)
                    })
                }
                put("visits", vArr)
            }
            arr.put(obj)
        }
        File(getSessionsDir(context), LOGS_FILE).writeText(arr.toString(), Charsets.UTF_8)
    }

    fun collectRecentAppVisits(context: Context, startMs: Long, endMs: Long): List<AppVisit> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val events = usm.queryEvents(startMs, endMs)
        val event = UsageEvents.Event()
        val activeStart = mutableMapOf<String, Long>()
        val totals = mutableMapOf<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg == context.packageName) continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    activeStart[pkg] = event.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = activeStart.remove(pkg)
                    if (start != null && event.timeStamp > start) {
                        totals[pkg] = (totals[pkg] ?: 0L) + ((event.timeStamp - start) / 1000L)
                    }
                }
            }
        }
        activeStart.forEach { (pkg, start) ->
            if (endMs > start) {
                totals[pkg] = (totals[pkg] ?: 0L) + ((endMs - start) / 1000L)
            }
        }

        val pm = context.packageManager
        return totals.entries
            .filter { it.value > 0L }
            .sortedByDescending { it.value }
            .map { (pkg, sec) ->
                val label = try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    pkg
                }
                AppVisit(packageName = pkg, appLabel = label, durationSec = sec)
            }
    }

    fun setHostConfig(context: Context, url: String, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_HOST_URL, url.trim())
            .putString(KEY_API_TOKEN, token.trim())
            .apply()
    }

    fun getHostUrl(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_HOST_URL, "") ?: ""

    fun getApiToken(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API_TOKEN, "") ?: ""
}
