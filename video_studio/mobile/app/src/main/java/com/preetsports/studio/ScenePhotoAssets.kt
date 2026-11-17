package com.preetsports.studio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Optional visual assets imported by the user from their gallery.
 * Order: venue, home-team image, away-team image, then player portraits.
 */
data class ScenePhotoAssets(
    val venue: Bitmap?,
    val homeTeam: Bitmap?,
    val awayTeam: Bitmap?,
    val players: List<Bitmap>
) {
    companion object {
        fun load(project: File): ScenePhotoAssets {
            val dir = File(project, "assets")
            fun decode(name: String): Bitmap? {
                val file = File(dir, name)
                if (!file.isFile) return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
                return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                })
            }
            return ScenePhotoAssets(
                venue = decode("01-venue"),
                homeTeam = decode("02-home-team"),
                awayTeam = decode("03-away-team"),
                players = (1..11).mapNotNull { decode("player-%02d".format(it)) }
            )
        }
    }

    fun recycle() {
        venue?.recycle()
        homeTeam?.recycle()
        awayTeam?.recycle()
        players.forEach { if (!it.isRecycled) it.recycle() }
    }
}
