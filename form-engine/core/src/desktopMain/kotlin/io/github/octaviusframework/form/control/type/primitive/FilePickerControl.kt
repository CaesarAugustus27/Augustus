package io.github.octaviusframework.form.control.type.primitive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.octavius.error.rememberSupervisedCoroutineScope
import io.github.octaviusframework.form.control.base.*
import io.github.octaviusframework.form.localization.FormTr
import org.octavius.theme.FormSpacing
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI

/**
 * What FilePickerControl returns.
 */
enum class FilePickerMode {
    /**
     * Returns the path of the selected file as a String.
     */
    PATH_STRING,

    /**
     * Returns the whole content of the selected file as a ByteArray.
     */
    CONTENT_BYTES
}

/**
 * A control for picking a file through the system dialog or drag and drop.
 *
 * The generic parameter T depends on the chosen FilePickerMode:
 * - String for PATH_STRING
 * - ByteArray for CONTENT_BYTES
 */
class FilePickerControl<T : Any>(
    label: String?,
    private val mode: FilePickerMode,
    required: Boolean? = false,
    dependencies: Map<String, ControlDependency<*>>? = null,
    actions: List<ControlAction<T>>? = null
) : Control<T>(label, required, dependencies, hasStandardLayout = true, actions = actions) {

    override fun acceptsValue(value: Any) = when (mode) {
        FilePickerMode.PATH_STRING -> value is String
        FilePickerMode.CONTENT_BYTES -> value is ByteArray
    }

    @OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
    @Composable
    override fun Display(controlContext: ControlContext, controlState: ControlState<T>, isRequired: Boolean) {
        val scope = rememberSupervisedCoroutineScope()
        var selectedFileName by remember { mutableStateOf<String?>(null) }
        var isDragging by remember { mutableStateOf(false) }

        LaunchedEffect(controlState.value.value) {
            val v = controlState.value.value
            if (v != null && selectedFileName == null) {
                if (mode == FilePickerMode.PATH_STRING && v is String) {
                    selectedFileName = File(v).name
                } else if (mode == FilePickerMode.CONTENT_BYTES && v is ByteArray) {
                    selectedFileName = FormTr.Form.FilePicker.loadedBytes(v.size)
                }
            }
        }

        fun handleFile(file: File) {
            selectedFileName = file.name
            val newValue: Any = when (mode) {
                FilePickerMode.PATH_STRING -> file.absolutePath
                FilePickerMode.CONTENT_BYTES -> file.readBytes()
            }
            @Suppress("UNCHECKED_CAST")
            controlState.value.value = newValue as T
            executeActions(controlContext, newValue as T, scope)
        }

        val dragTarget = remember {
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) {
                    isDragging = true
                }
                override fun onExited(event: DragAndDropEvent) {
                    isDragging = false
                }
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    isDragging = false
                    val dragData = event.dragData()
                    if (dragData is DragData.FilesList) {
                        val files = dragData.readFiles()
                        if (files.isNotEmpty()) {
                            val uriString = files.first()
                            val file = try {
                                File(URI(uriString))
                            } catch (e: Exception) {
                                File(uriString.removePrefix("file://").removePrefix("file:"))
                            }
                            if (file.exists() && file.isFile) {
                                handleFile(file)
                                return true
                            }
                        }
                    }
                    return false
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = FormSpacing.fieldPaddingVertical)
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { true },
                    target = dragTarget
                ),
            color = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = FormTr.Form.FilePicker.file(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (isDragging) FormTr.Form.FilePicker.dropHere() else (selectedFileName ?: FormTr.Form.FilePicker.dragOrClick()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {
                        val fileDialog = FileDialog(null as Frame?, FormTr.Form.FilePicker.chooseFile(), FileDialog.LOAD)
                        fileDialog.isVisible = true
                        val dir = fileDialog.directory
                        val file = fileDialog.file
                        if (dir != null && file != null) {
                            handleFile(File(dir, file))
                        }
                    }
                ) {
                    Text(FormTr.Form.FilePicker.chooseFile())
                }
            }
        }
    }
}
