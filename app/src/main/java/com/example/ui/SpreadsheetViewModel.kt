package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.WorkbookRepository
import com.example.engine.FormulaEvaluator
import com.example.engine.ParsedQrisResult
import com.example.model.CellAddress
import com.example.model.CellAlign
import com.example.model.CellData
import com.example.model.CellStyle
import com.example.model.QrisInsertMode
import com.example.model.SheetData
import com.example.model.WorkbookData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpreadsheetUiState(
    val workbook: WorkbookData? = null,
    val activeCell: CellAddress = CellAddress(0, 0),
    val formulaBarText: String = "",
    val qrisInsertMode: QrisInsertMode = QrisInsertMode.SMART_SUMMARY,
    val autoAdvanceRow: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val lastScannedCell: CellAddress? = null
)

class SpreadsheetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WorkbookRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(SpreadsheetUiState())
    val uiState: StateFlow<SpreadsheetUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<WorkbookData>()
    private val redoStack = ArrayDeque<WorkbookData>()

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        qrisInsertMode = settings.qrisInsertMode,
                        autoAdvanceRow = settings.autoAdvanceRow
                    )
                }
            }
        }
    }

    fun importXlsx(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val wb = repository.importFromUri(uri)
                undoStack.clear()
                redoStack.clear()
                val initialCell = CellAddress(0, 0)
                val initialRaw = wb.activeSheet.getCell(initialCell).rawInput
                _uiState.update {
                    it.copy(
                        workbook = wb,
                        activeCell = initialCell,
                        formulaBarText = initialRaw,
                        canUndo = false,
                        canRedo = false,
                        isLoading = false,
                        statusMessage = "Berhasil impor ${wb.fileName} (${wb.sheets.size} sheet)",
                        lastScannedCell = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Gagal impor XLSX: ${e.message ?: "Format tidak dikenali"}"
                    )
                }
            }
        }
    }

    fun exportXlsx(uri: Uri) {
        val currentWb = _uiState.value.workbook ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                repository.exportToUri(uri, currentWb)
                val savedWb = currentWb.copy(isModified = false)
                _uiState.update {
                    it.copy(
                        workbook = savedWb,
                        isLoading = false,
                        statusMessage = "Berhasil ekspor ${savedWb.fileName} ke penyimpanan!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Gagal ekspor XLSX: ${e.message ?: "Terjadi kesalahan"}"
                    )
                }
            }
        }
    }

    fun openBlankWorkbook() {
        val wb = repository.createBlankWorkbook()
        loadFreshWorkbook(wb, "Workbook baru siap digunakan")
    }

    fun openSampleWorkbook() {
        val wb = repository.createSampleWorkbook()
        loadFreshWorkbook(wb, "Contoh Rekap QRIS dimuat lengkap dengan rumus")
    }

    fun closeWorkbookToHome() {
        undoStack.clear()
        redoStack.clear()
        _uiState.update {
            it.copy(
                workbook = null,
                canUndo = false,
                canRedo = false,
                lastScannedCell = null
            )
        }
    }

    private fun loadFreshWorkbook(wb: WorkbookData, msg: String) {
        undoStack.clear()
        redoStack.clear()
        val initialCell = CellAddress(0, 0)
        _uiState.update {
            it.copy(
                workbook = wb,
                activeCell = initialCell,
                formulaBarText = wb.activeSheet.getCell(initialCell).rawInput,
                canUndo = false,
                canRedo = false,
                statusMessage = msg,
                lastScannedCell = null
            )
        }
        viewModelScope.launch { repository.saveAutosave(wb) }
    }

    fun selectCell(address: CellAddress) {
        val wb = _uiState.value.workbook ?: return
        val raw = wb.activeSheet.getCell(address).rawInput
        _uiState.update {
            it.copy(
                activeCell = address,
                formulaBarText = raw
            )
        }
    }

    fun updateFormulaBarText(newText: String) {
        _uiState.update { it.copy(formulaBarText = newText) }
    }

    fun appendFormulaSnippet(snippet: String) {
        val current = _uiState.value.formulaBarText
        val updated = if (current.startsWith("=")) current + snippet else "=$snippet"
        _uiState.update { it.copy(formulaBarText = updated) }
    }

    fun commitFormulaBarEdit() {
        val state = _uiState.value
        commitCellValue(state.activeCell, state.formulaBarText)
    }

    fun commitCellValue(address: CellAddress, rawValue: String) {
        val wb = _uiState.value.workbook ?: return
        val sheet = wb.activeSheet
        val prevCell = sheet.getCell(address)
        val trimmed = rawValue.trimEnd()
        val isFormula = trimmed.startsWith("=")
        val updatedCell = prevCell.copy(
            rawInput = trimmed,
            formula = if (isFormula) trimmed.removePrefix("=") else null
        )

        val newCells = sheet.cells.toMutableMap()
        if (updatedCell.isEmpty) {
            newCells.remove(address)
        } else {
            newCells[address] = updatedCell
        }

        val updatedSheet = sheet.copy(
            cells = newCells,
            rowCount = maxOf(sheet.rowCount, address.row + 10),
            colCount = maxOf(sheet.colCount, address.col + 4)
        )
        applySheetMutation(wb, updatedSheet, focusCell = address)
    }

    fun applyCellFillColor(hexColor: String?) {
        val wb = _uiState.value.workbook ?: return
        val addr = _uiState.value.activeCell
        val sheet = wb.activeSheet
        val prevCell = sheet.getCell(addr)
        val newTextHex = if (hexColor != null && isDarkHex(hexColor)) "#FFFFFFFF" else null
        val newStyle = prevCell.style.copy(
            bgColorHex = hexColor,
            textColorHex = if (prevCell.style.textColorHex == "#FFFFFFFF" && hexColor == null) null else (newTextHex ?: prevCell.style.textColorHex)
        )
        val updatedCell = prevCell.copy(style = newStyle)
        val newCells = sheet.cells.toMutableMap()
        if (updatedCell.isEmpty) newCells.remove(addr) else newCells[addr] = updatedCell
        applySheetMutation(wb, sheet.copy(cells = newCells), focusCell = addr)
    }

    fun toggleCellBold() {
        val wb = _uiState.value.workbook ?: return
        val addr = _uiState.value.activeCell
        val sheet = wb.activeSheet
        val prev = sheet.getCell(addr)
        val updated = prev.copy(style = prev.style.copy(bold = !prev.style.bold))
        val newCells = sheet.cells.toMutableMap()
        if (updated.isEmpty) newCells.remove(addr) else newCells[addr] = updated
        applySheetMutation(wb, sheet.copy(cells = newCells), focusCell = addr)
    }

    fun setCellAlignment(align: CellAlign) {
        val wb = _uiState.value.workbook ?: return
        val addr = _uiState.value.activeCell
        val sheet = wb.activeSheet
        val prev = sheet.getCell(addr)
        val updated = prev.copy(style = prev.style.copy(horizontalAlignment = align))
        val newCells = sheet.cells.toMutableMap()
        if (updated.isEmpty) newCells.remove(addr) else newCells[addr] = updated
        applySheetMutation(wb, sheet.copy(cells = newCells), focusCell = addr)
    }

    fun clearActiveCell() {
        val wb = _uiState.value.workbook ?: return
        val addr = _uiState.value.activeCell
        val sheet = wb.activeSheet
        val newCells = sheet.cells.toMutableMap()
        newCells.remove(addr)
        applySheetMutation(wb, sheet.copy(cells = newCells), focusCell = addr)
    }

    fun addRow() {
        val wb = _uiState.value.workbook ?: return
        val sheet = wb.activeSheet
        val updated = sheet.copy(rowCount = sheet.rowCount + 10)
        applySheetMutation(wb, updated, focusCell = _uiState.value.activeCell, msg = "10 baris ditambahkan")
    }

    fun addColumn() {
        val wb = _uiState.value.workbook ?: return
        val sheet = wb.activeSheet
        val updated = sheet.copy(colCount = sheet.colCount + 3)
        applySheetMutation(wb, updated, focusCell = _uiState.value.activeCell, msg = "3 kolom ditambahkan")
    }

    fun adjustActiveColumnWidth(deltaDp: Float) {
        val wb = _uiState.value.workbook ?: return
        val col = _uiState.value.activeCell.col
        val sheet = wb.activeSheet
        val currentWidth = sheet.getColWidthDp(col)
        val nextWidth = (currentWidth + deltaDp).coerceIn(64f, 280f)
        val newWidths = sheet.colWidthsDp.toMutableMap()
        newWidths[col] = nextWidth
        applySheetMutation(wb, sheet.copy(colWidthsDp = newWidths), focusCell = _uiState.value.activeCell)
    }

    fun switchSheet(index: Int) {
        val wb = _uiState.value.workbook ?: return
        if (index !in wb.sheets.indices) return
        val nextWb = wb.copy(activeSheetIndex = index)
        val targetCell = CellAddress(0, 0)
        _uiState.update {
            it.copy(
                workbook = nextWb,
                activeCell = targetCell,
                formulaBarText = nextWb.activeSheet.getCell(targetCell).rawInput
            )
        }
    }

    fun addSheet() {
        val wb = _uiState.value.workbook ?: return
        var idx = wb.sheets.size + 1
        while (wb.sheets.any { it.name.equals("Sheet$idx", ignoreCase = true) }) idx++
        val newSheet = SheetData(name = "Sheet$idx")
        pushUndo(wb)
        val updatedWb = wb.copy(
            sheets = wb.sheets + newSheet,
            activeSheetIndex = wb.sheets.size,
            isModified = true
        )
        val cell = CellAddress(0, 0)
        _uiState.update {
            it.copy(
                workbook = updatedWb,
                activeCell = cell,
                formulaBarText = "",
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                statusMessage = "Sheet '${newSheet.name}' ditambahkan"
            )
        }
        viewModelScope.launch { repository.saveAutosave(updatedWb) }
    }

    fun renameActiveSheet(newName: String) {
        val wb = _uiState.value.workbook ?: return
        val cleaned = newName.trim().take(31)
        if (cleaned.isEmpty()) return
        pushUndo(wb)
        val sheets = wb.sheets.toMutableList()
        sheets[wb.activeSheetIndex] = sheets[wb.activeSheetIndex].copy(name = cleaned)
        val evaluated = FormulaEvaluator.evaluateWorkbook(wb.copy(sheets = sheets, isModified = true))
        _uiState.update {
            it.copy(
                workbook = evaluated,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                statusMessage = "Nama sheet diubah ke '$cleaned'"
            )
        }
        viewModelScope.launch { repository.saveAutosave(evaluated) }
    }

    fun setQrisInsertMode(mode: QrisInsertMode) {
        _uiState.update { it.copy(qrisInsertMode = mode) }
        viewModelScope.launch { repository.saveQrisMode(mode) }
    }

    fun setAutoAdvanceRow(enabled: Boolean) {
        _uiState.update { it.copy(autoAdvanceRow = enabled) }
        viewModelScope.launch { repository.saveAutoAdvance(enabled) }
    }

    fun onQrisScanned(parsed: ParsedQrisResult) {
        val state = _uiState.value
        val wb = state.workbook ?: return
        val target = state.activeCell
        val sheet = wb.activeSheet
        val newCells = sheet.cells.toMutableMap()

        when (state.qrisInsertMode) {
            QrisInsertMode.AUTO_ROW -> {
                val merchant = parsed.merchantName ?: parsed.rawPayload
                val amountStr = parsed.numericAmount?.let {
                    FormulaEvaluator.formatNumber(it)
                } ?: parsed.amount ?: "0"
                val detail = listOfNotNull(parsed.merchantCity, parsed.nmid ?: parsed.merchantId)
                    .joinToString(" • ")

                val prev0 = sheet.getCell(target)
                newCells[target] = prev0.copy(rawInput = merchant, formula = null)

                val c1 = CellAddress(target.row, target.col + 1)
                val prev1 = sheet.getCell(c1)
                newCells[c1] = prev1.copy(rawInput = amountStr, formula = null)

                if (detail.isNotBlank()) {
                    val c2 = CellAddress(target.row, target.col + 2)
                    val prev2 = sheet.getCell(c2)
                    newCells[c2] = prev2.copy(rawInput = detail, formula = null)
                }
            }
            else -> {
                val valueToInsert = when (state.qrisInsertMode) {
                    QrisInsertMode.SMART_SUMMARY -> parsed.formatSummary()
                    QrisInsertMode.MERCHANT_NAME -> parsed.merchantName ?: parsed.rawPayload
                    QrisInsertMode.AMOUNT_ONLY -> parsed.numericAmount?.let {
                        FormulaEvaluator.formatNumber(it)
                    } ?: parsed.amount ?: parsed.rawPayload
                    QrisInsertMode.NMID_ONLY -> parsed.nmid ?: parsed.merchantId ?: parsed.rawPayload
                    QrisInsertMode.RAW_PAYLOAD -> parsed.rawPayload
                    QrisInsertMode.AUTO_ROW -> parsed.formatSummary()
                }
                val prev = sheet.getCell(target)
                newCells[target] = prev.copy(rawInput = valueToInsert, formula = null)
            }
        }

        val updatedSheet = sheet.copy(
            cells = newCells,
            rowCount = maxOf(sheet.rowCount, target.row + 10),
            colCount = maxOf(sheet.colCount, target.col + 5)
        )

        val nextCell = if (state.autoAdvanceRow) {
            CellAddress(row = target.row + 1, col = target.col)
        } else {
            target
        }

        val labelInfo = parsed.merchantName?.let { " ($it)" } ?: ""
        applySheetMutation(
            wb = wb,
            updatedSheet = updatedSheet,
            focusCell = nextCell,
            msg = "QRIS masuk ke sel ${target.toA1()}$labelInfo",
            scannedCell = target
        )
    }

    fun undo() {
        val current = _uiState.value.workbook ?: return
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current)
        val raw = prev.activeSheet.getCell(_uiState.value.activeCell).rawInput
        _uiState.update {
            it.copy(
                workbook = prev,
                formulaBarText = raw,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                statusMessage = "Perubahan diurungkan"
            )
        }
        viewModelScope.launch { repository.saveAutosave(prev) }
    }

    fun redo() {
        val current = _uiState.value.workbook ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current)
        val raw = next.activeSheet.getCell(_uiState.value.activeCell).rawInput
        _uiState.update {
            it.copy(
                workbook = next,
                formulaBarText = raw,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                statusMessage = "Perubahan diulangi"
            )
        }
        viewModelScope.launch { repository.saveAutosave(next) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun pushUndo(wb: WorkbookData) {
        if (undoStack.size >= 30) undoStack.removeFirst()
        undoStack.addLast(wb)
        redoStack.clear()
    }

    private fun applySheetMutation(
        wb: WorkbookData,
        updatedSheet: SheetData,
        focusCell: CellAddress,
        msg: String? = null,
        scannedCell: CellAddress? = _uiState.value.lastScannedCell
    ) {
        pushUndo(wb)
        val sheets = wb.sheets.toMutableList()
        sheets[wb.activeSheetIndex] = updatedSheet
        val evaluated = FormulaEvaluator.evaluateWorkbook(
            wb.copy(sheets = sheets, isModified = true)
        )
        val newFormulaText = evaluated.activeSheet.getCell(focusCell).rawInput
        _uiState.update {
            it.copy(
                workbook = evaluated,
                activeCell = focusCell,
                formulaBarText = newFormulaText,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                statusMessage = msg ?: it.statusMessage,
                lastScannedCell = scannedCell
            )
        }
        viewModelScope.launch { repository.saveAutosave(evaluated) }
    }

    private fun isDarkHex(hex: String): Boolean {
        val cleaned = hex.removePrefix("#")
        val rgb = if (cleaned.length == 8) cleaned.substring(2) else cleaned
        if (rgb.length != 6) return false
        val r = rgb.substring(0, 2).toIntOrNull(16) ?: return false
        val g = rgb.substring(2, 4).toIntOrNull(16) ?: return false
        val b = rgb.substring(4, 6).toIntOrNull(16) ?: return false
        val lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return lum < 0.5
    }
}
