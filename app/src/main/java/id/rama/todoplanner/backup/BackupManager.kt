package id.rama.todoplanner.backup

import id.rama.todoplanner.data.ActivityEntity
import id.rama.todoplanner.data.TargetEntity
import org.json.JSONArray
import org.json.JSONObject

/** Backup lokal: seluruh data <-> satu file JSON. Tanpa server, tanpa cloud. */
object BackupManager {

    fun exportJson(activities: List<ActivityEntity>, targets: List<TargetEntity>): String {
        val root = JSONObject()
        root.put("app", "TodoPlanner")
        root.put("version", 1)
        val arrA = JSONArray()
        activities.forEach { a ->
            arrA.put(JSONObject().apply {
                put("title", a.title); put("dateEpochDay", a.dateEpochDay)
                put("startMinutes", a.startMinutes ?: JSONObject.NULL)
                put("endMinutes", a.endMinutes ?: JSONObject.NULL)
                put("template", a.template); put("repeatRule", a.repeatRule)
                put("reminderMinutesBefore", a.reminderMinutesBefore ?: JSONObject.NULL)
                put("ringtoneUri", a.ringtoneUri ?: JSONObject.NULL)
                put("ringtoneName", a.ringtoneName ?: JSONObject.NULL)
                put("priority", a.priority); put("category", a.category)
                put("note", a.note); put("doneDates", a.doneDates)
                // RONDE 5: interval Daily Reminder + timestamp selesai per occurrence
                put("intervalValue", a.intervalValue ?: JSONObject.NULL)
                put("intervalUnit", a.intervalUnit ?: JSONObject.NULL)
                put("doneTimestamps", a.doneTimestamps)
                // RONDE 6: logo emoji kegiatan (backup lama tidak punya field ini)
                put("iconEmoji", a.iconEmoji)
                // RONDE 7: daftar penandaan selesai Daily Reminder per hari
                put("dailyDoneLog", a.dailyDoneLog)
            })
        }
        root.put("activities", arrA)
        val arrT = JSONArray()
        targets.forEach { t ->
            arrT.put(JSONObject().apply {
                put("title", t.title); put("target", t.target); put("current", t.current)
                put("quarter", t.quarter); put("year", t.year)
            })
        }
        root.put("targets", arrT)
        return root.toString(2)
    }

    fun importJson(text: String): Pair<List<ActivityEntity>, List<TargetEntity>> {
        val root = JSONObject(text)
        val activities = ArrayList<ActivityEntity>()
        val arrA = root.optJSONArray("activities") ?: JSONArray()
        for (i in 0 until arrA.length()) {
            val o = arrA.getJSONObject(i)
            activities.add(
                ActivityEntity(
                    title = o.optString("title", "Kegiatan"),
                    dateEpochDay = o.optLong("dateEpochDay", 0),
                    startMinutes = if (o.isNull("startMinutes")) null else o.optInt("startMinutes"),
                    endMinutes = if (o.isNull("endMinutes")) null else o.optInt("endMinutes"),
                    template = o.optString("template", "Harian"),
                    repeatRule = o.optString("repeatRule", "NONE"),
                    reminderMinutesBefore = if (o.isNull("reminderMinutesBefore")) null else o.optInt("reminderMinutesBefore"),
                    ringtoneUri = if (o.isNull("ringtoneUri")) null else o.optString("ringtoneUri"),
                    ringtoneName = if (o.isNull("ringtoneName")) null else o.optString("ringtoneName"),
                    priority = o.optString("priority", "Sedang"),
                    category = o.optString("category", "Kerja"),
                    note = o.optString("note", ""),
                    doneDates = o.optString("doneDates", ""),
                    // RONDE 5: backup LAMA tidak punya field ini -> null/"" (aman).
                    intervalValue = if (o.isNull("intervalValue")) null else o.optInt("intervalValue"),
                    intervalUnit = if (o.isNull("intervalUnit")) null else o.optString("intervalUnit"),
                    doneTimestamps = o.optString("doneTimestamps", ""),
                    // RONDE 6: backup LAMA tidak punya field ini -> "" (tanpa logo, aman).
                    iconEmoji = o.optString("iconEmoji", ""),
                    // RONDE 7: backup LAMA tidak punya field ini -> "" (belum ada penandaan, aman).
                    dailyDoneLog = o.optString("dailyDoneLog", "")
                )
            )
        }
        val targets = ArrayList<TargetEntity>()
        val arrT = root.optJSONArray("targets") ?: JSONArray()
        for (i in 0 until arrT.length()) {
            val o = arrT.getJSONObject(i)
            targets.add(
                TargetEntity(
                    title = o.optString("title", "Target"),
                    target = o.optInt("target", 1), current = o.optInt("current", 0),
                    quarter = o.optInt("quarter", 1), year = o.optInt("year", 2026)
                )
            )
        }
        return activities to targets
    }
}
