package com.example.engine

import android.util.Xml
import com.example.model.CellAddress
import com.example.model.CellAlign
import com.example.model.CellData
import com.example.model.CellStyle
import com.example.model.MergeRange
import com.example.model.SheetData
import com.example.model.WorkbookData
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

object XlsxCodec {

    private data class FontDef(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val colorHex: String? = null
    )

    private data class XfDef(
        val fontId: Int = 0,
        val fillId: Int = 0,
        val numFmtId: Int = 0,
        val align: CellAlign = CellAlign.AUTO
    )

    private data class SheetRef(val name: String, val rId: String)

    // Standard Office 2013+ theme base colors (0..9)
    private val defaultThemeColors = listOf(
        "FFFFFF", "000000", "E7E6E6", "44546A", "4472C4",
        "ED7D31", "A5A5A5", "FFC000", "5B9BD5", "70AD47"
    )

    // Standard Excel BIFF8 indexed colors
    private val indexedColors = mapOf(
        0 to "000000", 1 to "FFFFFF", 2 to "FF0000", 3 to "00FF00", 4 to "0000FF",
        5 to "FFFF00", 6 to "FF00FF", 7 to "00FFFF", 8 to "000000", 9 to "FFFFFF",
        10 to "FF0000", 11 to "00FF00", 12 to "0000FF", 13 to "FFFF00", 14 to "FF00FF",
        15 to "00FFFF", 16 to "800000", 17 to "008000", 18 to "000080", 19 to "808000",
        20 to "800080", 21 to "008080", 22 to "C0C0C0", 23 to "808080", 24 to "9999FF",
        25 to "993366", 26 to "FFFFCC", 27 to "CCFFFF", 28 to "660066", 29 to "FF8080",
        30 to "0066CC", 31 to "CCCCFF", 40 to "00CCFF", 41 to "CCFFFF", 42 to "CCFFCC",
        43 to "FFFF99", 44 to "99CCFF", 45 to "FF99CC", 46 to "CC99FF", 47 to "FFCC99",
        48 to "3366FF", 49 to "33CCCC", 50 to "99CC00", 51 to "FFCC00", 52 to "FF9900",
        53 to "FF6600", 54 to "666699", 55 to "969696", 56 to "003366", 57 to "339966",
        58 to "003300", 59 to "333300", 60 to "993300", 61 to "993366", 62 to "333399",
        63 to "333333"
    )

    private val builtInNumFmts = mapOf(
        9 to "0%",
        10 to "0.00%",
        3 to "#,##0",
        4 to "#,##0.00"
    )

    fun importXlsx(inputStream: InputStream, fileName: String, sourceUriString: String? = null): WorkbookData {
        val entries = readZipEntries(inputStream)
        if (!entries.containsKey("xl/workbook.xml")) {
            throw IllegalArgumentException("File bukan format XLSX yang valid (xl/workbook.xml tidak ditemukan).")
        }

        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val cellStyles = entries["xl/styles.xml"]?.let { parseStyles(it) } ?: emptyList()
        val rels = entries["xl/_rels/workbook.xml.rels"]?.let { parseRels(it) } ?: emptyMap()
        val sheetRefs = parseWorkbookSheets(entries["xl/workbook.xml"]!!)

        val sheets = sheetRefs.mapIndexed { idx, ref ->
            val targetRaw = rels[ref.rId] ?: "worksheets/sheet${idx + 1}.xml"
            val normalizedPath = when {
                targetRaw.startsWith("/") -> targetRaw.removePrefix("/")
                targetRaw.startsWith("xl/") -> targetRaw
                else -> "xl/$targetRaw"
            }
            val sheetBytes = entries[normalizedPath]
            if (sheetBytes != null) {
                parseWorksheet(ref.name, sheetBytes, sharedStrings, cellStyles)
            } else {
                SheetData(name = ref.name)
            }
        }.ifEmpty {
            listOf(SheetData(name = "Sheet1"))
        }

        val rawWorkbook = WorkbookData(
            fileName = fileName,
            sourceUriString = sourceUriString,
            sheets = sheets,
            activeSheetIndex = 0,
            isModified = false
        )
        return FormulaEvaluator.evaluateWorkbook(rawWorkbook)
    }

    fun exportXlsx(workbook: WorkbookData, outputStream: OutputStream) {
        val evaluated = FormulaEvaluator.evaluateWorkbook(workbook)

        // Collect all unique shared strings and styles
        val sharedStringsList = ArrayList<String>()
        val sharedStringsMap = LinkedHashMap<String, Int>()
        fun internString(text: String): Int {
            return sharedStringsMap.getOrPut(text) {
                val idx = sharedStringsList.size
                sharedStringsList.add(text)
                idx
            }
        }

        val stylesList = ArrayList<CellStyle>()
        val stylesMap = LinkedHashMap<CellStyle, Int>()
        fun internStyle(style: CellStyle): Int {
            return stylesMap.getOrPut(style) {
                val idx = stylesList.size
                stylesList.add(style)
                idx
            }
        }
        // Index 0 is always default style
        internStyle(CellStyle())

        // Pre-intern all strings and styles
        for (sheet in evaluated.sheets) {
            for (cell in sheet.cells.values) {
                internStyle(cell.style)
                if (!cell.hasFormula && cell.rawInput.isNotEmpty() && cell.numericValue == null &&
                    !cell.rawInput.equals("TRUE", true) && !cell.rawInput.equals("FALSE", true)
                ) {
                    internString(cell.rawInput)
                }
            }
        }

        ZipOutputStream(outputStream).use { zos ->
            writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml(evaluated.sheets.size))
            writeZipEntry(zos, "_rels/.rels", buildRootRelsXml())
            writeZipEntry(zos, "xl/workbook.xml", buildWorkbookXml(evaluated.sheets))
            writeZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(evaluated.sheets.size))
            writeZipEntry(zos, "xl/sharedStrings.xml", buildSharedStringsXml(sharedStringsList))
            writeZipEntry(zos, "xl/styles.xml", buildStylesXml(stylesList))
            evaluated.sheets.forEachIndexed { index, sheet ->
                writeZipEntry(
                    zos,
                    "xl/worksheets/sheet${index + 1}.xml",
                    buildWorksheetXml(sheet, sharedStringsMap, stylesMap)
                )
            }
        }
    }

    private fun readZipEntries(inputStream: InputStream): Map<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val buffer = ByteArrayOutputStream()
                    val data = ByteArray(8192)
                    var count = zis.read(data)
                    while (count != -1) {
                        buffer.write(data, 0, count)
                        count = zis.read(data)
                    }
                    map[entry.name] = buffer.toByteArray()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return map
    }

    private fun newParser(bytes: ByteArray): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        return parser
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val list = ArrayList<String>()
        val parser = newParser(bytes)
        var inSi = false
        var inT = false
        val currentSi = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = localName(parser.name)
                    if (tag == "si") {
                        inSi = true
                        currentSi.clear()
                    } else if (inSi && tag == "t") {
                        inT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inSi && inT) currentSi.append(parser.text ?: "")
                }
                XmlPullParser.END_TAG -> {
                    val tag = localName(parser.name)
                    if (tag == "t") inT = false
                    else if (tag == "si") {
                        list.add(currentSi.toString())
                        inSi = false
                    }
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun parseRels(bytes: ByteArray): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        val parser = newParser(bytes)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && localName(parser.name) == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                val target = parser.getAttributeValue(null, "Target")
                if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                    map[id] = target
                }
            }
            event = parser.next()
        }
        return map
    }

    private fun parseWorkbookSheets(bytes: ByteArray): List<SheetRef> {
        val list = ArrayList<SheetRef>()
        val parser = newParser(bytes)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && localName(parser.name) == "sheet") {
                val name = parser.getAttributeValue(null, "name") ?: "Sheet"
                val rId = parser.getAttributeValue(null, "r:id")
                    ?: (0 until parser.attributeCount).firstOrNull {
                        parser.getAttributeName(it).endsWith("id") &&
                            parser.getAttributeValue(it).startsWith("rId")
                    }?.let { parser.getAttributeValue(it) }
                    ?: ""
                list.add(SheetRef(name = name, rId = rId))
            }
            event = parser.next()
        }
        return list
    }

    private fun parseStyles(bytes: ByteArray): List<CellStyle> {
        val parser = newParser(bytes)
        val numFmts = HashMap<Int, String>(builtInNumFmts)
        val fonts = ArrayList<FontDef>()
        val fills = ArrayList<String?>()
        val xfs = ArrayList<XfDef>()

        var section = ""
        var currentFontBold = false
        var currentFontItalic = false
        var currentFontColor: String? = null
        var currentFillPattern: String? = null
        var currentFillFg: String? = null
        var inXf = false
        var curXf = XfDef()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = localName(parser.name)
                    when (tag) {
                        "fonts", "fills", "cellXfs", "numFmts" -> section = tag
                        "numFmt" -> if (section == "numFmts") {
                            val id = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull()
                            val code = parser.getAttributeValue(null, "formatCode")
                            if (id != null && code != null) numFmts[id] = code
                        }
                        "font" -> if (section == "fonts") {
                            currentFontBold = false
                            currentFontItalic = false
                            currentFontColor = null
                        }
                        "b" -> if (section == "fonts") {
                            val v = parser.getAttributeValue(null, "val")
                            currentFontBold = v != "0" && v != "false"
                        }
                        "i" -> if (section == "fonts") {
                            val v = parser.getAttributeValue(null, "val")
                            currentFontItalic = v != "0" && v != "false"
                        }
                        "color" -> if (section == "fonts") {
                            currentFontColor = resolveColorAttr(parser)
                        }
                        "fill" -> if (section == "fills") {
                            currentFillPattern = null
                            currentFillFg = null
                        }
                        "patternFill" -> if (section == "fills") {
                            currentFillPattern = parser.getAttributeValue(null, "patternType")
                        }
                        "fgColor" -> if (section == "fills") {
                            currentFillFg = resolveColorAttr(parser)
                        }
                        "xf" -> if (section == "cellXfs") {
                            inXf = true
                            curXf = XfDef(
                                fontId = parser.getAttributeValue(null, "fontId")?.toIntOrNull() ?: 0,
                                fillId = parser.getAttributeValue(null, "fillId")?.toIntOrNull() ?: 0,
                                numFmtId = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull() ?: 0
                            )
                        }
                        "alignment" -> if (inXf) {
                            val horiz = parser.getAttributeValue(null, "horizontal")
                            curXf = curXf.copy(
                                align = when (horiz) {
                                    "left" -> CellAlign.LEFT
                                    "center" -> CellAlign.CENTER
                                    "right" -> CellAlign.RIGHT
                                    else -> CellAlign.AUTO
                                }
                            )
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = localName(parser.name)
                    when (tag) {
                        "font" -> if (section == "fonts") {
                            fonts.add(FontDef(currentFontBold, currentFontItalic, currentFontColor))
                        }
                        "fill" -> if (section == "fills") {
                            val bg = if (currentFillPattern == "solid") currentFillFg else null
                            fills.add(bg)
                        }
                        "xf" -> if (section == "cellXfs") {
                            xfs.add(curXf)
                            inXf = false
                        }
                        "fonts", "fills", "cellXfs", "numFmts" -> section = ""
                    }
                }
            }
            event = parser.next()
        }

        return xfs.map { xf ->
            val font = fonts.getOrNull(xf.fontId) ?: FontDef()
            val bg = fills.getOrNull(xf.fillId)
            val fmt = numFmts[xf.numFmtId]
            CellStyle(
                bgColorHex = bg,
                textColorHex = font.colorHex,
                bold = font.bold,
                italic = font.italic,
                horizontalAlignment = xf.align,
                numberFormat = fmt
            )
        }
    }

    private fun resolveColorAttr(parser: XmlPullParser): String? {
        val rgb = parser.getAttributeValue(null, "rgb")
        if (!rgb.isNullOrBlank() && rgb.length >= 6) {
            val hex = if (rgb.length == 8) rgb else "FF$rgb"
            return "#${hex.uppercase()}"
        }
        val indexed = parser.getAttributeValue(null, "indexed")?.toIntOrNull()
        if (indexed != null && indexedColors.containsKey(indexed)) {
            return "#FF${indexedColors[indexed]}"
        }
        val theme = parser.getAttributeValue(null, "theme")?.toIntOrNull()
        if (theme != null && theme in defaultThemeColors.indices) {
            val baseHex = defaultThemeColors[theme]
            val tint = parser.getAttributeValue(null, "tint")?.toDoubleOrNull() ?: 0.0
            return "#FF${applyTint(baseHex, tint)}"
        }
        return null
    }

    private fun applyTint(hex6: String, tint: Double): String {
        if (tint == 0.0) return hex6
        val r = hex6.substring(0, 2).toInt(16)
        val g = hex6.substring(2, 4).toInt(16)
        val b = hex6.substring(4, 6).toInt(16)
        fun adj(c: Int): Int {
            val out = if (tint < 0) c * (1.0 + tint) else c + (255.0 - c) * tint
            return out.roundToInt().coerceIn(0, 255)
        }
        return "%02X%02X%02X".format(adj(r), adj(g), adj(b))
    }

    private fun parseWorksheet(
        sheetName: String,
        bytes: ByteArray,
        sharedStrings: List<String>,
        styles: List<CellStyle>
    ): SheetData {
        val parser = newParser(bytes)
        val cells = LinkedHashMap<CellAddress, CellData>()
        val colWidths = HashMap<Int, Float>()
        val mergedCells = ArrayList<MergeRange>()

        var currentAddr: CellAddress? = null
        var currentType: String? = null
        var currentStyleIdx = 0
        var currentFormula: String? = null
        var currentVal: String? = null
        var currentInline: String? = null
        var activeSubTag = ""
        var maxRow = 0
        var maxCol = 0

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = localName(parser.name)
                    when (tag) {
                        "col" -> {
                            val min = parser.getAttributeValue(null, "min")?.toIntOrNull()
                            val max = parser.getAttributeValue(null, "max")?.toIntOrNull()
                            val width = parser.getAttributeValue(null, "width")?.toFloatOrNull()
                            if (min != null && max != null && width != null) {
                                val dp = (width * 8.5f).coerceIn(56f, 280f)
                                for (c in (min - 1)..(max - 1)) {
                                    if (c >= 0) colWidths[c] = dp
                                }
                            }
                        }
                        "mergeCell" -> {
                            val ref = parser.getAttributeValue(null, "ref")
                            if (!ref.isNullOrBlank()) {
                                MergeRange.fromA1Range(ref)?.let { mergedCells.add(it) }
                            }
                        }
                        "c" -> {
                            val r = parser.getAttributeValue(null, "r")
                            currentAddr = r?.let { CellAddress.fromA1(it) }
                            currentType = parser.getAttributeValue(null, "t")
                            currentStyleIdx = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: 0
                            currentFormula = null
                            currentVal = null
                            currentInline = null
                        }
                        "f" -> if (currentAddr != null) {
                            activeSubTag = "f"
                            currentFormula = ""
                        }
                        "v" -> if (currentAddr != null) {
                            activeSubTag = "v"
                            currentVal = ""
                        }
                        "t" -> if (currentAddr != null && currentType == "inlineStr") {
                            activeSubTag = "inline"
                            if (currentInline == null) currentInline = ""
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    when (activeSubTag) {
                        "f" -> currentFormula = (currentFormula ?: "") + (parser.text ?: "")
                        "v" -> currentVal = (currentVal ?: "") + (parser.text ?: "")
                        "inline" -> currentInline = (currentInline ?: "") + (parser.text ?: "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = localName(parser.name)
                    when (tag) {
                        "f", "v", "t" -> activeSubTag = ""
                        "c" -> {
                            val addr = currentAddr
                            if (addr != null) {
                                maxRow = maxOf(maxRow, addr.row)
                                maxCol = maxOf(maxCol, addr.col)
                                val style = styles.getOrElse(currentStyleIdx) { CellStyle() }
                                val cell = buildImportedCell(
                                    type = currentType,
                                    formula = currentFormula,
                                    valueText = currentVal,
                                    inlineText = currentInline,
                                    sharedStrings = sharedStrings,
                                    style = style
                                )
                                if (!cell.isEmpty) {
                                    cells[addr] = cell
                                }
                            }
                            currentAddr = null
                        }
                    }
                }
            }
            event = parser.next()
        }

        return SheetData(
            name = sheetName,
            cells = cells,
            colWidthsDp = colWidths,
            mergedCells = mergedCells,
            rowCount = maxOf(40, maxRow + 15),
            colCount = maxOf(14, maxCol + 6)
        )
    }

    private fun buildImportedCell(
        type: String?,
        formula: String?,
        valueText: String?,
        inlineText: String?,
        sharedStrings: List<String>,
        style: CellStyle
    ): CellData {
        if (!formula.isNullOrBlank()) {
            val fClean = formula.trim().removePrefix("=")
            return CellData(
                rawInput = "=$fClean",
                formula = fClean,
                cachedDisplay = valueText ?: "",
                numericValue = valueText?.toDoubleOrNull(),
                style = style
            )
        }

        val resolvedText = when (type) {
            "s" -> {
                val idx = valueText?.toIntOrNull()
                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
            }
            "inlineStr" -> inlineText ?: ""
            "b" -> if (valueText == "1") "TRUE" else "FALSE"
            else -> valueText ?: ""
        }

        return CellData(
            rawInput = resolvedText,
            formula = null,
            cachedDisplay = resolvedText,
            numericValue = if (type != "s" && type != "inlineStr" && type != "b") resolvedText.toDoubleOrNull() else null,
            style = style
        )
    }

    private fun localName(raw: String?): String {
        if (raw == null) return ""
        val idx = raw.indexOf(':')
        return if (idx >= 0) raw.substring(idx + 1) else raw
    }

    private fun writeZipEntry(zos: ZipOutputStream, path: String, bytes: ByteArray) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun buildContentTypesXml(sheetCount: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val xs = Xml.newSerializer()
        xs.setOutput(out, "UTF-8")
        xs.startDocument("UTF-8", true)
        xs.startTag(null, "Types")
        xs.attribute(null, "xmlns", "http://schemas.openxmlformats.org/package/2006/content-types")

        fun defaultExt(ext: String, type: String) {
            xs.startTag(null, "Default")
            xs.attribute(null, "Extension", ext)
            xs.attribute(null, "ContentType", type)
            xs.endTag(null, "Default")
        }
        defaultExt("rels", "application/vnd.openxmlformats-package.relationships+xml")
        defaultExt("xml", "application/xml")

        fun overridePart(partName: String, type: String) {
            xs.startTag(null, "Override")
            xs.attribute(null, "PartName", partName)
            xs.attribute(null, "ContentType", type)
            xs.endTag(null, "Override")
        }
        overridePart("/xl/workbook.xml", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml")
        overridePart("/xl/styles.xml", "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml")
        overridePart("/xl/sharedStrings.xml", "application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml")
        for (i in 1..sheetCount) {
            overridePart(
                "/xl/worksheets/sheet$i.xml",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"
            )
        }
        xs.endTag(null, "Types")
        xs.endDocument()
        return out.toByteArray()
    }

    private fun buildRootRelsXml(): ByteArray {
        val out = ByteArrayOutputStream()
        val xs = Xml.newSerializer()
        xs.setOutput(out, "UTF-8")
        xs.startDocument("UTF-8", true)
        xs.startTag(null, "Relationships")
        xs.attribute(null, "xmlns", "http://schemas.openxmlformats.org/package/2006/relationships")
        xs.startTag(null, "Relationship")
        xs.attribute(null, "Id", "rId1")
        xs.attribute(null, "Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument")
        xs.attribute(null, "Target", "xl/workbook.xml")
        xs.endTag(null, "Relationship")
        xs.endTag(null, "Relationships")
        xs.endDocument()
        return out.toByteArray()
    }

    private fun buildWorkbookXml(sheets: List<SheetData>): ByteArray {
        val out = ByteArrayOutputStream()
        val xs = Xml.newSerializer()
        xs.setOutput(out, "UTF-8")
        xs.startDocument("UTF-8", true)
        xs.startTag(null, "workbook")
        xs.attribute(null, "xmlns", "http://schemas.openxmlformats.org/spreadsheetml/2006/main")
        xs.attribute(null, "xmlns:r", "http://schemas.openxmlformats.org/officeDocument/2006/relationships")
        xs.startTag(null, "sheets")
        sheets.forEachIndexed { idx, sheet ->
            xs.startTag(null, "sheet")
            xs.attribute(null, "name", sheet.name)
            xs.attribute(null, "sheetId", (idx + 1).toString())
            xs.attribute(null, "r:id", "rId${idx + 1}")
            xs.endTag(null, "sheet")
        }
        xs.endTag(null, "sheets")
        xs.endTag(null, "workbook")
        xs.endDocument()
        return out.toByteArray()
    }

    private fun buildWorkbookRelsXml(sheetCount: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val xs = Xml.newSerializer()
        xs.setOutput(out, "UTF-8")
        xs.startDocument("UTF-8", true)
        xs.startTag(null, "Relationships")
        xs.attribute(null, "xmlns", "http://schemas.openxmlformats.org/package/2006/relationships")
        for (i in 1..sheetCount) {
            xs.startTag(null, "Relationship")
            xs.attribute(null, "Id", "rId$i")
            xs.attribute(null, "Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet")
            xs.attribute(null, "Target", "worksheets/sheet$i.xml")
            xs.endTag(null, "Relationship")
        }
        xs.startTag(null, "Relationship")
        xs.attribute(null, "Id", "rId${sheetCount + 1}")
        xs.attribute(null, "Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles")
        xs.attribute(null, "Target", "styles.xml")
        xs.endTag(null, "Relationship")

        xs.startTag(null, "Relationship")
        xs.attribute(null, "Id", "rId${sheetCount + 2}")
        xs.attribute(null, "Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings")
        xs.attribute(null, "Target", "sharedStrings.xml")
        xs.endTag(null, "Relationship")

        xs.endTag(null, "Relationships")
        xs.endDocument()
        return out.toByteArray()
    }

    private fun buildSharedStringsXml(strings: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        val xs = Xml.newSerializer()
        xs.setOutput(out, "UTF-8")
        xs.startDocument("UTF-8", true)
        xs.startTag(null, "sst")
        xs.attribute(null, "xmlns", "http://schemas.openxmlformats.org/spreadsheetml/2006/main")
        xs.attribute(null, "count", strings.size.toString())
        xs.attribute(null, "uniqueCount", strings.size.toString())
        for (s in strings) {
            xs.startTag(null, "si")
            xs.startTag(null, "t")
            if (s.startsWith(" ") || s.endsWith(" ")) {
                xs.attribute(null, "xml:space", "preserve")
            }
            xs.text(s)
            xs.endTag(null, "t")
            xs.endTag(null, "si")
        }
        xs.endTag(null, "sst")
        xs.endDocument()
        return out.toByteArray()
    }

    private fun buildStylesXml(styles: List<CellStyle>): ByteArray {
        // Deduplicate fonts and fills; OOXML requires fill 0 = none, fill 1 = gray125
        val fonts = ArrayList<FontDef>()
        val fontMap = LinkedHashMap<FontDef, Int>()
        fun internFont(f: FontDef): Int = fontMap.getOrPut(f) { fonts.size.also { fonts.add(f) } }
        internFont(FontDef())

        val fills = ArrayList<String?>()
        val fillMap = LinkedHashMap<String, Int>()
        fills.add(null) // 0 = none
        fills.add(null) // 1 = gray125
        fun internFill(hex: String?): Int {
            if (hex.isNullOrBlank()) return 0
            val clean = hex.removePrefix("#").uppercase().let { if (it.length == 6) "FF$it" else it }
            return fillMap.getOrPut(clean) { fills.size.also { fills.add(clean) } }
        }

        val xfEntries = styles.map { st ->
            val fId = internFont(FontDef(st.bold, st.italic, st.textColorHex))
            val flId = internFill(st.bgColorHex)
            XfDef(fontId = fId, fillId = flId, numFmtId = 0, align = st.horizontalAlignment)
        }

        val out = ByteArrayOutputStream()
        val xs = Xml.newSerializer()
        xs.setOutput(out, "UTF-8")
        xs.startDocument("UTF-8", true)
        xs.startTag(null, "styleSheet")
        xs.attribute(null, "xmlns", "http://schemas.openxmlformats.org/spreadsheetml/2006/main")

        // Fonts
        xs.startTag(null, "fonts")
        xs.attribute(null, "count", fonts.size.toString())
        for (f in fonts) {
            xs.startTag(null, "font")
            if (f.bold) { xs.startTag(null, "b"); xs.endTag(null, "b") }
            if (f.italic) { xs.startTag(null, "i"); xs.endTag(null, "i") }
            xs.startTag(null, "sz"); xs.attribute(null, "val", "11"); xs.endTag(null, "sz")
            if (!f.colorHex.isNullOrBlank()) {
                val rgb = f.colorHex.removePrefix("#").uppercase().let { if (it.length == 6) "FF$it" else it }
                xs.startTag(null, "color"); xs.attribute(null, "rgb", rgb); xs.endTag(null, "color")
            }
            xs.startTag(null, "name"); xs.attribute(null, "val", "Calibri"); xs.endTag(null, "name")
            xs.endTag(null, "font")
        }
        xs.endTag(null, "fonts")

        // Fills
        xs.startTag(null, "fills")
        xs.attribute(null, "count", fills.size.toString())
        fills.forEachIndexed { idx, hex ->
            xs.startTag(null, "fill")
            xs.startTag(null, "patternFill")
            when {
                idx == 0 -> xs.attribute(null, "patternType", "none")
                idx == 1 -> xs.attribute(null, "patternType", "gray125")
                else -> {
                    xs.attribute(null, "patternType", "solid")
                    xs.startTag(null, "fgColor"); xs.attribute(null, "rgb", hex!!); xs.endTag(null, "fgColor")
                    xs.startTag(null, "bgColor"); xs.attribute(null, "indexed", "64"); xs.endTag(null, "bgColor")
                }
            }
            xs.endTag(null, "patternFill")
            xs.endTag(null, "fill")
        }
        xs.endTag(null, "fills")

        // Borders
        xs.startTag(null, "borders")
        xs.attribute(null, "count", "1")
        xs.startTag(null, "border")
        listOf("left", "right", "top", "bottom", "diagonal").forEach { side ->
            xs.startTag(null, side); xs.endTag(null, side)
        }
        xs.endTag(null, "border")
        xs.endTag(null, "borders")

        // cellStyleXfs
        xs.startTag(null, "cellStyleXfs")
        xs.attribute(null, "count", "1")
        xs.startTag(null, "xf")
        xs.attribute(null, "numFmtId", "0")
        xs.attribute(null, "fontId", "0")
        xs.attribute(null, "fillId", "0")
        xs.attribute(null, "borderId", "0")
        xs.endTag(null, "xf")
        xs.endTag(null, "cellStyleXfs")

        // cellXfs
        xs.startTag(null, "cellXfs")
        xs.attribute(null, "count", xfEntries.size.toString())
        for (xf in xfEntries) {
            xs.startTag(null, "xf")
            xs.attribute(null, "numFmtId", xf.numFmtId.toString())
            xs.attribute(null, "fontId", xf.fontId.toString())
            xs.attribute(null, "fillId", xf.fillId.toString())
            xs.attribute(null, "borderId", "0")
            xs.attribute(null, "xfId", "0")
            if (xf.fontId != 0) xs.attribute(null, "applyFont", "1")
            if (xf.fillId != 0) xs.attribute(null, "applyFill", "1")
            if (xf.align != CellAlign.AUTO) {
                xs.attribute(null, "applyAlignment", "1")
                xs.startTag(null, "alignment")
                xs.attribute(
                    null,
                    "horizontal",
                    when (xf.align) {
                        CellAlign.LEFT -> "left"
                        CellAlign.CENTER -> "center"
                        CellAlign.RIGHT -> "right"
                        CellAlign.AUTO -> "general"
                    }
                )
                xs.endTag(null, "alignment")
            }
            xs.endTag(null, "xf")
        }
        xs.endTag(null, "cellXfs")

        xs.endTag(null, "styleSheet")
        xs.endDocument()
        return out.toByteArray()
    }

    private fun buildWorksheetXml(
        sheet: SheetData,
        sharedStringsMap: Map<String, Int>,
        stylesMap: Map<CellStyle, Int>
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val xs = Xml.newSerializer()
        xs.setOutput(out, "UTF-8")
        xs.startDocument("UTF-8", true)
        xs.startTag(null, "worksheet")
        xs.attribute(null, "xmlns", "http://schemas.openxmlformats.org/spreadsheetml/2006/main")

        // Column widths
        if (sheet.colWidthsDp.isNotEmpty()) {
            xs.startTag(null, "cols")
            for ((colIdx, dp) in sheet.colWidthsDp.toSortedMap()) {
                xs.startTag(null, "col")
                xs.attribute(null, "min", (colIdx + 1).toString())
                xs.attribute(null, "max", (colIdx + 1).toString())
                xs.attribute(null, "width", "%.2f".format(java.util.Locale.ROOT, dp / 8.5f))
                xs.attribute(null, "customWidth", "1")
                xs.endTag(null, "col")
            }
            xs.endTag(null, "cols")
        }

        // SheetData rows
        xs.startTag(null, "sheetData")
        val rowsGrouped = sheet.cells.entries
            .filter { !it.value.isEmpty }
            .groupBy { it.key.row }
            .toSortedMap()

        for ((rowIdx, rowEntries) in rowsGrouped) {
            xs.startTag(null, "row")
            xs.attribute(null, "r", (rowIdx + 1).toString())
            for ((addr, cell) in rowEntries.sortedBy { it.key.col }) {
                val styleIdx = stylesMap[cell.style] ?: 0
                xs.startTag(null, "c")
                xs.attribute(null, "r", addr.toA1())
                if (styleIdx != 0) xs.attribute(null, "s", styleIdx.toString())

                if (cell.hasFormula) {
                    if (cell.isError) {
                        xs.attribute(null, "t", "e")
                    } else if (cell.numericValue == null && cell.cachedDisplay.isNotEmpty()) {
                        xs.attribute(null, "t", "str")
                    }
                    xs.startTag(null, "f")
                    xs.text(cell.formula!!.removePrefix("="))
                    xs.endTag(null, "f")

                    val cachedVal = cell.numericValue?.let { FormulaEvaluator.formatNumber(it) } ?: cell.cachedDisplay
                    if (cachedVal.isNotEmpty()) {
                        xs.startTag(null, "v")
                        xs.text(cachedVal)
                        xs.endTag(null, "v")
                    }
                } else if (cell.rawInput.equals("TRUE", true) || cell.rawInput.equals("FALSE", true)) {
                    xs.attribute(null, "t", "b")
                    xs.startTag(null, "v")
                    xs.text(if (cell.rawInput.equals("TRUE", true)) "1" else "0")
                    xs.endTag(null, "v")
                } else if (cell.numericValue != null) {
                    xs.startTag(null, "v")
                    xs.text(cell.rawInput.trim())
                    xs.endTag(null, "v")
                } else if (cell.rawInput.isNotEmpty()) {
                    val strIdx = sharedStringsMap[cell.rawInput]
                    if (strIdx != null) {
                        xs.attribute(null, "t", "s")
                        xs.startTag(null, "v")
                        xs.text(strIdx.toString())
                        xs.endTag(null, "v")
                    }
                }
                xs.endTag(null, "c")
            }
            xs.endTag(null, "row")
        }
        xs.endTag(null, "sheetData")

        // Merged cells
        if (sheet.mergedCells.isNotEmpty()) {
            xs.startTag(null, "mergeCells")
            xs.attribute(null, "count", sheet.mergedCells.size.toString())
            for (mr in sheet.mergedCells) {
                xs.startTag(null, "mergeCell")
                xs.attribute(null, "ref", mr.toA1Range())
                xs.endTag(null, "mergeCell")
            }
            xs.endTag(null, "mergeCells")
        }

        xs.endTag(null, "worksheet")
        xs.endDocument()
        return out.toByteArray()
    }
}
