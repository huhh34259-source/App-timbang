package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CellAddress
import com.example.ui.SpreadsheetUiState
import com.example.ui.components.CellEditorDialog
import com.example.ui.components.CellStyleBottomSheet
import com.example.ui.components.RenameSheetDialog
import com.example.ui.components.SpreadsheetGrid
import com.example.ui.components.WelcomeHomeView
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.parseHexColorOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkbookScreen(
    uiState: SpreadsheetUiState,
    onImportXlsxClick: () -> Unit,
    onExportXlsxClick: () -> Unit,
    onOpenSampleClick: () -> Unit,
    onCreateBlankClick: () -> Unit,
    onCloseToHome: () -> Unit,
    onSelectCell: (CellAddress) -> Unit,
    onFormulaBarChange: (String) -> Unit,
    onFormulaBarCommit: () -> Unit,
    onAppendFormulaSnippet: (String) -> Unit,
    onCommitCellValue: (CellAddress, String) -> Unit,
    onApplyCellColor: (String?) -> Unit,
    onToggleBold: () -> Unit,
    onSelectAlign: (com.example.model.CellAlign) -> Unit,
    onAdjustColWidth: (Float) -> Unit,
    onClearCell: () -> Unit,
    onAddRow: () -> Unit,
    onAddColumn: () -> Unit,
    onSwitchSheet: (Int) -> Unit,
    onAddSheet: () -> Unit,
    onRenameActiveSheet: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenScanner: () -> Unit,
    onClearStatus: () -> Unit,
    onClearError: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    var showStyleSheet by remember { mutableStateOf(false) }
    var showCellEditorDialog by remember { mutableStateOf(false) }
    var showRenameSheetDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onClearStatus()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        val err = uiState.errorMessage
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            onClearError()
        }
    }

    val workbook = uiState.workbook

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    if (workbook == null) {
                        Column {
                            Text(
                                text = "SheetQR",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "XLSX Offline & Scanner QRIS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = workbook.fileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (workbook.isModified) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Diedit",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Offline • ${workbook.sheets.size} Sheet",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (workbook != null) {
                        IconButton(
                            onClick = onUndo,
                            enabled = uiState.canUndo,
                            modifier = Modifier.testTag("undo_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Urungkan")
                        }
                        IconButton(
                            onClick = onRedo,
                            enabled = uiState.canRedo,
                            modifier = Modifier.testTag("redo_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Ulangi")
                        }
                        IconButton(
                            onClick = onExportXlsxClick,
                            modifier = Modifier.testTag("export_xlsx_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileDownload,
                                contentDescription = "Ekspor XLSX",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.testTag("more_menu_button")
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Menu lainnya")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Impor File XLSX Lain") },
                                    leadingIcon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onImportXlsxClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ekspor sebagai XLSX") },
                                    leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onExportXlsxClick()
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Tambah 10 Baris") },
                                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onAddRow()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Tambah 3 Kolom") },
                                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onAddColumn()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ubah Nama Sheet Aktif") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showRenameSheetDialog = true
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Buka Contoh Rekap QRIS") },
                                    onClick = {
                                        showOverflowMenu = false
                                        onOpenSampleClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Buat Workbook Baru") },
                                    onClick = {
                                        showOverflowMenu = false
                                        onCreateBlankClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Tutup ke Beranda") },
                                    leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onCloseToHome()
                                    }
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = onImportXlsxClick) {
                            Icon(Icons.Filled.UploadFile, contentDescription = "Impor XLSX")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (workbook == null) {
                WelcomeHomeView(
                    onImportClick = onImportXlsxClick,
                    onOpenSampleClick = onOpenSampleClick,
                    onCreateBlankClick = onCreateBlankClick,
                    modifier = Modifier.weight(1f)
                )
            } else {
                val activeSheet = workbook.activeSheet
                val activeCellData = activeSheet.getCell(uiState.activeCell)

                // Formula Bar
                FormulaBar(
                    activeCell = uiState.activeCell,
                    text = uiState.formulaBarText,
                    hasFormula = activeCellData.hasFormula,
                    evaluatedPreview = activeCellData.cachedDisplay,
                    onTextChange = onFormulaBarChange,
                    onCommit = {
                        onFormulaBarCommit()
                        focusManager.clearFocus()
                    },
                    onAppendSnippet = onAppendFormulaSnippet,
                    onOpenFullEditor = { showCellEditorDialog = true }
                )

                // Main Spreadsheet Grid
                SpreadsheetGrid(
                    sheet = activeSheet,
                    activeCell = uiState.activeCell,
                    lastScannedCell = uiState.lastScannedCell,
                    onSelectCell = { addr ->
                        if (uiState.formulaBarText != activeCellData.rawInput) {
                            onFormulaBarCommit()
                        }
                        onSelectCell(addr)
                    },
                    onDoubleTapCell = { addr ->
                        onSelectCell(addr)
                        showCellEditorDialog = true
                    },
                    modifier = Modifier.weight(1f)
                )

                // Sheet Tabs Strip
                SheetTabsBar(
                    sheetNames = workbook.sheets.map { it.name },
                    activeIndex = workbook.activeSheetIndex,
                    onSelectSheet = onSwitchSheet,
                    onAddSheet = onAddSheet,
                    onLongClickActive = { showRenameSheetDialog = true }
                )

                // Bottom Action Dock
                BottomSpreadsheetToolbar(
                    activeCell = uiState.activeCell,
                    activeCellBgHex = activeCellData.style.bgColorHex,
                    onOpenStyleSheet = { showStyleSheet = true },
                    onOpenEditor = { showCellEditorDialog = true },
                    onScanQris = {
                        focusManager.clearFocus()
                        onOpenScanner()
                    },
                    onClearCell = onClearCell
                )
            }
        }
    }

    if (workbook != null && showStyleSheet) {
        val activeSheet = workbook.activeSheet
        val activeCellData = activeSheet.getCell(uiState.activeCell)
        CellStyleBottomSheet(
            activeCell = uiState.activeCell,
            cellData = activeCellData,
            currentColWidthDp = activeSheet.getColWidthDp(uiState.activeCell.col),
            onSelectBgColor = onApplyCellColor,
            onToggleBold = onToggleBold,
            onSelectAlign = onSelectAlign,
            onAdjustColWidth = onAdjustColWidth,
            onClearCell = onClearCell,
            onDismiss = { showStyleSheet = false }
        )
    }

    if (workbook != null && showCellEditorDialog) {
        val activeCellData = workbook.activeSheet.getCell(uiState.activeCell)
        CellEditorDialog(
            activeCell = uiState.activeCell,
            initialText = activeCellData.rawInput,
            evaluatedPreview = activeCellData.cachedDisplay,
            hasFormula = activeCellData.hasFormula,
            onSave = { newText ->
                onCommitCellValue(uiState.activeCell, newText)
                showCellEditorDialog = false
            },
            onDismiss = { showCellEditorDialog = false }
        )
    }

    if (workbook != null && showRenameSheetDialog) {
        RenameSheetDialog(
            currentName = workbook.activeSheet.name,
            onRename = { newName ->
                onRenameActiveSheet(newName)
                showRenameSheetDialog = false
            },
            onDismiss = { showRenameSheetDialog = false }
        )
    }
}

@Composable
private fun FormulaBar(
    activeCell: CellAddress,
    text: String,
    hasFormula: Boolean,
    evaluatedPreview: String,
    onTextChange: (String) -> Unit,
    onCommit: () -> Unit,
    onAppendSnippet: (String) -> Unit,
    onOpenFullEditor: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clickable(onClick = onOpenFullEditor)
                        .testTag("active_cell_badge")
                ) {
                    Text(
                        text = activeCell.toA1(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Filled.Functions,
                    contentDescription = "Rumus",
                    tint = if (text.startsWith("=")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable {
                            if (!text.startsWith("=")) onTextChange("=$text")
                        }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = "Isi nilai atau =SUM(...)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = if (text.startsWith("=")) JetBrainsMonoFamily else MaterialTheme.typography.bodyMedium.fontFamily,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onCommit() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("formula_bar_input")
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onCommit,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("formula_bar_apply")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Terapkan",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(visible = text.startsWith("=")) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasFormula && evaluatedPreview.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Hasil: $evaluatedPreview",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    listOf("SUM(", "AVERAGE(", "IF(", "COUNT(", "MAX(", "MIN(").forEach { fn ->
                        FilterChip(
                            selected = false,
                            onClick = { onAppendSnippet(fn) },
                            label = {
                                Text(fn, fontFamily = JetBrainsMonoFamily, fontSize = 11.sp)
                            }
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun SheetTabsBar(
    sheetNames: List<String>,
    activeIndex: Int,
    onSelectSheet: (Int) -> Unit,
    onAddSheet: () -> Unit,
    onLongClickActive: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sheetNames.forEachIndexed { idx, name ->
                    val isActive = idx == activeIndex
                    Surface(
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        tonalElevation = if (isActive) 2.dp else 0.dp,
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (isActive) onLongClickActive() else onSelectSheet(idx)
                            }
                            .testTag("sheet_tab_$idx")
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onAddSheet)
                        .testTag("add_sheet_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Tambah Sheet",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Sheet",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomSpreadsheetToolbar(
    activeCell: CellAddress,
    activeCellBgHex: String?,
    onOpenStyleSheet: () -> Unit,
    onOpenEditor: () -> Unit,
    onScanQris: () -> Unit,
    onClearCell: () -> Unit
) {
    val swatchColor = parseHexColorOrNull(activeCellBgHex)

    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onOpenStyleSheet,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("color_cell_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Palette,
                        contentDescription = "Warna Sel",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    if (swatchColor != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(10.dp)
                                .clip(RoundedCornerShape(50))
                                .background(swatchColor)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                        )
                    }
                }
            }

            IconButton(
                onClick = onOpenEditor,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("edit_cell_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit Sel",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = onClearCell,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("quick_clear_cell_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Hapus Isi Sel",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onScanQris,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("scan_qris_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scan QRIS → ${activeCell.toA1()}",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
