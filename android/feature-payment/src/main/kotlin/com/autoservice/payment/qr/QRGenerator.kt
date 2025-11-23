package com.autoservice.payment.qr

import android.graphics.Bitmap
import io.github.g0dkar.qrcode.QRCode
import io.github.g0dkar.qrcode.render.Colors
import io.github.g0dkar.qrcode.render.QRCodeRenderers

class QRGenerator {

    fun generateBitmap(payload: String, size: Int = 512): Bitmap {
        val renderer = QRCodeRenderers.DEFAULT.withColor(Colors.DARK)
        val pngBytes = QRCode(payload).render(renderer).getBytes()
        return android.graphics.BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
            ?.let { Bitmap.createScaledBitmap(it, size, size, true) }
            ?: throw IllegalStateException("Не удалось сгенерировать QR")
    }
}
