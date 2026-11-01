package com.sevil.learncompanion
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionTutorTest {
    @Test fun linksAndHistoryStayLocalToTheirSession(): Unit=runBlocking {
        val api=Api(InstrumentationRegistry.getInstrumentation().targetContext)
        val c=JSONObject(api.json("/v1/courses","POST",JSONObject().put("title","Synthetic tutor course")))
        suspend fun session()=JSONObject(api.json("/v1/sessions","POST",JSONObject().put("course_id",c.getString("id")).put("title","Synthetic session").put("recording_permission",true))).getString("id")
        val a=session();val b=session()
        val link=JSONObject(api.json("/v1/sessions/$a/links","POST",JSONObject().put("title","Reading").put("url","https://example.edu/reading").put("text","Momentum is mass times velocity.")))
        assertEquals(1,JSONArray(api.json("/v1/sessions/$a/links")).length());assertEquals(0,JSONArray(api.json("/v1/sessions/$b/links")).length())
        api.workspace.store.save("message",JSONObject().put("session_id",a).put("role","user").put("content","Saved question").put("status","complete"))
        assertEquals(1,JSONArray(api.json("/v1/sessions/$a/chat")).length());assertEquals(0,JSONArray(api.json("/v1/sessions/$b/chat")).length())
        api.json("/v1/sessions/$a/chat","DELETE");assertEquals(0,JSONArray(api.json("/v1/sessions/$a/chat")).length())
        try {api.json("/v1/sessions/$b/links","POST",JSONObject().put("title","Bad").put("url","javascript:alert(1)"));fail("Unsafe link accepted")} catch(_:IllegalArgumentException) {}
        api.json("/v1/sessions/$a","DELETE");assertFalse(api.workspace.store.all("link").any {it.getString("id")==link.getString("id")});api.json("/v1/sessions/$b","DELETE")
    }
    @Test fun evidenceIsBoundedAndUnsupportedSourcesFail() {
        val text="x".repeat(60000)+" angular momentum conservation "+"y".repeat(60000)
        val excerpt=SessionTutor.excerpt(text,"angular momentum",5000);assertTrue(excerpt.contains("angular momentum"));assertTrue(excerpt.length<=5000)
        try {SessionTutor.validate(JSONObject().put("answer","A reply").put("citations",JSONArray().put("invented")).put("links",JSONArray()),JSONArray());fail("Invented source accepted")} catch(_:IllegalArgumentException) {}
    }
}
