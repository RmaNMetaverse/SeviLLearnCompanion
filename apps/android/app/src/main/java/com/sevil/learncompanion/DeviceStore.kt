package com.sevil.learncompanion

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

class DeviceStore(context: Context): SQLiteOpenHelper(context,"sevil-device.sqlite3",null,1) {
    val files=File(context.filesDir,"workspace-files").apply { mkdirs() }
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE records (id TEXT PRIMARY KEY, kind TEXT NOT NULL, payload TEXT NOT NULL)") }
    override fun onUpgrade(db: SQLiteDatabase,old: Int,new: Int) {}
    @Synchronized fun save(kind: String,value: JSONObject): JSONObject {
        if(!value.has("id")) value.put("id",UUID.randomUUID().toString())
        if(!value.has("created_at")) value.put("created_at",Instant.now().toString())
        writableDatabase.insertWithOnConflict("records",null,ContentValues().apply { put("id",value.getString("id"));put("kind",kind);put("payload",value.toString()) },SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) { "Cannot save to the device database. Check available storage." } }
        return value
    }
    @Synchronized fun get(kind: String,id: String): JSONObject = readableDatabase.query("records",arrayOf("payload"),"kind=? AND id=?",arrayOf(kind,id),null,null,null).use { c -> if(c.moveToFirst()) JSONObject(c.getString(0)) else error("${kind.replaceFirstChar { it.uppercase() }} not found") }
    @Synchronized fun all(kind: String): List<JSONObject> = readableDatabase.query("records",arrayOf("payload"),"kind=?",arrayOf(kind),null,null,"rowid DESC").use { c -> buildList { while(c.moveToNext()) add(JSONObject(c.getString(0))) } }
    @Synchronized fun remove(id: String) { writableDatabase.delete("records","id=?",arrayOf(id)) }
    fun assets(session: JSONObject)=all("asset").filter { it.optString("session_id")==session.getString("id") || (it.optString("kind")=="resource" && it.optString("course_id")==session.getString("course_id")) }
    fun file(id: String): File { require(id.matches(Regex("[A-Za-z0-9-]+"))); return File(files,id) }
    fun invalidate(session: JSONObject) { save("session",session.put("status","draft").put("handout",JSONObject.NULL).put("error",JSONObject.NULL).put("sources",org.json.JSONArray())) }
}
