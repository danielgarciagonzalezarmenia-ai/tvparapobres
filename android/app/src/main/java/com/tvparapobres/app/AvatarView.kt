package com.tvparapobres.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.util.Base64
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Avatar de perfil como la web: carita teñida con el color del perfil,
 * o foto del dispositivo si hay. El glyph ocupa el 58% como .avatar-glyph.
 */
class AvatarView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    def: Int = 0
) : FrameLayout(ctx, attrs, def) {

    companion object {
        val FACES = intArrayOf(
            R.drawable.face_0_smile,
            R.drawable.face_1_sad,
            R.drawable.face_2_serious,
            R.drawable.face_3_surprised
        )

        fun faceRes(variant: Int): Int {
            val i = variant.coerceIn(0, FACES.size - 1)
            return FACES[i]
        }
    }

    private val face: ImageView
    private val photo: ImageView

    init {
        LayoutInflater.from(ctx).inflate(R.layout.avatar_view, this, true)
        face = findViewById(R.id.avFace)
        photo = findViewById(R.id.avPhoto)
    }

    fun set(photoData: String?, color: Int, variant: Int) {
        val bmp = decodePhoto(photoData)
        if (bmp != null) {
            photo.setImageBitmap(circleCrop(bmp))
            photo.visibility = VISIBLE
            face.visibility = GONE
            return
        }
        photo.visibility = GONE
        face.visibility = VISIBLE
        face.setImageResource(faceRes(variant))
        face.setColorFilter(color)
    }

    private fun decodePhoto(photoData: String?): Bitmap? {
        if (photoData.isNullOrEmpty()) return null
        return try {
            val b64 = if (photoData.contains(",")) photoData.substringAfter(",") else photoData
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun circleCrop(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height).coerceAtLeast(1)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, -(src.width - size) / 2f, -(src.height - size) / 2f, paint)
        return out
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val p = ((r - l) * 0.21).toInt()
        if (face.paddingLeft != p) face.setPadding(p, p, p, p)
    }
}
