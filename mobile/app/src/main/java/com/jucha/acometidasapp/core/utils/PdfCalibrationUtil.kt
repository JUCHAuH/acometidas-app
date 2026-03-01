package com.jucha.acometidasapp.core.utils

import android.content.Context
import android.os.Environment
import com.tom_roush.harmony.awt.AWTColor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File

class PdfCalibrationUtil(private val context: Context) {

    fun generarCuadricula(): File {
        val stream = context.assets.open("plantilla_acometida.pdf")
        val template = PDDocument.load(stream)
        val doc      = PDDocument()
        val page     = doc.importPage(template.getPage(0))

        val width  = page.mediaBox.width
        val height = page.mediaBox.height

        val cs = PDPageContentStream(doc, page, AppendMode.APPEND, true, true)
        val font = PDType1Font.HELVETICA

        val step = 50f

        // Líneas verticales (X fijo)
        cs.setStrokingColor(AWTColor(200, 0, 0, 120))
        cs.setLineWidth(0.4f)
        var x = 0f
        while (x <= width) {
            cs.moveTo(x, 0f)
            cs.lineTo(x, height)
            cs.stroke()
            x += step
        }

        // Líneas horizontales (Y fijo)
        cs.setStrokingColor(AWTColor(0, 0, 200, 120))
        var y = 0f
        while (y <= height) {
            cs.moveTo(0f, y)
            cs.lineTo(width, y)
            cs.stroke()
            y += step
        }

        // Etiquetas en cada intersección
        cs.setNonStrokingColor(AWTColor(180, 0, 0))
        x = 0f
        while (x <= width) {
            var yy = 0f
            while (yy <= height) {
                // Solo etiquetar cada 100pt para no saturar
                if (x % 100 == 0f && yy % 100 == 0f) {
                    cs.beginText()
                    cs.setFont(font, 5f)
                    cs.newLineAtOffset(x + 1f, yy + 1f)
                    cs.showText("${x.toInt()},${yy.toInt()}")
                    cs.endText()
                }
                yy += step
            }
            x += step
        }

        cs.close()

        // Guardar
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val out = File(dir, "calibracion_grid.pdf")
        doc.save(out)
        doc.close()
        template.close()
        return out
    }
}
