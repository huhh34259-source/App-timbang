package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.engine.FormulaEvaluator
import com.example.engine.XlsxCodec
import com.example.model.CellAddress
import com.example.model.CellAlign
import com.example.model.CellData
import com.example.model.CellStyle
import com.example.model.QrisInsertMode
import com.example.model.SheetData
import com.example.model.WorkbookData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sheetqr_prefs")

data class UserSettings(
    val qrisInsertMode: QrisInsertMode = QrisInsertMode.SMART_SUMMARY,
    val autoAdvanceRow: Boolean = false
)

class WorkbookRepository(private val context: Context) {

    private val autosaveFile = File(context.filesDir, "autosave_workbook.xlsx")

    private object Keys {
        val QRIS_MODE = stringPreferencesKey("qris_insert_mode")
        val AUTO_ADVANCE = booleanPreferencesKey("auto_advance_row")
        val LAST_FILE_NAME = stringPreferencesKey("last_file_name")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val modeName = prefs[Keys.QRIS_MODE] ?: QrisInsertMode.SMART_SUMMARY.name
        val mode = QrisInsertMode.entries.firstOrNull { it.name == modeName }
            ?: QrisInsertMode.SMART_SUMMARY
        UserSettings(
            qrisInsertMode = mode,
            autoAdvanceRow = prefs[Keys.AUTO_ADVANCE] ?: false
        )
    }

    suspend fun saveQrisMode(mode: QrisInsertMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.QRIS_MODE] = mode.name
        }
    }

    suspend fun saveAutoAdvance(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_ADVANCE] = enabled
        }
    }

    suspend fun importFromUri(uri: Uri): WorkbookData = withContext(Dispatchers.IO) {
        val fileName = queryDisplayName(uri) ?: "Impor_Spreadsheet.xlsx"
        val workbook = context.contentResolver.openInputStream(uri)?.use { stream ->
            XlsxCodec.importXlsx(stream, fileName, uri.toString())
        } ?: throw IllegalStateException("Tidak dapat membuka file yang dipilih.")
        saveAutosave(workbook)
        workbook
    }

    suspend fun exportToUri(uri: Uri, workbook: WorkbookData) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            XlsxCodec.exportXlsx(workbook, stream)
        } ?: throw IllegalStateException("Tidak dapat menulis ke lokasi penyimpanan.")
    }

    suspend fun saveAutosave(workbook: WorkbookData) = withContext(Dispatchers.IO) {
        runCatching {
            FileOutputStream(autosaveFile).use { fos ->
                XlsxCodec.exportXlsx(workbook, fos)
            }
            context.dataStore.edit { prefs ->
                prefs[Keys.LAST_FILE_NAME] = workbook.fileName
            }
        }
    }

    suspend fun loadAutosave(): WorkbookData? = withContext(Dispatchers.IO) {
        if (!autosaveFile.exists()) return@withContext null
        runCatching {
            var savedName = "Autosave_Workbook.xlsx"
            context.dataStore.data.map { it[Keys.LAST_FILE_NAME] }.let { flow ->
                // Read synchronously via first-like collection not needed; fallback cleanly
            }
            FileInputStream(autosaveFile).use { fis ->
                XlsxCodec.importXlsx(fis, savedName)
            }
        }.getOrNull()
    }

    suspend fun clearAutosave() = withContext(Dispatchers.IO) {
        if (autosaveFile.exists()) autosaveFile.delete()
    }

    fun createBlankWorkbook(): WorkbookData {
        return WorkbookData(
            fileName = "SheetQR_Baru.xlsx",
            sheets = listOf(SheetData(name = "Sheet1", rowCount = 40, colCount = 14)),
            activeSheetIndex = 0,
            isModified = false
        )
    }

    fun createSampleWorkbook(): WorkbookData {
        val headerStyle = CellStyle(
            bgColorHex = "#FF0F5238",
            textColorHex = "#FFFFFFFF",
            bold = true,
            horizontalAlignment = CellAlign.CENTER
        )
        val totalLabelStyle = CellStyle(
            bgColorHex = "#FFD1FAE5",
            bold = true,
            horizontalAlignment = CellAlign.RIGHT
        )
        val totalValStyle = CellStyle(
            bgColorHex = "#FFD1FAE5",
            bold = true,
            horizontalAlignment = CellAlign.RIGHT,
            numberFormat = "#,##0"
        )
        val amountStyle = CellStyle(
            horizontalAlignment = CellAlign.RIGHT,
            numberFormat = "#,##0"
        )
        val highlightYellow = CellStyle(
            bgColorHex = "#FFFEF08A",
            horizontalAlignment = CellAlign.CENTER
        )
        val highlightMint = CellStyle(
            bgColorHex = "#FFD1FAE5",
            horizontalAlignment = CellAlign.CENTER,
            bold = true
        )

        val sheet1Cells = linkedMapOf(
            CellAddress(0, 0) to CellData("No", style = headerStyle),
            CellAddress(0, 1) to CellData("Merchant / Hasil Scan QRIS", style = headerStyle),
            CellAddress(0, 2) to CellData("Kota / Ref", style = headerStyle),
            CellAddress(0, 3) to CellData("Nominal (Rp)", style = headerStyle),
            CellAddress(0, 4) to CellData("Kategori", style = headerStyle),

            CellAddress(1, 0) to CellData("1", style = CellStyle(horizontalAlignment = CellAlign.CENTER)),
            CellAddress(1, 1) to CellData("WARUNG KOPI NUSANTARA"),
            CellAddress(1, 2) to CellData("JAKARTA"),
            CellAddress(1, 3) to CellData("45000", style = amountStyle),
            CellAddress(1, 4) to CellData("=IF(D2>=50000,\"Besar\",\"Reguler\")", formula = "IF(D2>=50000,\"Besar\",\"Reguler\")", style = highlightYellow),

            CellAddress(2, 0) to CellData("2", style = CellStyle(horizontalAlignment = CellAlign.CENTER)),
            CellAddress(2, 1) to CellData("TOKO BUKU MERDEKA"),
            CellAddress(2, 2) to CellData("BANDUNG"),
            CellAddress(2, 3) to CellData("125000", style = amountStyle),
            CellAddress(2, 4) to CellData("=IF(D3>=50000,\"Besar\",\"Reguler\")", formula = "IF(D3>=50000,\"Besar\",\"Reguler\")", style = highlightMint),

            CellAddress(3, 0) to CellData("3", style = CellStyle(horizontalAlignment = CellAlign.CENTER)),
            CellAddress(3, 1) to CellData("BAKMI JAYA ABADI"),
            CellAddress(3, 2) to CellData("SURABAYA"),
            CellAddress(3, 3) to CellData("78000", style = amountStyle),
            CellAddress(3, 4) to CellData("=IF(D4>=50000,\"Besar\",\"Reguler\")", formula = "IF(D4>=50000,\"Besar\",\"Reguler\")", style = highlightMint),

            CellAddress(4, 0) to CellData("4", style = CellStyle(horizontalAlignment = CellAlign.CENTER)),
            CellAddress(4, 1) to CellData("[Tap lalu Scan QRIS di sini]"),
            CellAddress(4, 2) to CellData(""),
            CellAddress(4, 3) to CellData("0", style = amountStyle),
            CellAddress(4, 4) to CellData("=IF(D5>=50000,\"Besar\",\"Reguler\")", formula = "IF(D5>=50000,\"Besar\",\"Reguler\")"),

            CellAddress(6, 2) to CellData("TOTAL", style = totalLabelStyle),
            CellAddress(6, 3) to CellData("=SUM(D2:D5)", formula = "SUM(D2:D5)", style = totalValStyle),
            CellAddress(6, 4) to CellData("=COUNTIF(E2:E5,\"Besar\")&\" Besar\"", formula = "COUNTIF(E2:E5,\"Besar\")&\" Besar\"", style = highlightMint),

            CellAddress(7, 2) to CellData("RATA-RATA", style = totalLabelStyle),
            CellAddress(7, 3) to CellData("=AVERAGE(D2:D5)", formula = "AVERAGE(D2:D5)", style = totalValStyle)
        )

        val sheet1 = SheetData(
            name = "Transaksi QRIS",
            cells = sheet1Cells,
            colWidthsDp = mapOf(0 to 56f, 1 to 210f, 2 to 130f, 3 to 130f, 4 to 116f),
            rowCount = 40,
            colCount = 12
        )

        val sheet2Cells = linkedMapOf(
            CellAddress(0, 0) to CellData("Metrik Rekap", style = headerStyle),
            CellAddress(0, 1) to CellData("Nilai", style = headerStyle),
            CellAddress(1, 0) to CellData("Total Transaksi (dari Sheet 1)"),
            CellAddress(1, 1) to CellData("='Transaksi QRIS'!D7", formula = "'Transaksi QRIS'!D7", style = amountStyle),
            CellAddress(2, 0) to CellData("Rata-rata Transaksi"),
            CellAddress(2, 1) to CellData("='Transaksi QRIS'!D8", formula = "'Transaksi QRIS'!D8", style = amountStyle),
            CellAddress(3, 0) to CellData("Transaksi Tertinggi"),
            CellAddress(3, 1) to CellData("=MAX('Transaksi QRIS'!D2:D5)", formula = "MAX('Transaksi QRIS'!D2:D5)", style = amountStyle)
        )

        val sheet2 = SheetData(
            name = "Ringkasan",
            cells = sheet2Cells,
            colWidthsDp = mapOf(0 to 210f, 1 to 140f),
            rowCount = 30,
            colCount = 8
        )

        return FormulaEvaluator.evaluateWorkbook(
            WorkbookData(
                fileName = "Rekap_QRIS_Sample.xlsx",
                sheets = listOf(sheet1, sheet2),
                activeSheetIndex = 0,
                isModified = false
            )
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }
}
