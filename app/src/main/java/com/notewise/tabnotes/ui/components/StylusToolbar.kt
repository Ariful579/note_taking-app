package com.notewise.tabnotes.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notewise.tabnotes.data.model.PageTemplate
import com.notewise.tabnotes.data.model.PaperColor
import com.notewise.tabnotes.data.model.ToolType
import com.notewise.tabnotes.ui.theme.*

@Composable
fun StylusToolbar(
    selectedTool: ToolType,
    strokeColor: Long,
    strokeWidth: Float,
    selectedTemplate: PageTemplate,
    selectedPaperColor: Long,
    canUndo: Boolean,
    canRedo: Boolean,
    isStylusOnlyMode: Boolean,
    isMagicAiEnabled: Boolean,
    selectedShapeType: ToolType,
    saveStatusMessage: String,
    lastEraserType: ToolType = ToolType.ERASER_STROKE,
    isToolbarPinned: Boolean = true,
    onSelectTool: (ToolType) -> Unit,
    onSelectColor: (Long) -> Unit,
    onSelectWidth: (Float) -> Unit,
    onSelectTemplate: (PageTemplate) -> Unit,
    onSelectPaperColor: (Long) -> Unit,
    onToggleMagicAi: () -> Unit,
    onSelectShape: (ToolType) -> Unit,
    onExportClick: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearPage: () -> Unit,
    onToggleStylusMode: () -> Unit,
    onTogglePin: () -> Unit = {},
    onAddImage: () -> Unit = {},
    onImportPdf: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showWidthSlider by remember { mutableStateOf(false) }
    var showTemplateMenu by remember { mutableStateOf(false) }
    var showShapeMenu by remember { mutableStateOf(false) }
    var showEraserMenu by remember { mutableStateOf(false) }
    var showColorPaletteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val presetColors = listOf(
        0xFF0F172A, // Black
        0xFF1D4ED8, // Blue
        0xFFDC2626, // Red
        0xFF16A34A, // Green
        0xFF9333EA, // Purple
        0xFFFFE066, // Soft Yellow (Highlighter)
        0xFFF472B6, // Soft Pink (Highlighter)
        0xFF86EFAC  // Soft Mint (Highlighter)
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Left Toolset: Pen, Fountain, Highlighter, Dedicated Eraser, Magic AI, Shapes, Text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Pen
                    ToolButton(
                        icon = Icons.Default.Edit,
                        label = "Pen",
                        isSelected = selectedTool == ToolType.PEN,
                        onClick = { onSelectTool(ToolType.PEN) }
                    )

                    // Fountain Pen
                    ToolButton(
                        icon = Icons.Default.Brush,
                        label = "Fountain Pen",
                        isSelected = selectedTool == ToolType.FOUNTAIN_PEN,
                        onClick = { onSelectTool(ToolType.FOUNTAIN_PEN) }
                    )

                    // Highlighter (Semi-Transparent)
                    ToolButton(
                        icon = Icons.Default.BorderColor,
                        label = "Highlighter",
                        isSelected = selectedTool == ToolType.HIGHLIGHTER,
                        badgeColor = Color(0xFFFFE066),
                        onClick = {
                            onSelectTool(ToolType.HIGHLIGHTER)
                            if (strokeColor == 0xFF0F172A) {
                                onSelectColor(0xFFFFE066) // Auto-switch to pastel yellow
                            }
                        }
                    )

                    // Dedicated Eraser Button (with dropdown for Precision vs Stroke eraser)
                    Box {
                        val isEraserActive = selectedTool == ToolType.ERASER_STROKE || selectedTool == ToolType.ERASER_POINT
                        ToolButton(
                            icon = Icons.Default.AutoFixNormal,
                            label = if (selectedTool == ToolType.ERASER_POINT) "Precision Eraser" else "Eraser",
                            isSelected = isEraserActive,
                            badgeColor = if (selectedTool == ToolType.ERASER_POINT) Color(0xFF2563EB) else Color(0xFFDC2626),
                            onClick = {
                                if (!isEraserActive) {
                                    onSelectTool(lastEraserType)
                                } else {
                                    showEraserMenu = true
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = showEraserMenu,
                            onDismissRequest = { showEraserMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Precision Eraser (Point Erase)") },
                                onClick = {
                                    onSelectTool(ToolType.ERASER_POINT)
                                    showEraserMenu = false
                                },
                                leadingIcon = {
                                    if (selectedTool == ToolType.ERASER_POINT) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Stroke Eraser (Whole Stroke)") },
                                onClick = {
                                    onSelectTool(ToolType.ERASER_STROKE)
                                    showEraserMenu = false
                                },
                                leadingIcon = {
                                    if (selectedTool == ToolType.ERASER_STROKE) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )
                        }
                    }

                    // Magic Disappearing Pen (Fading Ink)
                    ToolButton(
                        icon = Icons.Default.AutoFixHigh,
                        label = "Magic Pen",
                        isSelected = selectedTool == ToolType.MAGIC_PEN,
                        badgeColor = Color(0xFF8B5CF6),
                        onClick = {
                            onSelectTool(ToolType.MAGIC_PEN)
                            Toast.makeText(context, "Magic Pen selected: Ink fades away in 2.5 seconds ✨", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // Magic AI Assistant Button (Toggles AI Smoothing & Shape Snapping)
                    FilledTonalIconButton(
                        onClick = {
                            onToggleMagicAi()
                            val willEnable = !isMagicAiEnabled
                            Toast.makeText(
                                context,
                                if (willEnable) "Magic AI: Shape Snapping & Smoothing Enabled ✨" else "Magic AI Smoothing Disabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isMagicAiEnabled) Color(0xFFE0E7FF) else Color.Transparent,
                            contentColor = if (isMagicAiEnabled) Color(0xFF4338CA) else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .size(38.dp)
                            .then(
                                if (isMagicAiEnabled) Modifier.border(1.5.dp, Color(0xFF6366F1), RoundedCornerShape(10.dp))
                                else Modifier
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Magic AI Assistant",
                            modifier = Modifier.size(20.dp),
                            tint = if (isMagicAiEnabled) Color(0xFF4F46E5) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Geometric Shape Drawing Mode Button
                    Box {
                        val isShapeActive = selectedTool in listOf(
                            ToolType.SHAPE_LINE,
                            ToolType.SHAPE_RECTANGLE,
                            ToolType.SHAPE_CIRCLE,
                            ToolType.SHAPE_TRIANGLE,
                            ToolType.SHAPE_ARROW
                        )

                        FilledTonalIconButton(
                            onClick = {
                                if (isShapeActive) {
                                    showShapeMenu = true
                                } else {
                                    onSelectTool(selectedShapeType)
                                    showShapeMenu = true
                                }
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (isShapeActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                contentColor = if (isShapeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = when (selectedShapeType) {
                                    ToolType.SHAPE_LINE -> Icons.Default.HorizontalRule
                                    ToolType.SHAPE_CIRCLE -> Icons.Default.Circle
                                    ToolType.SHAPE_ARROW -> Icons.Default.ArrowForward
                                    else -> Icons.Default.Square
                                },
                                contentDescription = "Geometric Shapes",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showShapeMenu,
                            onDismissRequest = { showShapeMenu = false }
                        ) {
                            Text(
                                "Geometric Shapes",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            DropdownMenuItem(
                                text = { Text("Rectangle") },
                                onClick = {
                                    onSelectShape(ToolType.SHAPE_RECTANGLE)
                                    showShapeMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Square, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Circle / Ellipse") },
                                onClick = {
                                    onSelectShape(ToolType.SHAPE_CIRCLE)
                                    showShapeMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Circle, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Straight Line") },
                                onClick = {
                                    onSelectShape(ToolType.SHAPE_LINE)
                                    showShapeMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.HorizontalRule, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Arrow") },
                                onClick = {
                                    onSelectShape(ToolType.SHAPE_ARROW)
                                    showShapeMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.ArrowForward, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Triangle") },
                                onClick = {
                                    onSelectShape(ToolType.SHAPE_TRIANGLE)
                                    showShapeMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.ChangeHistory, contentDescription = null) }
                            )
                        }
                    }

                    // Lasso / Selection Tool
                    ToolButton(
                        icon = Icons.Default.Gesture,
                        label = "Lasso Selection",
                        isSelected = selectedTool == ToolType.LASSO,
                        onClick = { onSelectTool(ToolType.LASSO) }
                    )

                    // Text Tool
                    ToolButton(
                        icon = Icons.Default.TextFields,
                        label = "Text",
                        isSelected = selectedTool == ToolType.TEXT,
                        onClick = { onSelectTool(ToolType.TEXT) }
                    )

                    // Insert Image Button
                    FilledTonalIconButton(
                        onClick = onAddImage,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Insert Image",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Import PDF Button
                    FilledTonalIconButton(
                        onClick = onImportPdf,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color(0xFFDC2626)
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Import PDF into notebook",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 2. Center Palette: Microsoft Word Style Pen Color Dropdown & Quick Swatches
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Microsoft Word Style Primary Pen Color Dropdown Trigger
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showColorPaletteDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Pen Color",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(width = 18.dp, height = 4.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(Color(strokeColor))
                                )
                            }
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Open Word Color Palette",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Word Quick Color Swatches (Square Word-style swatches)
                    val quickSwatches = listOf(0xFF0F172AL, 0xFF1D4ED8L, 0xFFDC2626L, 0xFF16A34AL, 0xFFFFE066L)
                    quickSwatches.forEach { colorHex ->
                        val isSelected = strokeColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(colorHex))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x33000000),
                                    shape = RoundedCornerShape(3.dp)
                                )
                                .clickable { onSelectColor(colorHex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                val isBright = ((Color(colorHex).red * 0.299f + Color(colorHex).green * 0.587f + Color(colorHex).blue * 0.114f) > 0.6f)
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (isBright) Color.Black else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Full Custom Color Palette Dialog Trigger
                    IconButton(
                        onClick = { showColorPaletteDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Custom Color Palette",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Stroke Width Button
                    FilterChip(
                        selected = showWidthSlider,
                        onClick = { showWidthSlider = !showWidthSlider },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(strokeWidth.coerceIn(3f, 14f).dp)
                                        .clip(CircleShape)
                                        .background(Color(strokeColor))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${strokeWidth.toInt()}px", fontSize = 11.sp)
                            }
                        }
                    )
                }

                // 3. Right Controls: Templates, Export, Undo, Redo, Clear
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Paper Template & Color Selector
                    Box {
                        IconButton(onClick = { showTemplateMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Page Template",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showTemplateMenu,
                            onDismissRequest = { showTemplateMenu = false }
                        ) {
                            Text(
                                "Page Template",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            PageTemplate.entries.forEach { template ->
                                DropdownMenuItem(
                                    text = { Text(template.displayName) },
                                    onClick = {
                                        onSelectTemplate(template)
                                        Toast.makeText(context, "Page Template: ${template.displayName}", Toast.LENGTH_SHORT).show()
                                        showTemplateMenu = false
                                    },
                                    leadingIcon = {
                                        if (selectedTemplate == template) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                            HorizontalDivider()
                            Text(
                                "Paper Tone",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            PaperColor.entries.forEach { color ->
                                DropdownMenuItem(
                                    text = { Text(color.displayName) },
                                    onClick = {
                                        onSelectPaperColor(color.hex)
                                        showTemplateMenu = false
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(Color(color.hex))
                                                .border(1.dp, Color.Gray, CircleShape)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Download / Share as PDF Button
                    IconButton(onClick = onExportClick) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Download / Share as PDF",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Stylus Palm Rejection Mode Toggle
                    IconButton(onClick = onToggleStylusMode) {
                        Icon(
                            imageVector = if (isStylusOnlyMode) Icons.Default.PanTool else Icons.Default.TouchApp,
                            contentDescription = "Stylus Only Mode",
                            tint = if (isStylusOnlyMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Undo
                    IconButton(onClick = onUndo, enabled = canUndo) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    }

                    // Redo
                    IconButton(onClick = onRedo, enabled = canRedo) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    }

                    // Pin / Unpin Toolbar Button
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = if (isToolbarPinned) "Unpin Toolbar" else "Pin Toolbar",
                            tint = if (isToolbarPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Clear Page
                    IconButton(onClick = onClearPage) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Page",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Expandable Width Slider Bar & Save Status
            AnimatedVisibility(visible = showWidthSlider) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Thickness:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(12.dp))
                        Slider(
                            value = strokeWidth,
                            onValueChange = onSelectWidth,
                            valueRange = 1f..32f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("${strokeWidth.toInt()} dp", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.width(24.dp))
                        Text(saveStatusMessage, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showColorPaletteDialog) {
        CustomColorPaletteDialog(
            currentColor = strokeColor,
            onColorSelected = {
                onSelectColor(it)
                showColorPaletteDialog = false
            },
            onDismissRequest = { showColorPaletteDialog = false }
        )
    }
}

@Composable
fun CustomColorPaletteDialog(
    currentColor: Long,
    onColorSelected: (Long) -> Unit,
    onDismissRequest: () -> Unit
) {
    // Microsoft Word Theme Colors: 10 columns x 6 rows
    // Row 1: Base Theme Colors
    // Rows 2-6: Tint and shade variations
    val themeColorMatrix = remember {
        listOf(
            // Row 1: Base Theme Colors
            listOf(0xFFFFFFFFL, 0xFF000000L, 0xFFEEECE1L, 0xFF1F497DL, 0xFF4F81BDL, 0xFFF79646L, 0xFFFFC000L, 0xFF9BBB59L, 0xFF4BACC6L, 0xFF8064A2L),
            // Row 2: Lighter 80%
            listOf(0xFFF2F2F2L, 0xFF7F7F7FL, 0xFFDDD9C4L, 0xFFDCE6F1L, 0xFFDBEEF4L, 0xFFFDEADAL, 0xFFFFF2CAL, 0xFFEBF1DDL, 0xFFDBEEF3L, 0xFFE5DFECL),
            // Row 3: Lighter 60%
            listOf(0xFFD9D9D9L, 0xFF595959L, 0xFFC4BD97L, 0xFFB8CCE4L, 0xFFB7DDE8L, 0xFFFBD5B5L, 0xFFFFE599L, 0xFFD7E3BCL, 0xFFB7DDE8L, 0xFFCCC1DAL),
            // Row 4: Lighter 40%
            listOf(0xFFBFBFBFL, 0xFF3F3F3FL, 0xFF948A54L, 0xFF95B3D7L, 0xFF92CDDCL, 0xFFFAC08FL, 0xFFFFD966L, 0xFFC3D69BL, 0xFF92CDDCL, 0xFFB2A2C7L),
            // Row 5: Darker 25%
            listOf(0xFFA6A6A6L, 0xFF262626L, 0xFF494429L, 0xFF366092L, 0xFF31859BL, 0xFFE36C09L, 0xFFD49B00L, 0xFF76933CL, 0xFF31859BL, 0xFF5F497AL),
            // Row 6: Darker 50%
            listOf(0xFF7F7F7FL, 0xFF0D0D0DL, 0xFF1D1B10L, 0xFF244062L, 0xFF205867L, 0xFF974806L, 0xFF8F6800L, 0xFF4F6128L, 0xFF1B6577L, 0xFF3F3151L)
        )
    }

    // Standard Colors: 10 solid classic colors
    val standardColors = remember {
        listOf(
            0xFFC00000L, // Dark Red
            0xFFFF0000L, // Red
            0xFFFFC000L, // Orange
            0xFFFFFF00L, // Yellow
            0xFF92D050L, // Light Green
            0xFF00B050L, // Green
            0xFF00B0F0L, // Light Blue
            0xFF0070C0L, // Blue
            0xFF002060L, // Dark Blue
            0xFF7030A0L  // Purple
        )
    }

    var redVal by remember(currentColor) { mutableFloatStateOf(((currentColor shr 16) and 0xFF).toFloat()) }
    var greenVal by remember(currentColor) { mutableFloatStateOf(((currentColor shr 8) and 0xFF).toFloat()) }
    var blueVal by remember(currentColor) { mutableFloatStateOf((currentColor and 0xFF).toFloat()) }
    var showCustomSliders by remember { mutableStateOf(false) }

    val composedColorLong = remember(redVal, greenVal, blueVal) {
        (0xFFL shl 24) or
        ((redVal.toInt() and 0xFF).toLong() shl 16) or
        ((greenVal.toInt() and 0xFF).toLong() shl 8) or
        (blueVal.toInt() and 0xFF).toLong()
    }

    val updateColor: (Long) -> Unit = { cLong ->
        redVal = ((cLong shr 16) and 0xFF).toFloat()
        greenVal = ((cLong shr 8) and 0xFF).toFloat()
        blueVal = (cLong and 0xFF).toFloat()
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Theme & Standard Colors", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Theme Colors Section (10 columns x 6 rows)
                Text(
                    "Theme Colors",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                themeColorMatrix.forEachIndexed { rowIndex, rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        rowColors.forEach { cLong ->
                            val isSelected = (composedColorLong and 0xFFFFFFL) == (cLong and 0xFFFFFFL)
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(cLong))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 0.8.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x33000000),
                                        shape = RoundedCornerShape(3.dp)
                                    )
                                    .clickable {
                                        updateColor(cLong)
                                        onColorSelected(cLong)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    val isBright = ((Color(cLong).red * 0.299f + Color(cLong).green * 0.587f + Color(cLong).blue * 0.114f) > 0.6f)
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (isBright) Color.Black else Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(if (rowIndex == 0) 6.dp else 2.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // 2. Standard Colors Section (1 row of 10 classic solid colors)
                Text(
                    "Standard Colors",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    standardColors.forEach { cLong ->
                        val isSelected = (composedColorLong and 0xFFFFFFL) == (cLong and 0xFFFFFFL)
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(cLong))
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.8.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x33000000),
                                    shape = RoundedCornerShape(3.dp)
                                )
                                .clickable {
                                    updateColor(cLong)
                                    onColorSelected(cLong)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                val isBright = ((Color(cLong).red * 0.299f + Color(cLong).green * 0.587f + Color(cLong).blue * 0.114f) > 0.6f)
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (isBright) Color.Black else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // 3. More Colors / Custom Sliders Expandable Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCustomSliders = !showCustomSliders }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "More Colors (Custom RGB Sliders)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (showCustomSliders) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = showCustomSliders) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        // Red slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("R", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(16.dp))
                            Slider(
                                value = redVal,
                                onValueChange = { redVal = it },
                                valueRange = 0f..255f,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${redVal.toInt()}", fontSize = 11.sp, modifier = Modifier.width(32.dp))
                        }

                        // Green slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("G", color = Color(0xFF16A34A), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(16.dp))
                            Slider(
                                value = greenVal,
                                onValueChange = { greenVal = it },
                                valueRange = 0f..255f,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${greenVal.toInt()}", fontSize = 11.sp, modifier = Modifier.width(32.dp))
                        }

                        // Blue slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("B", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(16.dp))
                            Slider(
                                value = blueVal,
                                onValueChange = { blueVal = it },
                                valueRange = 0f..255f,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${blueVal.toInt()}", fontSize = 11.sp, modifier = Modifier.width(32.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Color Preview Box with Hex code
                val hexString = String.format("#%06X", (0xFFFFFF and composedColorLong.toInt()))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(composedColorLong),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .border(1.dp, Color(0x33000000), RoundedCornerShape(8.dp))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val textColor = if ((redVal * 0.299 + greenVal * 0.587 + blueVal * 0.114) > 130) Color.Black else Color.White
                        Text(text = "Selected: $hexString", color = textColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onColorSelected(composedColorLong)
                onDismissRequest()
            }) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Apply Color")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    badgeColor: Color? = null,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
            badgeColor?.let { color ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}
