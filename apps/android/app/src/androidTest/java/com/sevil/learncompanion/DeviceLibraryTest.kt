package com.sevil.learncompanion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.graphics.Bitmap

@RunWith(AndroidJUnit4::class)
class DeviceLibraryTest {
    @Test fun independentLibraryRetainsOriginalsAndExportsPDF(): Unit = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val api=Api(context)
        val course=JSONObject(api.json("/v1/courses","POST",JSONObject().put("title","Synthetic physics")))
        val session=JSONObject(api.json("/v1/sessions","POST",JSONObject().put("title","Offline lecture").put("course_id",course.getString("id")).put("recording_permission",true)))
        val id=session.getString("id")
        val resource=File(context.cacheDir,"synthetic-chapter.txt").apply { writeText("Synthetic textbook extract") }
        api.upload(id,resource,"chapter.txt","resource",12500,0)
        val saved=JSONObject(api.json("/v1/sessions/$id")).getJSONArray("assets").getJSONObject(0)
        assertEquals(12500,saved.getInt("offset_ms"))
        assertEquals(resource.readText(),api.assetFile(saved.getString("id")).readText())
        val reopened=DeviceStore(context)
        assertEquals("Offline lecture",reopened.get("session",id).getString("title"));reopened.close()
        val board=File(context.cacheDir,"synthetic-board.png")
        val bitmap=Bitmap.createBitmap(80,60,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
        board.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        api.upload(id,board,"board.png","photo",12500,0)
        val photo=JSONObject(api.json("/v1/sessions/$id")).getJSONArray("assets").objects().first { it.getString("kind")=="photo" }
        assertEquals(12500,photo.getInt("offset_ms"))
        api.json("/v1/sessions/$id/demo","POST",JSONObject())
        val pdfFile=File(context.cacheDir,"synthetic.pdf").apply { writeBytes(api.bytes("/v1/sessions/$id/handout.pdf")) }
        PdfRenderer(ParcelFileDescriptor.open(pdfFile,ParcelFileDescriptor.MODE_READ_ONLY)).use { assertTrue(it.pageCount>=2) }
        api.keys.save("gemini","")
        try { api.json("/v1/sessions/$id/generate","POST",JSONObject().put("provider","gemini"));fail("Missing key should fail before network") } catch(e:IllegalArgumentException) { assertTrue(e.message!!.contains("Settings")) }
        api.json("/v1/sessions/$id","DELETE")
        assertFalse(api.assetFile(photo.getString("id")).exists())
        assertTrue(api.assetFile(saved.getString("id")).exists()) // Course resource survives session deletion.
        api.json("/v1/assets/${saved.getString("id")}","DELETE")
        resource.delete();pdfFile.delete();board.delete()
    }
    @Test fun deviceKeysAreEncryptedAndCanBeCleared() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val keys=DeviceKeys(context)
        keys.save("gemini","synthetic-not-a-secret")
        assertEquals("synthetic-not-a-secret",DeviceKeys(context).read("gemini"))
        assertFalse(context.getSharedPreferences("sevil-device-keys",0).all.values.any { it.toString().contains("synthetic-not-a-secret") })
        keys.save("gemini","");assertEquals("",keys.read("gemini"))
    }
}
