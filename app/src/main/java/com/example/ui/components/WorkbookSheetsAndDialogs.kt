package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.CellAddress
import com.example.model.CellAlign
import com.example.model.CellData
import com.example.ui.theme.CellColorPalette
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.contrastingTextColor

@Composable
fun WelcomeHomeView(
    onImportClick: () -> Unit,
    onOpenSampleClick: () -> Unit,
    onCreateBlankClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.OfflinePin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.label_offline_ready),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.workbook_empty_hero_1795447107135),
                    contentDescription = "Ilustrasi SheetQR",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.empty_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onImportClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("home_import_xlsx_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Impor File Excel (.XLSX)",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenSampleClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("home_sample_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buka Contoh", maxLines = 1)
                }

                OutlinedButton(
                    onClick = onCreateBlankClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("home_blank_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buat Baru", maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeatureRow("Rumus Excel Otomatis", "SUM, AVERAGE, IF, COUNTIF, VLOOKUP, antar-sheet")
                    FeatureRow("Warna & Format Sel", "16 warna sel, teks tebal, perataan, ekspor ke XLSX")
                    FeatureRow("Scan QRIS ke Sel Aktif", "Kamera & galeri offline, otomatis masuk ke sel terpilih")
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CellStyleBottomSheet(
    activeCell: CellAddress,
    cellData: CellData,
    currentColWidthDp: Float,
    onSelectBgColor: (String?) -> Unit,
    onToggleBold: () -> Unit,
    onSelectAlign: (CellAlign) -> Unit,
    onAdjustColWidth: (Float) -> Unit,
    onClearCell: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("cell_style_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = activeCell.toA1(),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Warna & Gaya Sel",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Tutup")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Warna Latar Sel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CellColorPalette.forEach { swatch ->
                    val isSelected = cellData.style.bgColorHex.equals(swatch.hex, ignoreCase = true) ||
                        (swatch.hex == null && cellData.style.bgColorHex == null)

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (swatch.hex == null) MaterialTheme.colorScheme.surfaceVariant
                                else swatch.previewColor
                            )
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .clickable { onSelectBgColor(swatch.hex) }
                            .testTag("color_swatch_${swatch.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (swatch.hex == null) {
                            Icon(
                                imageVector = Icons.Filled.FormatColorReset,
                                contentDescription = swatch.name,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        } else if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = swatch.name,
                                tint = contrastingTextColor(swatch.previewColor),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Format Teks & Perataan",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = cellData.style.bold,
                    onClick = onToggleBold,
                    label = { Text("Tebal") },
                    leadingIcon = { Icon(Icons.Filled.FormatBold, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("toggle_bold_chip")
                )
                FilterChip(
                    selected = cellData.style.horizontalAlignment == CellAlign.LEFT,
                    onClick = { onSelectAlign(CellAlign.LEFT) },
                    label = { Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, contentDescription = "Rata Kiri", modifier = Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = cellData.style.horizontalAlignment == CellAlign.CENTER,
                    onClick = { onSelectAlign(CellAlign.CENTER) },
                    label = { Icon(Icons.Filled.FormatAlignCenter, contentDescription = "Rata Tengah", modifier = Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = cellData.style.horizontalAlignment == CellAlign.RIGHT,
                    onClick = { onSelectAlign(CellAlign.RIGHT) },
                    label = { Icon(Icons.AutoMirrored.Filled.FormatAlignRight, contentDescription = "Rata Kanan", modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Lebar Kolom ${CellAddress.colIndexToName(activeCell.col)}", style = MaterialTheme.typography.labelLarge)
                    Text("${currentColWidthDp.toInt()} dp", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onAdjustColWidth(-20f) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("-")
                    }
                    OutlinedButton(
                        onClick = { onAdjustColWidth(20f) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("+")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = {
                    onClearCell()
                    onDismiss()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("clear_cell_button")
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hapus Isi & Warna Sel ${activeCell.toA1()}")
            }
        }
    }
}

@Composable
fun CellEditorDialog(
    activeCell: CellAddress,
    initialText: String,
    evaluatedPreview: String,
    hasFormula: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(activeCell, initialText) { mutableStateOf(initialText) }
    val snippets = listOf("=SUM(", "=AVERAGE(", "=IF(", "=COUNT(", "=MAX(", "=MIN(")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = activeCell.toA1(),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("Edit Sel & Rumus", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Nilai atau Rumus (=...)") },
                    placeholder = { Text("Contoh: 50000 atau =SUM(D2:D5)") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = if (text.startsWith("=")) JetBrainsMonoFamily else MaterialTheme.typography.bodyLarge.fontFamily
                    ),
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cell_dialog_input")
                )

                if (hasFormula && evaluatedPreview.isNotEmpty()) {
                    Text(
                        text = "Hasil saat ini: $evaluatedPreview",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "Rumus Cepat:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    snippets.forEach { snippet ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                text = if (text.startsWith("=")) {
                                    text + snippet.removePrefix("=")
                                } else {
                                    snippet
                                }
                            },
                            label = {
                                Text(
                                    text = snippet,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Functions, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text) },
                modifier = Modifier.testTag("cell_dialog_save")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun RenameSheetDialog(
    currentName: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah Nama Sheet") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Nama Sheet") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onRename(name) }) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
