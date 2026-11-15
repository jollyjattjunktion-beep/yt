package com.preetsports.studio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import java.io.File

enum class SceneKind { HERO, MATCHUP, PLAYING_XI, PITCH, FORM, PLAYERS, HEAD_TO_HEAD, PREDICTION, OUTRO }

data class StudioScene(
    val title: String,
    val subtitle: String,
    val kind: SceneKind = SceneKind.MATCHUP,
    val homeTeam: String = "TEAM A",
    val awayTeam: String = "TEAM B"
)

class CricketSceneRenderer {
    fun defaultScenes(match: String, format: String): List<StudioScene> {
        val teams = match.split(Regex("\\s+vs\\s+|\\s+v\\s+", RegexOption.IGNORE_CASE))
        fun code(raw: String?): String {
            val name = raw.orEmpty().substringBefore(",").trim().split(Regex("\\s+")).firstOrNull().orEmpty()
            return when (name.lowercase()) {
                "australia", "aus" -> "AUS"
                "england", "eng" -> "ENG"
                "india", "ind" -> "IND"
                "new" -> "NZ"
                "newzealand", "nz" -> "NZ"
                "pakistan", "pak" -> "PAK"
                "south" -> "SA"
                "southafrica", "sa" -> "SA"
                "west" -> "WI"
                "windies", "wi" -> "WI"
                "sri" -> "SL"
                "srilanka", "sl" -> "SL"
                "bangladesh", "ban" -> "BAN"
                "afghanistan", "afg" -> "AFG"
                "zimbabwe", "zim" -> "ZIM"
                else -> name.take(3).uppercase().ifBlank { "TEAM" }
            }
        }
        val a = code(teams.firstOrNull())
        val b = code(teams.getOrNull(1))
        return listOf(
            StudioScene("MATCH DAY", "$match  •  $format", SceneKind.HERO, a, b),
            StudioScene("THE BIG MATCHUP", "Team comparison and key talking points", SceneKind.MATCHUP, a, b),
            StudioScene("PLAYING XI", "Add verified line-ups before publishing", SceneKind.PLAYING_XI, a, b),
            StudioScene("PITCH & CONDITIONS", "Venue facts and surface report need verification", SceneKind.PITCH, a, b),
            StudioScene("RECENT FORM", "Add verified recent results and scorecards", SceneKind.FORM, a, b),
            StudioScene("PLAYERS TO WATCH", "Key roles, match-ups and player opportunities", SceneKind.PLAYERS, a, b),
            StudioScene("HEAD TO HEAD", "Historical numbers require a verified source", SceneKind.HEAD_TO_HEAD, a, b),
            StudioScene("MATCH OUTLOOK", "Tactical view — not a guaranteed result", SceneKind.PREDICTION, a, b),
            StudioScene("PREET SPORTS", "Cricket ko samjho. Game ko enjoy karo.", SceneKind.OUTRO, a, b)
        )
    }

    fun draw(scene: StudioScene, width: Int, height: Int, frameIndex: Int, assets: ScenePhotoAssets? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val gold = Color.rgb(241,190,86); val cyan = Color.rgb(46,177,199)
        c.drawColor(Color.rgb(5,17,31))
        p.shader = LinearGradient(0f,0f,width.toFloat(),height.toFloat(),
            intArrayOf(Color.rgb(9,34,58),Color.rgb(7,18,34),Color.rgb(14,47,55)),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,width.toFloat(),height.toFloat(),p); p.shader=null
        p.color=gold; c.drawRect(width*.055f,height*.045f,width*.945f,height*.051f,p)
        p.color=Color.WHITE; p.textSize=width*.031f
        p.typeface=android.graphics.Typeface.create("sans-serif-condensed",android.graphics.Typeface.BOLD)
        c.drawText("PREET SPORTS  /  CRICKET ANALYSIS",width*.075f,height*.09f,p)
        when(scene.kind) {
            SceneKind.HERO -> {
                if (assets?.venue == null) {
                    p.color=Color.rgb(16,69,73); c.drawOval(RectF(width*.02f,height*.34f,width*.98f,height*.84f),p)
                    p.color=Color.rgb(26,112,76); c.drawOval(RectF(width*.12f,height*.47f,width*.88f,height*.83f),p)
                    p.color=Color.rgb(184,143,83); c.drawRoundRect(RectF(width*.44f,height*.52f,width*.56f,height*.76f),12f,12f,p)
                }
                for(n in 0..5) { p.color=gold; c.drawCircle(width*(.12f+n*.15f),height*.30f,width*.009f,p) }
                title(c,p,scene.title,width,height,gold)
                badge(c,p,scene.homeTeam,width*.25f,height*.46f,width*.31f,gold)
                badge(c,p,scene.awayTeam,width*.75f,height*.46f,width*.31f,cyan)
                p.color=Color.WHITE;p.textSize=width*.12f;p.textAlign=Paint.Align.CENTER;c.drawText("VS",width*.5f,height*.49f,p);p.textAlign=Paint.Align.LEFT
                p.color=gold;p.textSize=width*.034f;p.textAlign=Paint.Align.CENTER;c.drawText(scene.subtitle.uppercase(),width*.5f,height*.88f,p);p.textAlign=Paint.Align.LEFT
            }
            SceneKind.MATCHUP -> {
                title(c,p,scene.title,width,height,gold)
                badge(c,p,scene.homeTeam,width*.27f,height*.36f,width*.34f,gold)
                badge(c,p,scene.awayTeam,width*.73f,height*.36f,width*.34f,cyan)
                p.color=Color.WHITE;p.textSize=width*.10f;p.textAlign=Paint.Align.CENTER;c.drawText("VS",width*.5f,height*.38f,p);p.textAlign=Paint.Align.LEFT
                panel(c,p,width*.08f,height*.54f,width*.92f,height*.77f,"MATCH STORY",listOf("Top-order stability","Middle-over control","Death-over execution"),gold)
            }
            SceneKind.PLAYING_XI -> {
                title(c,p,scene.title,width,height,gold); ribbon(c,p,scene.homeTeam,width*.08f,height*.23f,width*.92f,gold)
                for(n in 0..10) {
                    val x=width*(.08f+(n%3)*.285f); val y=height*(.31f+(n/3)*.125f)
                    p.color=Color.rgb(16,43,64);c.drawRoundRect(RectF(x,y,x+width*.25f,y+height*.105f),18f,18f,p)
                    p.color=gold;c.drawCircle(x+width*.125f,y+height*.035f,height*.021f,p)
                    p.color=Color.WHITE;p.textSize=width*.026f;p.textAlign=Paint.Align.CENTER
                    c.drawText("XI SLOT "+(n+1),x+width*.125f,y+height*.085f,p);p.textAlign=Paint.Align.LEFT
                }
                smallCenter(c,p,"ADD VERIFIED PLAYER NAMES & PHOTOS",width,height*.84f)
            }
            SceneKind.PITCH -> {
                title(c,p,scene.title,width,height,gold)
                if (assets?.venue != null) {
                    drawPhoto(c,p,assets.venue,RectF(width*.08f,height*.25f,width*.92f,height*.56f),width*.025f,255)
                } else {
                    p.color=Color.rgb(20,94,73);c.drawRoundRect(RectF(width*.08f,height*.25f,width*.92f,height*.56f),30f,30f,p)
                    p.color=Color.rgb(64,145,91);c.drawOval(RectF(width*.16f,height*.29f,width*.84f,height*.53f),p)
                    p.color=Color.rgb(194,158,101);c.drawRoundRect(RectF(width*.44f,height*.32f,width*.56f,height*.51f),12f,12f,p)
                }
                panel(c,p,width*.08f,height*.60f,width*.92f,height*.80f,"PITCH REPORT",listOf("Surface: awaiting verified report","Bounce / carry: confirm at venue","Weather & toss: update on match day"),gold)
                smallCenter(c,p,"VENUE PHOTO & VERIFIED PITCH DATA SLOT",width,height*.86f)
            }
            SceneKind.FORM -> {
                title(c,p,scene.title,width,height,gold);ribbon(c,p,scene.homeTeam,width*.08f,height*.24f,width*.92f,gold)
                for(n in 0..3) {
                    val y=height*(.34f+n*.115f);p.color=Color.rgb(16,42,64);c.drawRoundRect(RectF(width*.08f,y,width*.92f,y+height*.085f),16f,16f,p)
                    p.color=if(n%2==0) gold else cyan;c.drawRoundRect(RectF(width*.10f,y+height*.018f,width*.25f,y+height*.067f),10f,10f,p)
                    p.color=Color.WHITE;p.textSize=width*.027f;c.drawText("MATCH 0"+(n+1),width*.30f,y+height*.053f,p)
                    p.color=Color.LTGRAY;p.textSize=width*.022f;c.drawText("ADD VERIFIED SCORECARD",width*.55f,y+height*.053f,p)
                }
                smallCenter(c,p,"NO HISTORICAL SCORES ARE ASSUMED",width,height*.85f)
            }
            SceneKind.PLAYERS -> {
                title(c,p,scene.title,width,height,gold)
                val roles=listOf("TOP ORDER BATTER","PACE ALL-ROUNDER","SPIN OPTION")
                for(n in 0..2) {
                    val x=width*(.08f+n*.29f);p.color=Color.rgb(18,48,70);c.drawRoundRect(RectF(x,height*.29f,x+width*.25f,height*.67f),20f,20f,p)
                    p.color=if(n==1) cyan else gold;c.drawCircle(x+width*.125f,height*.41f,width*.072f,p)
                    p.color=Color.rgb(8,26,43);c.drawCircle(x+width*.125f,height*.40f,width*.047f,p)
                    p.color=Color.WHITE;p.textSize=width*.022f;p.textAlign=Paint.Align.CENTER;c.drawText("PHOTO SLOT",x+width*.125f,height*.56f,p)
                    p.color=gold;c.drawText(roles[n],x+width*.125f,height*.62f,p);p.textAlign=Paint.Align.LEFT
                }
                smallCenter(c,p,"ADD CONFIRMED PLAYERS & HEADSHOTS",width,height*.77f)
            }
            SceneKind.HEAD_TO_HEAD -> {
                title(c,p,scene.title,width,height,gold);ribbon(c,p,scene.homeTeam+"  vs  "+scene.awayTeam,width*.08f,height*.25f,width*.92f,gold)
                listOf("WINS","NO RESULT","LAST MEETING").forEachIndexed { n,label ->
                    val y=height*(.38f+n*.15f);p.color=Color.rgb(15,43,65);c.drawRoundRect(RectF(width*.08f,y,width*.92f,y+height*.105f),16f,16f,p)
                    p.color=Color.WHITE;p.textSize=width*.031f;p.typeface=android.graphics.Typeface.DEFAULT_BOLD;c.drawText(label,width*.13f,y+height*.064f,p)
                    p.color=gold;p.textSize=width*.025f;c.drawText("VERIFY SOURCE",width*.60f,y+height*.064f,p)
                }
                smallCenter(c,p,"H2H FIGURES REQUIRE A TRUSTED SOURCE",width,height*.88f)
            }
            SceneKind.PREDICTION -> {
                title(c,p,scene.title,width,height,gold);badge(c,p,scene.homeTeam,width*.27f,height*.34f,width*.34f,gold);badge(c,p,scene.awayTeam,width*.73f,height*.34f,width*.34f,cyan)
                panel(c,p,width*.08f,height*.53f,width*.92f,height*.79f,"KEY DECISION FACTORS",listOf("Playing XI and role clarity","Pitch, weather and toss","Recent form and match-ups"),gold)
                smallCenter(c,p,"CONDITIONAL TACTICAL VIEW • NOT A GUARANTEE",width,height*.86f)
            }
            SceneKind.OUTRO -> {
                p.color=gold;p.textSize=width*.12f;p.typeface=android.graphics.Typeface.DEFAULT_BOLD;p.textAlign=Paint.Align.CENTER;c.drawText("PREET SPORTS",width*.5f,height*.43f,p)
                p.color=Color.WHITE;p.textSize=width*.045f;c.drawText(scene.subtitle,width*.5f,height*.51f,p);p.textAlign=Paint.Align.LEFT
                p.color=cyan;c.drawRoundRect(RectF(width*.18f,height*.59f,width*.82f,height*.60f),10f,10f,p)
            }
        }
        assets?.let { photos ->
            when (scene.kind) {
                SceneKind.HERO -> {
                    drawCirclePhoto(c,p,photos.homeTeam,RectF(width*.105f,height*.34f,width*.395f,height*.63f),gold)
                    drawCirclePhoto(c,p,photos.awayTeam,RectF(width*.605f,height*.34f,width*.895f,height*.63f),cyan)
                }
                SceneKind.MATCHUP, SceneKind.PREDICTION -> {
                    drawCirclePhoto(c,p,photos.homeTeam,RectF(width*.12f,height*.20f,width*.42f,height*.50f),gold)
                    drawCirclePhoto(c,p,photos.awayTeam,RectF(width*.58f,height*.20f,width*.88f,height*.50f),cyan)
                }
                SceneKind.PLAYING_XI -> photos.players.take(11).forEachIndexed { n, photo ->
                    val x=width*(.08f+(n%3)*.285f); val y=height*(.31f+(n/3)*.125f)
                    drawPhoto(c,p,photo,RectF(x+width*.012f,y+height*.008f,x+width*.238f,y+height*.068f),width*.014f,255)
                }
                SceneKind.PLAYERS -> photos.players.take(3).forEachIndexed { n, photo ->
                    val x=width*(.08f+n*.29f)
                    drawCirclePhoto(c,p,photo,RectF(x+width*.045f,height*.33f,x+width*.205f,height*.49f),if(n==1) cyan else gold)
                }
                else -> Unit
            }
        }
        val sweep = ((frameIndex % 160) / 160f) * width
        p.color=Color.argb(145,241,190,86)
        c.drawRect(sweep,height*.925f,sweep+width*.12f,height*.929f,p)
        p.color=Color.rgb(181,198,211);p.textSize=width*.021f
        c.drawText("GRAPHIC PREVIEW • VERIFY MATCH FACTS BEFORE UPLOAD",width*.075f,height*.955f,p)
        return bitmap
    }

    private fun drawPhoto(c:Canvas,p:Paint,photo:Bitmap?,rect:RectF,radius:Float,alpha:Int) {
        if (photo == null || photo.isRecycled) return
        val save=c.save()
        val path=android.graphics.Path().apply { addRoundRect(rect,radius,radius,android.graphics.Path.Direction.CW) }
        c.clipPath(path); p.alpha=alpha
        c.drawBitmap(photo,null,rect,p)
        p.alpha=255; c.restoreToCount(save)
    }
    private fun drawCirclePhoto(c:Canvas,p:Paint,photo:Bitmap?,rect:RectF,ringColor:Int) {
        if (photo == null || photo.isRecycled) return
        p.color=ringColor;c.drawOval(rect,p)
        val inset=rect.width()*.045f
        val inner=RectF(rect.left+inset,rect.top+inset,rect.right-inset,rect.bottom-inset)
        val save=c.save()
        val path=android.graphics.Path().apply { addOval(inner,android.graphics.Path.Direction.CW) }
        c.clipPath(path); c.drawBitmap(photo,null,inner,p); c.restoreToCount(save)
    }
    private fun title(c:Canvas,p:Paint,text:String,w:Int,h:Int,color:Int) {
        p.color=color;p.textSize=w*.064f;p.typeface=android.graphics.Typeface.create("sans-serif-condensed",android.graphics.Typeface.BOLD)
        c.drawText(text.take(24),w*.075f,h*.18f,p)
    }
    private fun badge(c:Canvas,p:Paint,name:String,x:Float,y:Float,r:Float,color:Int) {
        p.color=color;c.drawCircle(x,y,r,p);p.color=Color.rgb(8,26,43);c.drawCircle(x,y,r*.84f,p)
        p.color=color;c.drawCircle(x,y,r*.68f,p);p.color=Color.rgb(8,26,43);c.drawCircle(x,y,r*.59f,p)
        p.color=Color.WHITE;p.textSize=r*.27f;p.typeface=android.graphics.Typeface.DEFAULT_BOLD;p.textAlign=Paint.Align.CENTER
        c.drawText(name.take(3),x,y+r*.10f,p);p.textSize=r*.17f;c.drawText(name.take(16),x,y+r*1.28f,p);p.textAlign=Paint.Align.LEFT
    }
    private fun ribbon(c:Canvas,p:Paint,text:String,left:Float,top:Float,right:Float,color:Int) {
        p.color=color;c.drawRoundRect(RectF(left,top,right,top+top*.22f),14f,14f,p)
        p.color=Color.rgb(7,25,42);p.textSize=(right-left)*.045f;p.typeface=android.graphics.Typeface.DEFAULT_BOLD
        c.drawText(text.take(30),left+(right-left)*.04f,top+top*.145f,p)
    }
    private fun panel(c:Canvas,p:Paint,l:Float,t:Float,r:Float,b:Float,heading:String,items:List<String>,accent:Int) {
        p.color=Color.rgb(14,39,60);c.drawRoundRect(RectF(l,t,r,b),18f,18f,p);p.color=accent;c.drawRect(l,t,l+(r-l)*.018f,b,p)
        p.color=accent;p.textSize=(r-l)*.052f;p.typeface=android.graphics.Typeface.DEFAULT_BOLD;c.drawText(heading,l+(r-l)*.06f,t+(b-t)*.22f,p)
        p.color=Color.WHITE;p.textSize=(r-l)*.035f;p.typeface=android.graphics.Typeface.DEFAULT
        items.forEachIndexed { n,item -> c.drawText("•  "+item,l+(r-l)*.06f,t+(b-t)*(.43f+n*.19f),p) }
    }
    private fun smallCenter(c:Canvas,p:Paint,text:String,w:Int,y:Float) {
        p.color=Color.rgb(205,218,229);p.textSize=w*.025f;p.textAlign=Paint.Align.CENTER;c.drawText(text,w*.5f,y,p);p.textAlign=Paint.Align.LEFT
    }
    fun savePreview(scene:StudioScene,output:File,assets:ScenePhotoAssets?=null,width:Int=720,height:Int=1280) {
        output.parentFile?.mkdirs();draw(scene,width,height,0,assets).compress(Bitmap.CompressFormat.JPEG,92,output.outputStream())
    }
}
