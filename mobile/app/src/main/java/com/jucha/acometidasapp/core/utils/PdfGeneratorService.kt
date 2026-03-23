package com.jucha.acometidasapp.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Log
import com.jucha.acometidasapp.data.model.FotoDto
import com.jucha.acometidasapp.data.model.PredioDto
import com.tom_roush.harmony.awt.AWTColor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PdfGeneratorService(private val context: Context) {

    private companion object {
        // Higher DPI keeps text edges crisp in PNG exports.
        private const val PNG_EXPORT_DPI = 450f
    }

    fun generarPdfIndividual(
        predio: PredioDto,
        fotos: List<FotoDto>,
        empresaContratista: String = "",
        supervisorObra: String = "",
        fechaEmpresa: String = "",
        fechaSupervisor: String = "",
        tipoProyecto: String = "agua_potable"
    ): File {
        val plantilla = if (tipoProyecto == "alcantarillado")
            "plantilla_acometida_alcantarillado.pdf" else "plantilla_acometida.pdf"
        val stream = context.assets.open(plantilla)
        val doc = PDDocument.load(stream)
        rellenarCampos(doc, predio, fotos, empresaContratista, supervisorObra, fechaEmpresa, fechaSupervisor, tipoProyecto)
        return guardarDocumento(doc, "${predio.codigoPredio}.pdf")
    }

    fun generarPdfBatch(
        predios: List<PredioDto>,
        fotosPorPredio: Map<String, List<FotoDto>>,
        empresaContratista: String = "",
        supervisorObra: String = "",
        tipoProyecto: String = "agua_potable",
        proyectoNombre: String = ""
    ): File {
        val plantilla = if (tipoProyecto == "alcantarillado")
            "plantilla_acometida_alcantarillado.pdf" else "plantilla_acometida.pdf"
        val tempFiles = predios.map { predio ->
            val stream = context.assets.open(plantilla)
            val doc = PDDocument.load(stream)
            rellenarCampos(
                doc, predio,
                fotos = fotosPorPredio[predio.id] ?: emptyList(),
                empresaContratista = empresaContratista,
                supervisorObra = supervisorObra,
                tipoProyecto = tipoProyecto
            )
            val tempFile = File(context.cacheDir, "temp_${predio.id}.pdf")
            doc.save(tempFile)
            doc.close()
            tempFile
        }
        val merged = PDDocument()
        val partsToClose = mutableListOf<PDDocument>()
        tempFiles.forEach { file ->
            val part = PDDocument.load(file)
            partsToClose.add(part)
            merged.importPage(part.getPage(0))
        }
        val nombreSanitizado = proyectoNombre.trim().replace(Regex("[^a-zA-Z0-9\\-]"), "_").replace(Regex("_+"), "_").trim('_')
        val fechaStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val batchName = if (nombreSanitizado.isNotEmpty()) "predios_${nombreSanitizado}_$fechaStr.pdf" else "predios_$fechaStr.pdf"
        val result = guardarDocumento(merged, batchName)
        partsToClose.forEach { it.close() }
        tempFiles.forEach { it.delete() }
        return result
    }

    fun generarPngIndividual(
        predio: PredioDto,
        fotos: List<FotoDto>,
        empresaContratista: String = "",
        supervisorObra: String = "",
        fechaEmpresa: String = "",
        fechaSupervisor: String = "",
        tipoProyecto: String = "agua_potable"
    ): File {
        val plantilla = if (tipoProyecto == "alcantarillado")
            "plantilla_acometida_alcantarillado.pdf" else "plantilla_acometida.pdf"
        val doc = context.assets.open(plantilla).use { stream -> PDDocument.load(stream) }
        rellenarCampos(doc, predio, fotos, empresaContratista, supervisorObra, fechaEmpresa, fechaSupervisor, tipoProyecto)
        val bitmap = renderPngPage(doc)
        doc.close()
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        dir.mkdirs()
        val outFile = File(dir, "${predio.codigoPredio}.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bitmap.recycle()
        return outFile
    }

    fun generarPngBatch(
        predios: List<PredioDto>,
        fotosPorPredio: Map<String, List<FotoDto>>,
        empresaContratista: String = "",
        supervisorObra: String = "",
        tipoProyecto: String = "agua_potable",
        proyectoNombre: String = ""
    ): File {
        val plantilla = if (tipoProyecto == "alcantarillado")
            "plantilla_acometida_alcantarillado.pdf" else "plantilla_acometida.pdf"
        val nombreSanitizado = proyectoNombre.trim().replace(Regex("[^a-zA-Z0-9\\-]"), "_").replace(Regex("_+"), "_").trim('_')
        val fechaStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val zipName = if (nombreSanitizado.isNotEmpty()) "predios_${nombreSanitizado}_$fechaStr.zip" else "predios_$fechaStr.zip"
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        dir.mkdirs()
        val zipFile = File(dir, zipName)
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            predios.forEach { predio ->
                val doc = context.assets.open(plantilla).use { stream -> PDDocument.load(stream) }
                rellenarCampos(doc, predio, fotosPorPredio[predio.id] ?: emptyList(),
                    empresaContratista, supervisorObra, tipoProyecto = tipoProyecto)
                val bitmap = renderPngPage(doc)
                doc.close()
                zos.putNextEntry(ZipEntry("${predio.codigoPredio}.png"))
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, zos)
                zos.closeEntry()
                bitmap.recycle()
            }
        }
        return zipFile
    }

    private fun renderPngPage(doc: PDDocument): Bitmap {
        // Render with Android PdfRenderer to match how the PDF looks in viewers
        // (better support for transparency/blending in logos).
        runCatching {
            val tempPdf = File.createTempFile("png_render_", ".pdf", context.cacheDir)
            doc.save(tempPdf)

            val pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val page = renderer.openPage(0)

            val scale = PNG_EXPORT_DPI / 72f
            val widthPx = (page.width * scale).toInt().coerceAtLeast(1)
            val heightPx = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

            page.close()
            renderer.close()
            pfd.close()
            tempPdf.delete()
            return bitmap
        }.onFailure {
            Log.w("PdfGenerator", "Android PdfRenderer falló, usando fallback PDFBox: ${it.message}")
        }

        val renderer = PDFRenderer(doc)
        return try {
            renderer.renderImageWithDPI(0, PNG_EXPORT_DPI)
        } catch (e: Exception) {
            Log.w("PdfGenerator", "Fallback render PNG por excepción: ${e.message}")
            renderer.renderImage(0, 4.17f)
        }
    }

    private fun rellenarCampos(
        doc: PDDocument,
        predio: PredioDto,
        fotos: List<FotoDto>,
        empresaContratista: String = "",
        supervisorObra: String = "",
        fechaEmpresa: String = "",
        fechaSupervisor: String = "",
        tipoProyecto: String = "agua_potable"
    ) {
        val acroForm = doc.documentCatalog.acroForm
        if (acroForm == null) {
            Log.e("PdfGenerator", "El PDF no tiene AcroForm.")
            return
        }
        Log.d("PdfGenerator", "Campos: ${acroForm.fields.map { it.fullyQualifiedName }}")

        // Recoger posicion + valor de cada campo de texto
        data class FieldInfo(
            val x: Float, val y: Float, val w: Float, val h: Float,
            val value: String, val multiline: Boolean
        )
        val camposTexto = mutableListOf<FieldInfo>()

        fun collect(name: String, value: String) {
            val field = acroForm.getField(name)
            if (field == null) { Log.w("PdfGenerator", "Campo no encontrado: '$name'"); return }
            if (field !is PDTextField) return
            val rect = field.widgets?.firstOrNull()?.rectangle ?: return
            val x = rect.lowerLeftX;  val y = rect.lowerLeftY
            val w = rect.upperRightX - x;  val h = rect.upperRightY - y

            val hasNewlines = value.contains("\n") || value.contains("\r")
            val isMulti = field.isMultiline || hasNewlines
            val safe = if (isMulti) sanitizeMultiline(value) else sanitize(value)
            camposTexto.add(FieldInfo(x + 2f, y, w - 4f, h, safe, isMulti))
            Log.d("PdfGenerator", "Campo '$name' -> x=$x y=$y w=$w h=$h  val='$value'")
        }

        collect("numero_parte",        predio.numeroParte ?: "")
        collect("numero_contrato",     predio.numeroContrato)
        collect("codigo_predio",       predio.codigoPredio)
        collect("usuario",             predio.usuario)
        collect("telefono_usuario",    predio.telefonoUsuario ?: "")
        collect("direccion",           predio.direccion ?: "")
        collect("observaciones",       predio.observaciones ?: "")
        collect("empresa_contratista", empresaContratista)
        collect("supervisor_obra",     supervisorObra)
        collect("fecha_empresa",       fechaEmpresa)
        collect("fecha_supervisor",    fechaSupervisor)

        // Recoger posicion de campos de foto
        fun readRect(name: String, fx: Float, fy: Float, fw: Float, fh: Float): FloatArray {
            val rect = acroForm.getField(name)?.widgets?.firstOrNull()?.rectangle
            return if (rect != null) {
                Log.d("PdfGenerator", "Foto '$name': x=${rect.lowerLeftX} y=${rect.lowerLeftY} w=${rect.upperRightX - rect.lowerLeftX} h=${rect.upperRightY - rect.lowerLeftY}")
                floatArrayOf(rect.lowerLeftX, rect.lowerLeftY, rect.upperRightX - rect.lowerLeftX, rect.upperRightY - rect.lowerLeftY)
            } else {
                Log.w("PdfGenerator", "Campo foto '$name' no encontrado, usando respaldo")
                floatArrayOf(fx, fy, fw, fh)
            }
        }
        val r1 = readRect("foto_predio",    PdfCoords.FOTO1_X, PdfCoords.FOTO1_Y, PdfCoords.FOTO1_WIDTH, PdfCoords.FOTO1_HEIGHT)
        val r2 = readRect("foto_acometida", PdfCoords.FOTO2_X, PdfCoords.FOTO2_Y, PdfCoords.FOTO2_WIDTH, PdfCoords.FOTO2_HEIGHT)
        val foto3Campo = if (tipoProyecto == "alcantarillado") "foto_alcantarillado" else "foto_medidor"
        val r3 = readRect(foto3Campo,       PdfCoords.FOTO3_X, PdfCoords.FOTO3_Y, PdfCoords.FOTO3_WIDTH, PdfCoords.FOTO3_HEIGHT)

        // Eliminar todos los widgets de la pagina 
        val page = doc.getPage(0)
        val sinWidgets = page.annotations.filterNot { it is PDAnnotationWidget }
        page.annotations = sinWidgets

        // Eliminar el AcroForm del catalogo para que no quede referencia
        doc.documentCatalog.cosObject.removeItem(
            com.tom_roush.pdfbox.cos.COSName.getPDFName("AcroForm")
        )

        // Dibujar texto en las posiciones guardadas
        val cs = PDPageContentStream(doc, page, AppendMode.APPEND, true, true)
        val font = PDType1Font.HELVETICA
        cs.setNonStrokingColor(AWTColor.BLACK)

        val fontSize = 8f

        val capHeight = (font.fontDescriptor?.capHeight ?: 718f) / 1000f * fontSize
        val lineHeight = fontSize * 1.4f

        camposTexto.forEach { fi ->
            if (fi.value.isBlank()) return@forEach
            if (fi.multiline) {
                // Alineado a la izquierda, empezando desde el borde superior con margen
                val lines = fi.value.split("\n")
                var lineY = fi.y + fi.h - capHeight - 3f   // 3pt de margen superior
                for (line in lines) {
                    drawText(cs, font, fontSize, line, fi.x, lineY)
                    lineY -= lineHeight
                }
            } else {
                // Centrar verticalmente: baseline sobre la línea media del campo
                val textY = fi.y + (fi.h - capHeight) / 2f
                // Centrar horizontalmente: medir el ancho real del texto
                val textW = try { font.getStringWidth(fi.value) / 1000f * fontSize } catch (e: Exception) { 0f }
                val textX = fi.x + (fi.w - textW) / 2f
                drawText(cs, font, fontSize, fi.value, textX, textY)
            }
        }
        cs.close()

        // Insertar fotos
        insertarFoto(doc, page, fotos, "predio",    r1[0], r1[1], r1[2], r1[3])
        insertarFoto(doc, page, fotos, "acometida", r2[0], r2[1], r2[2], r2[3])
        insertarFoto(doc, page, fotos, "medidor",   r3[0], r3[1], r3[2], r3[3])
    }

    private fun drawText(cs: PDPageContentStream, font: PDType1Font, size: Float, text: String, x: Float, y: Float) {
        if (text.isBlank()) return
        cs.beginText()
        cs.setFont(font, size)
        cs.newLineAtOffset(x, y)
        cs.showText(text)
        cs.endText()
    }


    @Suppress("unused")
    private fun drawWrappedText(cs: PDPageContentStream, font: PDType1Font, size: Float, text: String, x: Float, startY: Float, maxWidth: Float) {
        val lineHeight = size * 1.4f
        var currentY = startY
        for (paragraph in text.split("\n")) {
            var line = ""
            for (word in paragraph.split(" ")) {
                val test = if (line.isEmpty()) word else "$line $word"
                val testW = try { font.getStringWidth(test) / 1000f * size } catch (e: Exception) { test.length * size * 0.5f }
                if (testW > maxWidth && line.isNotEmpty()) {
                    drawText(cs, font, size, line, x, currentY)
                    currentY -= lineHeight
                    line = word
                } else line = test
            }
            if (line.isNotEmpty()) drawText(cs, font, size, line, x, currentY)
            currentY -= lineHeight
        }
    }

    private fun insertarFoto(doc: PDDocument, page: com.tom_roush.pdfbox.pdmodel.PDPage, fotos: List<FotoDto>, tipo: String, x: Float, y: Float, width: Float, height: Float) {
        val foto = fotos.firstOrNull { it.tipo == tipo } ?: run {
            Log.w("PdfGenerator", "No hay foto tipo '$tipo'"); return
        }
        try {
            Log.d("PdfGenerator", "Descargando foto tipo=$tipo url=${foto.url}")
            val connection = java.net.URL(foto.url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout    = 15000
            connection.connect()
            Log.d("PdfGenerator", "HTTP ${connection.responseCode} para foto $tipo")
            val bitmap = BitmapFactory.decodeStream(connection.inputStream) ?: run {
                Log.w("PdfGenerator", "BitmapFactory devolvio null para $tipo"); return
            }
            val pdImage = JPEGFactory.createFromImage(doc, bitmap)
            val cs = PDPageContentStream(doc, page, AppendMode.APPEND, true, true)
            cs.drawImage(pdImage, x, y, width, height)
            cs.close()
            Log.d("PdfGenerator", "Foto $tipo insertada correctamente")
        } catch (e: Exception) {
            Log.e("PdfGenerator", "Error insertando foto '$tipo': ${e.message}", e)
        }
    }

    private fun guardarDocumento(doc: PDDocument, fileName: String): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val outputFile = File(dir, fileName)
        doc.save(outputFile)
        doc.close()
        return outputFile
    }

    // ─── Generador de lista tabular ───────────────────────────────────────────

    fun generarListaPdf(
        predios: List<PredioDto>,
        filtroBarrio: String? = null
    ): File {
        val pageW = 595f; val pageH = 842f
        val mL = 36f;    val mR = 36f
        val mT = 42f;    val mB = 36f
        val cW = pageW - mL - mR

        val colW   = floatArrayOf(26f, 82f, 66f, cW - 26f - 82f - 66f - 110f, 110f)
        val colKey = arrayOf("Nro", "C\u00f3digo", "Contrato", "Usuario", "Ubicaci\u00f3n")
        val fnt  = PDType1Font.HELVETICA
        val fntB = PDType1Font.HELVETICA_BOLD
        val fSz  = 7.8f; val hSz = 8f; val tHdr = 20f; val rH = 15f

        val cHdrBg  = AWTColor(30,  58, 138)
        val cHdrTxt = AWTColor.WHITE
        val cAlt    = AWTColor(237, 242, 255)
        val cBorder = AWTColor(180, 185, 200)
        val cTitle  = AWTColor(30,  58, 138)
        val cSub    = AWTColor(110, 110, 120)
        val cCell   = AWTColor(30,  30,  40)

        val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val doc   = PDDocument()

        fun filled(cs: PDPageContentStream, x: Float, y: Float, w: Float, h: Float, c: AWTColor) {
            cs.setNonStrokingColor(c); cs.addRect(x, y, w, h); cs.fill()
        }
        fun stroked(cs: PDPageContentStream, x: Float, y: Float, w: Float, h: Float, c: AWTColor) {
            cs.setStrokingColor(c); cs.setLineWidth(0.4f); cs.addRect(x, y, w, h); cs.stroke()
        }
        fun txt(cs: PDPageContentStream, t: String, x: Float, y: Float,
                f: PDType1Font, sz: Float, c: AWTColor) {
            cs.setFont(f, sz); cs.setNonStrokingColor(c)
            cs.beginText(); cs.newLineAtOffset(x, y); cs.showText(sanitize(t)); cs.endText()
        }
        fun fit(t: String, maxPt: Float): String {
            var s = sanitize(t)
            while (s.isNotEmpty() && fnt.getStringWidth(s) / 1000f * fSz > maxPt)
                s = s.dropLast(1)
            return if (s.length < sanitize(t).length && s.isNotEmpty()) s.dropLast(1) + "…" else s
        }

        lateinit var cs: PDPageContentStream
        var pageNum = 0
        var y = 0f

        fun newPage() {
            if (pageNum > 0) {
                txt(cs, "Pág. $pageNum  ·  Generado por SEMAPA Acometidas App",
                    mL, mB - 12f, fnt, 6.5f, cSub)
                cs.close()
            }
            pageNum++
            val pg = PDPage(PDRectangle(pageW, pageH))
            doc.addPage(pg)
            cs = PDPageContentStream(doc, pg, AppendMode.OVERWRITE, false)
            y = pageH - mT
        }

        fun drawPageTitle() {
            if (pageNum == 1) {
                txt(cs, "SEMAPA - Lista de Acometidas", mL, y, fntB, 13f, cTitle)
                y -= 13f
                val sub = buildString {
                    append("Exportado: $fecha")
                    if (filtroBarrio != null) append("   \u00b7   Barrio: $filtroBarrio")
                    append("   \u00b7   Total: ${predios.size} predios")
                }
                txt(cs, sub, mL, y, fnt, 7.5f, cSub)
                y -= 5f
                cs.setStrokingColor(cTitle); cs.setLineWidth(1.5f)
                cs.moveTo(mL, y); cs.lineTo(pageW - mR, y); cs.stroke()
            } else {
                txt(cs, "SEMAPA - Lista de Acometidas (cont.)", mL, y, fntB, 10f, cTitle)
                y -= 4f
                cs.setStrokingColor(cTitle); cs.setLineWidth(1f)
                cs.moveTo(mL, y); cs.lineTo(pageW - mR, y); cs.stroke()
            }
            y -= 8f
        }

        fun drawTableHeader() {
            var x = mL
            colW.forEachIndexed { i, w ->
                filled(cs, x, y - tHdr, w, tHdr, cHdrBg)
                txt(cs, colKey[i], x + 4f, y - tHdr + 6f, fntB, hSz, cHdrTxt)
                x += w
            }
            stroked(cs, mL, y - tHdr, cW, tHdr, cHdrBg)
            y -= tHdr
        }

        newPage()
        drawPageTitle()
        drawTableHeader()

        predios.sortedBy { it.usuario.uppercase(Locale.getDefault()) }.forEachIndexed { idx, p ->
            if (y - rH < mB + rH) {
                newPage(); drawPageTitle(); drawTableHeader()
            }
            val rowY  = y - rH
            val rowBg = if (idx % 2 == 1) cAlt else AWTColor.WHITE
            filled(cs, mL, rowY, cW, rH, rowBg)
            stroked(cs, mL, rowY, cW, rH, cBorder)
            val vals = arrayOf(
                "${idx + 1}",
                p.codigoPredio,
                p.numeroContrato,
                p.usuario,
                p.direccion ?: ""
            )
            var x = mL
            colW.forEachIndexed { col, w ->
                txt(cs, fit(vals[col], w - 6f), x + 3f, rowY + 4.5f, fnt, fSz, cCell)
                x += w
            }
            y = rowY
        }

        txt(cs, "Pág. $pageNum  ·  Generado por SEMAPA Acometidas App", mL, mB - 12f, fnt, 6.5f, cSub)
        cs.close()
        return guardarDocumento(doc, "lista_acometidas_${System.currentTimeMillis()}.pdf")
    }

  
    private fun sanitize(text: String): String =
        text.replace("\n", " ").replace("\r", "").map { if (it.code in 32..255) it else '?' }.joinToString("")

  
    private fun sanitizeMultiline(text: String): String =
        text.replace("\r\n", "\n").replace("\r", "\n")
            .map { c -> if (c == '\n' || c.code in 32..255) c else '?' }.joinToString("")
}