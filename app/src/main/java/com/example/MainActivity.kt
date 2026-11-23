package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.SpreadsheetViewModel
import com.example.ui.screens.QrisScannerScreen
import com.example.ui.screens.WorkbookScreen
import com.example.ui.theme.SheetQrTheme

const val ROUTE_WORKBOOK = "workbook"
const val ROUTE_SCANNER = "scanner"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SheetQrTheme {
                SheetQrApp()
            }
        }
    }
}

@Composable
fun SheetQrApp(
    viewModel: SpreadsheetViewModel = viewModel()
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val importXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importXlsx(uri)
        }
    }

    val exportXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        if (uri != null) {
            viewModel.exportXlsx(uri)
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_WORKBOOK
    ) {
        composable(ROUTE_WORKBOOK) {
            WorkbookScreen(
                uiState = uiState,
                onImportXlsxClick = {
                    importXlsxLauncher.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                },
                onExportXlsxClick = {
                    val suggestedName = uiState.workbook?.fileName?.let { name ->
                        if (name.endsWith(".xlsx", ignoreCase = true)) name else "$name.xlsx"
                    } ?: "SheetQR_Export.xlsx"
                    exportXlsxLauncher.launch(suggestedName)
                },
                onOpenSampleClick = viewModel::openSampleWorkbook,
                onCreateBlankClick = viewModel::openBlankWorkbook,
                onCloseToHome = viewModel::closeWorkbookToHome,
                onSelectCell = viewModel::selectCell,
                onFormulaBarChange = viewModel::updateFormulaBarText,
                onFormulaBarCommit = viewModel::commitFormulaBarEdit,
                onAppendFormulaSnippet = viewModel::appendFormulaSnippet,
                onCommitCellValue = viewModel::commitCellValue,
                onApplyCellColor = viewModel::applyCellFillColor,
                onToggleBold = viewModel::toggleCellBold,
                onSelectAlign = viewModel::setCellAlignment,
                onAdjustColWidth = viewModel::adjustActiveColumnWidth,
                onClearCell = viewModel::clearActiveCell,
                onAddRow = viewModel::addRow,
                onAddColumn = viewModel::addColumn,
                onSwitchSheet = viewModel::switchSheet,
                onAddSheet = viewModel::addSheet,
                onRenameActiveSheet = viewModel::renameActiveSheet,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onOpenScanner = {
                    navController.navigate(ROUTE_SCANNER)
                },
                onClearStatus = viewModel::clearStatusMessage,
                onClearError = viewModel::clearErrorMessage
            )
        }

        composable(ROUTE_SCANNER) {
            QrisScannerScreen(
                activeCell = uiState.activeCell,
                qrisInsertMode = uiState.qrisInsertMode,
                autoAdvanceRow = uiState.autoAdvanceRow,
                onSelectMode = viewModel::setQrisInsertMode,
                onToggleAutoAdvance = viewModel::setAutoAdvanceRow,
                onQrisDetected = viewModel::onQrisScanned,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
