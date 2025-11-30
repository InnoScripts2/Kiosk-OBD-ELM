package com.autoservice.payment.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter

class QRGenerator(
    private val writer: QRCodeWriter = QRCodeWriter()
) {

    private val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 0
    )

    fun generateBitmap(payload: String, size: Int = 512): Bitmap {
        require(size > 0) { "Размер QR-кода должен быть положительным" }
        val matrix = try {
            writer.encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
        } catch (error: WriterException) {
            throw IllegalStateException("Не удалось сгенерировать QR", error)
        }
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = if (matrix[x, y]) Color.BLACK else Color.WHITE
                pixels[y * width + x] = color
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
