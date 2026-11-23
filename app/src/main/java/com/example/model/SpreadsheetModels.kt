package com.example.model

enum class CellAlign {
    AUTO, LEFT, CENTER, RIGHT
}

data class CellAddress(
    val row: Int,
    val col: Int
) : Comparable<CellAddress> {

    fun toA1(): String = "${colIndexToName(col)}${row + 1}"

    override fun compareTo(other: CellAddress): Int {
        val rowCmp = row.compareTo(other.row)
        return if (rowCmp != 0) rowCmp else col.compareTo(other.col)
    }

    companion object {
        fun colIndexToName(index: Int): String {
            var n = index + 1
            val sb = StringBuilder()
            while (n > 0) {
                val rem = (n - 1) % 26
                sb.append(('A'.code + rem).toChar())
                n = (n - 1) / 26
            }
            return sb.reverse().toString()
        }

        fun colNameToIndex(name: String): Int {
            var res = 0
            for (ch in name.uppercase()) {
                if (ch !in 'A'..'Z') return -1
                res = res * 26 + (ch.code - 'A'.code + 1)
            }
            return res - 1
        }

        fun fromA1(ref: String): CellAddress? {
            val cleaned = ref.trim().replace("$", "")
            var splitIdx = 0
            while (splitIdx < cleaned.length && cleaned[splitIdx].isLetter()) {
                splitIdx++
            }
            if (splitIdx == 0 || splitIdx >= cleaned.length) return null
            val colStr = cleaned.substring(0, splitIdx)
            val rowStr = cleaned.substring(splitIdx)
            val col = colNameToIndex(colStr)
            val rowNum = rowStr.toIntOrNull() ?: return null
            if (col < 0 || rowNum <= 0) return null
            return CellAddress(row = rowNum - 1, col = col)
        }
    }
}

data class CellStyle(
    val bgColorHex: String? = null,
    val textColorHex: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val horizontalAlignment: CellAlign = CellAlign.AUTO,
    val numberFormat: String? = null
) {
    val isDefault: Boolean
        get() = bgColorHex == null &&
            textColorHex == null &&
            !bold &&
            !italic &&
            horizontalAlignment == CellAlign.AUTO &&
            numberFormat == null
}

data class CellData(
    val rawInput: String = "",
    val formula: String? = null,
    val cachedDisplay: String = "",
    val numericValue: Double? = null,
    val isError: Boolean = false,
    val style: CellStyle = CellStyle()
) {
    val isEmpty: Boolean
        get() = rawInput.isEmpty() && formula == null && style.isDefault

    val hasFormula: Boolean
        get() = !formula.isNullOrBlank()
}

data class MergeRange(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int
) {
    fun contains(row: Int, col: Int): Boolean =
        row in startRow..endRow && col in startCol..endCol

    fun isTopLeft(row: Int, col: Int): Boolean =
        row == startRow && col == startCol

    fun toA1Range(): String =
        "${CellAddress(startRow, startCol).toA1()}:${CellAddress(endRow, endCol).toA1()}"

    companion object {
        fun fromA1Range(rangeStr: String): MergeRange? {
            val parts = rangeStr.split(":")
            if (parts.size != 2) return null
            val start = CellAddress.fromA1(parts[0]) ?: return null
            val end = CellAddress.fromA1(parts[1]) ?: return null
            return MergeRange(
                startRow = minOf(start.row, end.row),
                startCol = minOf(start.col, end.col),
                endRow = maxOf(start.row, end.row),
                endCol = maxOf(start.col, end.col)
            )
        }
    }
}

data class SheetData(
    val name: String,
    val cells: Map<CellAddress, CellData> = emptyMap(),
    val colWidthsDp: Map<Int, Float> = emptyMap(),
    val rowHeightsDp: Map<Int, Float> = emptyMap(),
    val mergedCells: List<MergeRange> = emptyList(),
    val rowCount: Int = 40,
    val colCount: Int = 14
) {
    fun getCell(address: CellAddress): CellData = cells[address] ?: CellData()

    fun getColWidthDp(col: Int): Float = colWidthsDp[col] ?: DEFAULT_COL_WIDTH_DP

    fun getRowHeightDp(row: Int): Float = rowHeightsDp[row] ?: DEFAULT_ROW_HEIGHT_DP

    companion object {
        const val DEFAULT_COL_WIDTH_DP = 112f
        const val DEFAULT_ROW_HEIGHT_DP = 40f
    }
}

data class WorkbookData(
    val fileName: String = "Workbook_Baru.xlsx",
    val sourceUriString: String? = null,
    val sheets: List<SheetData> = listOf(SheetData(name = "Sheet1")),
    val activeSheetIndex: Int = 0,
    val isModified: Boolean = false
) {
    val activeSheet: SheetData
        get() = sheets.getOrElse(activeSheetIndex) { sheets.first() }
}

enum class QrisInsertMode(val label: String, val description: String) {
    SMART_SUMMARY("Ringkasan", "Nama merchant, nominal (jika ada), dan NMID"),
    MERCHANT_NAME("Merchant", "Hanya nama merchant/toko (Tag 59)"),
    AMOUNT_ONLY("Nominal", "Hanya nominal angka agar bisa dihitung rumus"),
    NMID_ONLY("NMID / ID", "Hanya kode NMID / Merchant ID"),
    RAW_PAYLOAD("Teks Mentah", "Seluruh string kode QRIS apa adanya"),
    AUTO_ROW("Auto Baris", "Isi 3 kolom: Merchant, Nominal, Kota/NMID")
}
