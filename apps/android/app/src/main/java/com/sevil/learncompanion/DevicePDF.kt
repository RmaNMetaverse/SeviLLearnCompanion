package com.sevil.learncompanion

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import org.json.JSONObject
import java.io.ByteArrayOutputStream

object DevicePDF {
    fun render(session: JSONObject,course: JSONObject,assets: List<JSONObject>,store: DeviceStore): ByteArray {
        val pdf=PdfDocument();var page: PdfDocument.Page?=null;var number=0;var y=48f
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(35,59,56);typeface=Typeface.create("sans-serif",Typeface.NORMAL);textSize=11f }
        fun finish() { page?.let { val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=9f;color=Color.GRAY };it.canvas.drawText("SeviL · $number",48f,817f,p);pdf.finishPage(it) };page=null }
        fun next() { finish();number++;page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,number).create());y=48f }
        fun paragraph(text: String,size: Float=11f,bold: Boolean=false) {
            if(text.isEmpty()) return
            paint.textSize=size;paint.typeface=Typeface.create("sans-serif",if(bold) Typeface.BOLD else Typeface.NORMAL)
            val layout=StaticLayout.Builder.obtain(text,0,text.length,paint,499).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(3f,1f).setIncludePad(false).build()
            var start=0
            while(start<layout.lineCount) {
                if(page==null) next()
                val top=layout.getLineTop(start);var end=start
                while(end<layout.lineCount && layout.getLineBottom(end)-top<=790-y) end++
                if(end==start) { next();continue }
                val bottom=layout.getLineBottom(end-1);val canvas=page!!.canvas
                canvas.save();canvas.translate(48f,y-top);canvas.clipRect(0,top,499,bottom);layout.draw(canvas);canvas.restore();y+=bottom-top+9;start=end
                if(start<layout.lineCount) next()
            }
        }
        next();val note=session.getJSONObject("handout")
        paragraph("SeviL / LECTURE NOTES",10f,true);paragraph(note.getString("title"),23f,true)
        paragraph(course.getString("title")+" · "+course.optString("university")+" · "+course.optString("professor"),9f)
        paragraph("Generated with: "+session.optString("generated_provider"),9f);paragraph(note.getString("summary"))
        paragraph("Learning objectives",16f,true);note.getJSONArray("objectives").strings().forEach { paragraph("• $it") }
        val refs=session.getJSONArray("sources").objects().associate { it.getString("id") to it.getString("label") }
        note.getJSONArray("sections").objects().forEach { s -> paragraph(s.getString("heading"),16f,true);paragraph(s.getString("kind").uppercase(),9f);paragraph(s.getString("body"));s.getJSONArray("citations").strings().forEach { paragraph(refs[it] ?: it,9f) } }
        paragraph("Bilingual glossary",16f,true);note.getJSONArray("glossary").objects().forEach { paragraph(it.getString("term")+" / "+it.getString("translation"),12f,true);paragraph(it.getString("definition")) }
        paragraph("Review questions",16f,true);note.getJSONArray("questions").strings().forEach { paragraph("• $it") }
        paragraph("Check before studying",16f,true);note.getJSONArray("uncertainties").strings().forEach { paragraph("• $it") }
        assets.filter { it.getString("kind")=="photo" }.forEach { a ->
            next();paragraph("Whiteboard evidence",16f,true);paragraph(a.getString("name")+" · captured at ${a.optLong("offset_ms")/1000}s",10f)
            val opts=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(store.file(a.getString("id")).path,opts);opts.inJustDecodeBounds=false;opts.inSampleSize=maxOf(1,maxOf(opts.outWidth,opts.outHeight)/1800)
            BitmapFactory.decodeFile(store.file(a.getString("id")).path,opts)?.let { b -> val scale=minOf(499f/b.width,600f/b.height);page!!.canvas.drawBitmap(b,null,RectF(48f,y,48f+b.width*scale,y+b.height*scale),null);b.recycle() }
        }
        finish();val output=ByteArrayOutputStream();pdf.writeTo(output);pdf.close();return output.toByteArray()
    }
}
