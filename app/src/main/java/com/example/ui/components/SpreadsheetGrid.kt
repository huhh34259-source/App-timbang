package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CellAddress
import com.example.model.CellAlign
import com.example.model.CellData
import com.example.model.SheetData
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.contrastingTextColor
import com.example.ui.theme.parseHexColorOrNull

@Composable
fun SpreadsheetGrid(
    sheet: SheetData,
    activeCell: CellAddress,
    lastScannedCell: CellAddress?,
    onSelectCell: (CellAddress) -> Unit,
    onDoubleTapCell: (CellAddress) -> Unit,
    modifier: Modifier = Modifier
) {
    val horizScrollState: ScrollState = rememberScrollState()
    val vertListState = rememberLazyListState()
    val rowHeaderWidth = 46.dp
    val colHeaderHeight = 32.dp

    val gridLineColor = MaterialTheme.colorScheme.outline
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    val headerTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val errorColor = MaterialTheme.colorScheme.error
    val defaultCellBg = MaterialTheme.colorScheme.surface
    val defaultCellText = MaterialTheme.colorScheme.onSurface

    val colWidths = remember(sheet.colCount, sheet.colWidthsDp) {
        (0 until sheet.colCount).map { col -> sheet.getColWidthDp(col).dp }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(defaultCellBg)
            .testTag("spreadsheet_grid")
    ) {
        // Sticky Column Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(colHeaderHeight)
                .background(headerBg)
        ) {
            // Top-left corner cell
            Box(
                modifier = Modifier
                    .width(rowHeaderWidth)
                    .fillMaxHeight()
                    .drawBehind {
                        drawLine(
                            color = gridLineColor,
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = gridLineColor,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = activeCell.toA1(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = primaryColor,
                    maxLines = 1
                )
            }

            // Scrollable Column Letters (A, B, C...)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizScrollState)
            ) {
                for (col in 0 until sheet.colCount) {
                    val isColSelected = activeCell.col == col
                    Box(
                        modifier = Modifier
                            .width(colWidths[col])
                            .fillMaxHeight()
                            .background(if (isColSelected) primaryContainer.copy(alpha = 0.65f) else headerBg)
                            .drawBehind {
                                drawLine(
                                    color = gridLineColor,
                                    start = Offset(size.width, 0f),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                                drawLine(
                                    color = gridLineColor,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = if (isColSelected) 2.dp.toPx() else 1.dp.toPx()
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = CellAddress.colIndexToName(col),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = if (isColSelected) FontWeight.Bold else FontWeight.SemiBold
                            ),
                            color = if (isColSelected) primaryColor else headerTextColor
                        )
                    }
                }
            }
        }

        // Scrollable Rows
        LazyColumn(
            state = vertListState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                count = sheet.rowCount,
                key = { it }
            ) { rowIdx ->
                val rowHeight = sheet.getRowHeightDp(rowIdx).dp
                val isRowSelected = activeCell.row == rowIdx

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                ) {
                    // Sticky Row Header (1, 2, 3...)
                    Box(
                        modifier = Modifier
                            .width(rowHeaderWidth)
                            .fillMaxHeight()
                            .background(if (isRowSelected) primaryContainer.copy(alpha = 0.65f) else headerBg)
                            .drawBehind {
                                drawLine(
                                    color = gridLineColor,
                                    start = Offset(size.width, 0f),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = if (isRowSelected) 2.dp.toPx() else 1.dp.toPx()
                                )
                                drawLine(
                                    color = gridLineColor,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (rowIdx + 1).toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = if (isRowSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isRowSelected) primaryColor else headerTextColor
                        )
                    }

                    // Row Cells synchronized horizontally
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(horizScrollState)
                    ) {
                        var colIdx = 0
                        while (colIdx < sheet.colCount) {
                            val merge = sheet.mergedCells.firstOrNull { it.contains(rowIdx, colIdx) }
                            if (merge != null && !merge.isTopLeft(rowIdx, colIdx)) {
                                // Skip covered cells if same row; if spanned from row above, render empty placeholder width
                                if (merge.startRow < rowIdx) {
                                    Box(
                                        modifier = Modifier
                                            .width(colWidths[colIdx])
                                            .fillMaxHeight()
                                            .background(defaultCellBg)
                                    )
                                }
                                colIdx++
                                continue
                            }

                            val spanCols = if (merge != null) {
                                (merge.endCol - merge.startCol + 1).coerceAtMost(sheet.colCount - colIdx)
                            } else 1
                            val totalWidth = (colIdx until (colIdx + spanCols))
                                .fold(0.dp) { acc, c -> acc + colWidths[c] }

                            val addr = CellAddress(rowIdx, colIdx)
                            val cell = sheet.getCell(addr)
                            val isSelected = activeCell == addr
                            val isLastScanned = lastScannedCell == addr

                            GridCellItem(
                                address = addr,
                                cell = cell,
                                width = totalWidth,
                                isSelected = isSelected,
                                isLastScanned = isLastScanned,
                                gridLineColor = gridLineColor,
                                defaultBg = defaultCellBg,
                                defaultTextColor = defaultCellText,
                                activeBorderColor = primaryColor,
                                errorColor = errorColor,
                                onTap = { onSelectCell(addr) },
                                onDoubleTap = { onDoubleTapCell(addr) }
                            )
                            colIdx += spanCols
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCellItem(
    address: CellAddress,
    cell: CellData,
    width: androidx.compose.ui.unit.Dp,
    isSelected: Boolean,
    isLastScanned: Boolean,
    gridLineColor: Color,
    defaultBg: Color,
    defaultTextColor: Color,
    activeBorderColor: Color,
    errorColor: Color,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit
) {
    val parsedBg = parseHexColorOrNull(cell.style.bgColorHex)
    val bgColor = parsedBg ?: defaultBg
    val parsedText = parseHexColorOrNull(cell.style.textColorHex)
    val textColor = when {
        cell.isError -> errorColor
        parsedText != null -> parsedText
        parsedBg != null -> contrastingTextColor(parsedBg)
        else -> defaultTextColor
    }

    val isNumericDisplay = cell.numericValue != null && !cell.isError
    val textAlign = when (cell.style.horizontalAlignment) {
        CellAlign.LEFT -> TextAlign.Start
        CellAlign.CENTER -> TextAlign.Center
        CellAlign.RIGHT -> TextAlign.End
        CellAlign.AUTO -> if (isNumericDisplay) TextAlign.End else TextAlign.Start
    }
    val boxAlign = when (textAlign) {
        TextAlign.Center -> Alignment.Center
        TextAlign.End -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }

    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(bgColor)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, activeBorderColor)
                } else {
                    Modifier.drawBehind {
                        drawLine(
                            color = gridLineColor,
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 0.8.dp.toPx()
                        )
                        drawLine(
                            color = gridLineColor,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 0.8.dp.toPx()
                        )
                    }
                }
            )
            .combinedClickable(
                onClick = onTap,
                onDoubleClick = onDoubleTap
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("cell_${address.toA1()}"),
        contentAlignment = boxAlign
    ) {
        if (cell.hasFormula) {
            val fxBadgeColor = activeBorderColor
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(7.dp)
            ) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(path, color = fxBadgeColor)
            }
        }

        if (isLastScanned && !isSelected) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(6.dp)
            ) {
                drawCircle(color = Color(0xFFB86314))
            }
        }

        Text(
            text = cell.cachedDisplay,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (isNumericDisplay) JetBrainsMonoFamily else MaterialTheme.typography.bodyMedium.fontFamily,
                fontSize = 12.5.sp,
                fontWeight = if (cell.style.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (cell.style.italic) FontStyle.Italic else FontStyle.Normal
            ),
            color = textColor,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
