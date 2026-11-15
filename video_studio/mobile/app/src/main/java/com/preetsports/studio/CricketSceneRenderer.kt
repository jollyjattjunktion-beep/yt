package com.preetsports.studio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import kotlin.math.min

data class StudioScene(val title: String, val subtitle: String)

class CricketSceneRenderer {
    fun defaultScenes(match: String, format: String): List<StudioScene> = listOf(
        StudioScene("PREET SPORTS", "$match · $format MATCH PREVIEW"),
        StudioScene("MATCH PREVIEW", "Aaj ke match ke key talking points"),
        StudioScene("TEAM INTRODUCTIONS", "Playing XI and squad details: verify before publishing"),
        StudioScene("PLAYERS TO WATCH", "Player roles and key battles"),
        StudioScene("PITCH REPORT", "Use verified venue and surface information"),
        StudioScene("HEAD TO HEAD", "Historical record requires a verified source"),
        StudioScene("MATCH PREDICTION", "Tactical assessment, not a guaranteed result"),
        StudioScene("PREET SPORTS", "Cricket ko samjho, game ko enjoy karo")
    )

    fun draw(scene: StudioScene, width: Int, height: Int, frameIndex: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(7, 19, 34))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val horizon = (height * .70f)
        p.color = Color.rgb(14, 49, 57)
        canvas.drawOval(RectF(width*.08f, horizon*.77f, width*.92f, height*1.05f), p)
        p.color = Color.rgb(27, 91, 65)
        canvas.drawOval(RectF(width*.29f, height*.61f, width*.71f, height*.93f), p)
        p.color = Color.rgb(232, 183, 95)
        canvas.drawRect(width*.06f, height*.085f, width*.94f, height*.092f, p)
        p.color = Color.rgb(13, 31, 48)
        canvas.drawRoundRect(RectF(width*.055f,height*.12f,width*.945f,height*.90f), width*.025f, width*.025f,p)
        p.color = Color.rgb(238, 190, 103)
        p.textSize = min(width,height)*.045f
        p.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        canvas.drawText("PREET SPORTS", width*.09f, height*.20f, p)
        p.color = Color.WHITE
        p.textSize = min(width,height)*.065f
        val lines = wrap(scene.title, 18)
        var y = height*.43f
        lines.take(4).forEach { canvas.drawText(it, width*.09f, y, p); y += height*.085f }
        p.color = Color.rgb(209, 222, 233)
        p.textSize = min(width,height)*.028f
        wrap(scene.subtitle, if (height > width) 25 else 42).take(3).forEachIndexed { i, line ->
            canvas.drawText(line, width*.09f, height*.76f + i*height*.045f, p)
        }
        p.color = Color.rgb(232,183,95)
        p.textSize = min(width,height)*.025f
        canvas.drawText("ORIGINAL CRICKET ANALYSIS", width*.09f, height*.86f, p)
        return bitmap
    }

    fun savePreview(scene: StudioScene, output: File, width: Int = 720, height: Int = 1280) {
        output.parentFile?.mkdirs()
        draw(scene,width,height,0).compress(Bitmap.CompressFormat.JPEG,90,output.outputStream())
    }

    private fun wrap(text: String, max: Int): List<String> {
        val out = mutableListOf<String>(); var line = ""
        text.split(Regex("\\s+")).forEach { word ->
            if (line.isNotEmpty() && line.length + word.length + 1 > max) { out += line; line = word }
            else line = if (line.isEmpty()) word else "$line $word"
        }
        if (line.isNotEmpty()) out += line
        return out
    }
}
