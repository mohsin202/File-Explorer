package win.android.fileexplorar.ui

import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import win.android.fileexplorar.data.FileItem
import win.android.fileexplorar.data.FileType
import win.android.fileexplorar.data.ShortcutHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun ExplorerScreen(
    initialPath: String? = null,
    onNewWindow: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    val external = Environment.getExternalStorageDirectory()
    val desktopDir = File(external, "Desktop").also { if (!it.exists()) it.mkdirs() }

    var currentPath by remember {
        mutableStateOf(
            when {
                initialPath != null && File(initialPath).exists() -> initialPath
                else -> external.absolutePath
            }
        )
    }
    val history = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }

    var selectedItems by remember { mutableStateOf<Set<String>>(emptySet()) }
    var clipboard by remember { mutableStateOf<List<File>>(emptyList()) }
    var isCut by remember { mutableStateOf(false) }
    var viewModeGrid by remember { mutableStateOf(true) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<FileItem?>(null) }
    var contextMenuItem by remember { mutableStateOf<FileItem?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // Drag state
    var draggingPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dropTargetPath by remember { mutableStateOf<String?>(null) }

    fun navigateTo(path: String, addHistory: Boolean = true) {
        val f = File(path)
        if (!f.exists()) return
        if (f.isFile && ShortcutHelper.isShortcut(f)) {
            val target = ShortcutHelper.resolve(f)
            if (target != null && File(target).exists()) {
                navigateTo(target, addHistory)
                return
            }
        }
        if (addHistory) {
            if (historyIndex < history.lastIndex) {
                while (history.size > historyIndex + 1) history.removeAt(history.lastIndex)
            }
            history.add(path)
            historyIndex = history.lastIndex
        }
        currentPath = path
        selectedItems = emptySet()
        refreshKey++
    }

    fun goBack() {
        if (historyIndex > 0) {
            historyIndex--
            currentPath = history[historyIndex]
            selectedItems = emptySet()
            refreshKey++
        }
    }

    fun goForward() {
        if (historyIndex < history.lastIndex) {
            historyIndex++
            currentPath = history[historyIndex]
            selectedItems = emptySet()
            refreshKey++
        }
    }

    fun goUp() {
        File(currentPath).parentFile?.let { navigateTo(it.absolutePath) }
    }

    LaunchedEffect(Unit) {
        if (history.isEmpty()) {
            history.add(currentPath)
            historyIndex = 0
        }
    }

    val currentDir = File(currentPath)
    val items = remember(currentPath, refreshKey) {
        currentDir.listFiles()
            ?.map { FileItem(it) }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
    }

    val pinned = listOf(
        NavEntry("Desktop", Icons.Outlined.DesktopWindows, desktopDir.absolutePath),
        NavEntry("Downloads", Icons.Outlined.Download, File(external, "Download").absolutePath),
        NavEntry("Documents", Icons.Outlined.Description, File(external, "Documents").absolutePath),
        NavEntry("Pictures", Icons.Outlined.Image, File(external, "Pictures").absolutePath),
        NavEntry("Music", Icons.Outlined.MusicNote, File(external, "Music").absolutePath),
        NavEntry("Videos", Icons.Outlined.VideoLibrary, File(external, "Movies").absolutePath),
    )

    val drives = buildList {
        add(NavEntry("Internal Storage", Icons.Outlined.Storage, external.absolutePath))
        val secondary = context.getExternalFilesDirs(null)
        secondary.getOrNull(1)?.let { sd ->
            var root = sd
            while (root.parentFile != null && root.parentFile!!.canRead() &&
                !root.parentFile!!.absolutePath.startsWith("/storage/emulated")
            ) {
                root = root.parentFile!!
            }
            if (root.exists() && root.canRead() && root.absolutePath != external.absolutePath) {
                add(NavEntry("SD Card", Icons.Outlined.SdCard, root.absolutePath))
            }
        }
    }

    fun performDrop(targetFolder: File) {
        if (draggingPaths.isEmpty()) return
        val sources = draggingPaths.map { File(it) }
        sources.forEach { src ->
            if (src.absolutePath == targetFolder.absolutePath) return@forEach
            if (targetFolder.absolutePath.startsWith(src.absolutePath + "/")) return@forEach
            try {
                val dest = File(targetFolder, src.name)
                if (src.isDirectory) src.copyRecursively(dest, overwrite = true)
                else src.copyTo(dest, overwrite = true)
                src.deleteRecursively()
            } catch (_: Exception) {}
        }
        draggingPaths = emptyList()
        dragOffset = Offset.Zero
        dropTargetPath = null
        selectedItems = emptySet()
        refreshKey++
        Toast.makeText(context, "Moved ${sources.size} item(s)", Toast.LENGTH_SHORT).show()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Color(0xFFF3F3F3))) {
            TitleBar(currentDir.name.ifEmpty { "This PC" })

            NavigationBar(
                canBack = historyIndex > 0,
                canForward = historyIndex < history.lastIndex,
                canUp = currentDir.parentFile != null,
                path = currentPath,
                onBack = { goBack() },
                onForward = { goForward() },
                onUp = { goUp() },
                onRefresh = { refreshKey++ },
                onPathSubmit = { navigateTo(it) }
            )

            CommandBar(
                hasSelection = selectedItems.isNotEmpty(),
                hasClipboard = clipboard.isNotEmpty(),
                onNewFolder = { showNewFolderDialog = true },
                onNewWindow = { onNewWindow(currentPath) },
                onCut = {
                    clipboard = selectedItems.map { File(it) }
                    isCut = true
                    Toast.makeText(context, "Cut ${clipboard.size} item(s)", Toast.LENGTH_SHORT).show()
                },
                onCopy = {
                    clipboard = selectedItems.map { File(it) }
                    isCut = false
                    Toast.makeText(context, "Copied ${clipboard.size} item(s)", Toast.LENGTH_SHORT).show()
                },
                onPaste = {
                    pasteFiles(clipboard, currentDir, isCut)
                    if (isCut) clipboard = emptyList()
                    selectedItems = emptySet()
                    refreshKey++
                },
                onDelete = {
                    selectedItems.forEach { File(it).deleteRecursively() }
                    selectedItems = emptySet()
                    refreshKey++
                },
                onRename = {
                    selectedItems.firstOrNull()?.let { showRenameDialog = FileItem(File(it)) }
                },
                viewModeGrid = viewModeGrid,
                onToggleView = { viewModeGrid = !viewModeGrid }
            )

            Row(Modifier.fillMaxSize()) {
                Sidebar(
                    pinned = pinned,
                    drives = drives,
                    currentPath = currentPath,
                    dropTargetPath = dropTargetPath,
                    onNavigate = { navigateTo(it) },
                    onDrop = { path -> performDrop(File(path)) }
                )

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.White)
                        .padding(8.dp)
                ) {
                    if (items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("This folder is empty", color = Color.Gray)
                        }
                    } else if (viewModeGrid) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(100.dp),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(items, key = { it.path }) { item ->
                                FileGridItem(
                                    item = item,
                                    selected = item.path in selectedItems,
                                    isDropTarget = dropTargetPath == item.path && item.isDirectory,
                                    onClick = {
                                        if (item.isDirectory || ShortcutHelper.isShortcut(item.file))
                                            navigateTo(item.path)
                                        else openFile(context, item.file)
                                    },
                                    onLongClick = { contextMenuItem = item },
                                    onSelect = {
                                        selectedItems = if (item.path in selectedItems)
                                            selectedItems - item.path else selectedItems + item.path
                                    },
                                    onDragStart = {
                                        val paths = if (item.path in selectedItems && selectedItems.isNotEmpty())
                                            selectedItems.toList() else listOf(item.path)
                                        draggingPaths = paths
                                        dragOffset = Offset.Zero
                                    },
                                    onDrag = { delta -> dragOffset += delta },
                                    onDragEnd = {
                                        dropTargetPath?.let { target ->
                                            val t = File(target)
                                            if (t.isDirectory) performDrop(t)
                                        }
                                        draggingPaths = emptyList()
                                        dragOffset = Offset.Zero
                                        dropTargetPath = null
                                    },
                                    onDragCancel = {
                                        draggingPaths = emptyList()
                                        dragOffset = Offset.Zero
                                        dropTargetPath = null
                                    },
                                    onHoverFolder = { hovering ->
                                        dropTargetPath = if (hovering && item.isDirectory) item.path else null
                                    }
                                )
                            }
                        }
                    } else {
                        LazyColumn {
                            items(items, key = { it.path }) { item ->
                                FileListItem(
                                    item = item,
                                    selected = item.path in selectedItems,
                                    isDropTarget = dropTargetPath == item.path && item.isDirectory,
                                    onClick = {
                                        if (item.isDirectory || ShortcutHelper.isShortcut(item.file))
                                            navigateTo(item.path)
                                        else openFile(context, item.file)
                                    },
                                    onLongClick = { contextMenuItem = item },
                                    onSelect = {
                                        selectedItems = if (item.path in selectedItems)
                                            selectedItems - item.path else selectedItems + item.path
                                    },
                                    onDragStart = {
                                        val paths = if (item.path in selectedItems && selectedItems.isNotEmpty())
                                            selectedItems.toList() else listOf(item.path)
                                        draggingPaths = paths
                                        dragOffset = Offset.Zero
                                    },
                                    onDrag = { delta -> dragOffset += delta },
                                    onDragEnd = {
                                        dropTargetPath?.let { target ->
                                            val t = File(target)
                                            if (t.isDirectory) performDrop(t)
                                        }
                                        draggingPaths = emptyList()
                                        dragOffset = Offset.Zero
                                        dropTargetPath = null
                                    },
                                    onDragCancel = {
                                        draggingPaths = emptyList()
                                        dragOffset = Offset.Zero
                                        dropTargetPath = null
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = "${items.size} items" +
                                if (selectedItems.isNotEmpty()) "  |  ${selectedItems.size} selected" else "",
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        // Drag ghost
        if (draggingPaths.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt() + 80) }
                    .padding(16.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xEE005FB8),
                shadowElevation = 8.dp
            ) {
                Text(
                    "${draggingPaths.size} item(s)",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 13.sp
                )
            }
        }
    }

    contextMenuItem?.let { item ->
        ContextMenuDialog(
            item = item,
            onDismiss = { contextMenuItem = null },
            onOpen = {
                if (item.isDirectory || ShortcutHelper.isShortcut(item.file)) navigateTo(item.path)
                else openFile(context, item.file)
                contextMenuItem = null
            },
            onOpenWith = {
                openFile(context, item.file, chooser = true)
                contextMenuItem = null
            },
            onOpenInNewWindow = {
                onNewWindow(if (item.isDirectory) item.path else item.file.parent)
                contextMenuItem = null
            },
            onCut = {
                clipboard = listOf(item.file)
                isCut = true
                contextMenuItem = null
            },
            onCopy = {
                clipboard = listOf(item.file)
                isCut = false
                contextMenuItem = null
            },
            onPaste = {
                pasteFiles(clipboard, if (item.isDirectory) item.file else currentDir, isCut)
                if (isCut) clipboard = emptyList()
                refreshKey++
                contextMenuItem = null
            },
            onRename = {
                showRenameDialog = item
                contextMenuItem = null
            },
            onDelete = {
                item.file.deleteRecursively()
                refreshKey++
                contextMenuItem = null
            },
            onNewFolder = {
                showNewFolderDialog = true
                contextMenuItem = null
            },
            onCreateShortcut = {
                val name = item.name + ".wlnk"
                val shortcut = File(desktopDir, name)
                if (ShortcutHelper.create(shortcut, item.path)) {
                    Toast.makeText(context, "Shortcut created on Desktop", Toast.LENGTH_SHORT).show()
                }
                contextMenuItem = null
            },
            hasClipboard = clipboard.isNotEmpty()
        )
    }

    if (showNewFolderDialog) {
        NameDialog(
            title = "New Folder",
            initial = "New folder",
            onConfirm = { name ->
                File(currentDir, name).mkdirs()
                showNewFolderDialog = false
                refreshKey++
            },
            onDismiss = { showNewFolderDialog = false }
        )
    }

    showRenameDialog?.let { item ->
        NameDialog(
            title = "Rename",
            initial = item.name,
            onConfirm = { name ->
                item.file.renameTo(File(item.file.parent, name))
                showRenameDialog = null
                refreshKey++
            },
            onDismiss = { showRenameDialog = null }
        )
    }
}

data class NavEntry(val label: String, val icon: ImageVector, val path: String)

@Composable
private fun TitleBar(title: String) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).background(Color(0xFFF3F3F3)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Folder, null, tint = Color(0xFFE8A317), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NavigationBar(
    canBack: Boolean, canForward: Boolean, canUp: Boolean, path: String,
    onBack: () -> Unit, onForward: () -> Unit, onUp: () -> Unit,
    onRefresh: () -> Unit, onPathSubmit: (String) -> Unit
) {
    var editPath by remember(path) { mutableStateOf(path) }
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFF3F3F3)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, enabled = canBack, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onForward, enabled = canForward, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onUp, enabled = canUp, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.ArrowUpward, "Up", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Refresh, "Refresh", modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(4.dp))
        OutlinedTextField(
            value = editPath,
            onValueChange = { editPath = it },
            modifier = Modifier.weight(1f).height(40.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            shape = RoundedCornerShape(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White
            )
        )
        IconButton(onClick = { onPathSubmit(editPath) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.ArrowForward, "Go", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CommandBar(
    hasSelection: Boolean, hasClipboard: Boolean,
    onNewFolder: () -> Unit, onNewWindow: () -> Unit,
    onCut: () -> Unit, onCopy: () -> Unit, onPaste: () -> Unit,
    onDelete: () -> Unit, onRename: () -> Unit,
    viewModeGrid: Boolean, onToggleView: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(Color.White)
            .border(BorderStroke(1.dp, Color(0xFFE5E5E5)))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CmdButton(Icons.Filled.CreateNewFolder, "New", onNewFolder)
        CmdButton(Icons.Filled.OpenInNew, "New window", onNewWindow)
        Spacer(Modifier.width(4.dp))
        CmdButton(Icons.Filled.ContentCut, "Cut", onCut, enabled = hasSelection)
        CmdButton(Icons.Filled.ContentCopy, "Copy", onCopy, enabled = hasSelection)
        CmdButton(Icons.Filled.ContentPaste, "Paste", onPaste, enabled = hasClipboard)
        CmdButton(Icons.Filled.Delete, "Delete", onDelete, enabled = hasSelection)
        CmdButton(Icons.Filled.DriveFileRenameOutline, "Rename", onRename, enabled = hasSelection)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleView, modifier = Modifier.size(32.dp)) {
            Icon(
                if (viewModeGrid) Icons.Filled.ViewList else Icons.Filled.GridView,
                "View", modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CmdButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        Icon(icon, label, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun Sidebar(
    pinned: List<NavEntry>, drives: List<NavEntry>, currentPath: String,
    dropTargetPath: String?, onNavigate: (String) -> Unit, onDrop: (String) -> Unit
) {
    Column(
        Modifier.width(200.dp).fillMaxHeight().background(Color(0xFFF9F9F9))
            .border(BorderStroke(1.dp, Color(0xFFE5E5E5)))
            .verticalScroll(rememberScrollState()).padding(vertical = 8.dp)
    ) {
        SidebarHeader("Home")
        SidebarItem(Icons.Outlined.Home, "Home", false,
            onClick = { onNavigate(Environment.getExternalStorageDirectory().absolutePath) })
        Spacer(Modifier.height(8.dp))
        SidebarHeader("Pinned")
        pinned.forEach { entry ->
            if (File(entry.path).exists()) {
                SidebarItem(entry.icon, entry.label, currentPath == entry.path,
                    isDropTarget = dropTargetPath == entry.path,
                    onClick = { onNavigate(entry.path) })
            }
        }
        Spacer(Modifier.height(8.dp))
        SidebarHeader("Drives")
        drives.forEach { entry ->
            SidebarItem(entry.icon, entry.label,
                currentPath == entry.path || currentPath.startsWith(entry.path + "/"),
                isDropTarget = dropTargetPath == entry.path,
                onClick = { onNavigate(entry.path) })
        }
    }
}

@Composable
private fun SidebarHeader(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SidebarItem(
    icon: ImageVector, label: String, selected: Boolean,
    isDropTarget: Boolean = false, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                when {
                    isDropTarget -> Color(0xFF90CAF9)
                    selected -> Color(0xFFCCE4F7)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = Color(0xFF1A1A1A))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FileGridItem(
    item: FileItem, selected: Boolean, isDropTarget: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onSelect: () -> Unit,
    onDragStart: () -> Unit, onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit, onDragCancel: () -> Unit,
    onHoverFolder: (Boolean) -> Unit
) {
    Column(
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isDropTarget -> Color(0xFF90CAF9)
                    selected -> Color(0xFFCCE4F7)
                    else -> Color.Transparent
                }
            )
            .pointerInput(item.path) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                    onDoubleTap = { onSelect() }
                )
            }
            .pointerInput(item.path) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() }
                )
            }
            .padding(8.dp).width(90.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(iconFor(item), null, Modifier.size(40.dp), tint = colorFor(item))
        Spacer(Modifier.height(4.dp))
        Text(item.name, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FileListItem(
    item: FileItem, selected: Boolean, isDropTarget: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onSelect: () -> Unit,
    onDragStart: () -> Unit, onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit, onDragCancel: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    Row(
        Modifier.fillMaxWidth()
            .background(
                when {
                    isDropTarget -> Color(0xFF90CAF9)
                    selected -> Color(0xFFCCE4F7)
                    else -> Color.Transparent
                }
            )
            .pointerInput(item.path) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                    onDoubleTap = { onSelect() }
                )
            }
            .pointerInput(item.path) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() }
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(iconFor(item), null, Modifier.size(22.dp), tint = colorFor(item))
        Spacer(Modifier.width(12.dp))
        Text(item.name, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!item.isDirectory) {
            Text(formatSize(item.size), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(80.dp))
        }
        Text(dateFmt.format(Date(item.lastModified)), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(120.dp))
    }
}

@Composable
private fun ContextMenuDialog(
    item: FileItem, onDismiss: () -> Unit,
    onOpen: () -> Unit, onOpenWith: () -> Unit, onOpenInNewWindow: () -> Unit,
    onCut: () -> Unit, onCopy: () -> Unit, onPaste: () -> Unit,
    onRename: () -> Unit, onDelete: () -> Unit, onNewFolder: () -> Unit,
    onCreateShortcut: () -> Unit, hasClipboard: Boolean
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(8.dp), color = Color.White, tonalElevation = 8.dp, shadowElevation = 8.dp) {
            Column(Modifier.width(240.dp).padding(vertical = 6.dp)) {
                MenuRow("Open", Icons.Filled.FolderOpen, onOpen)
                if (!item.isDirectory) MenuRow("Open with…", Icons.Filled.OpenInNew, onOpenWith)
                MenuRow("Open in new window", Icons.Filled.OpenInNew, onOpenInNewWindow)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                MenuRow("Cut", Icons.Filled.ContentCut, onCut)
                MenuRow("Copy", Icons.Filled.ContentCopy, onCopy)
                if (hasClipboard) MenuRow("Paste", Icons.Filled.ContentPaste, onPaste)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                MenuRow("Rename", Icons.Filled.DriveFileRenameOutline, onRename)
                MenuRow("Delete", Icons.Filled.Delete, onDelete)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                MenuRow("New Folder", Icons.Filled.CreateNewFolder, onNewFolder)
                MenuRow("Create shortcut on Desktop", Icons.Filled.Link, onCreateShortcut)
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { v -> text = v }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun iconFor(item: FileItem): ImageVector = when {
    ShortcutHelper.isShortcut(item.file) -> Icons.Filled.Link
    item.isDirectory -> Icons.Filled.Folder
    item.type == FileType.IMAGE -> Icons.Filled.Image
    item.type == FileType.VIDEO -> Icons.Filled.VideoFile
    item.type == FileType.AUDIO -> Icons.Filled.AudioFile
    item.type == FileType.DOCUMENT -> Icons.Filled.Description
    item.type == FileType.ARCHIVE -> Icons.Filled.FolderZip
    item.type == FileType.APK -> Icons.Filled.Android
    else -> Icons.Filled.InsertDriveFile
}

private fun colorFor(item: FileItem): Color = when {
    ShortcutHelper.isShortcut(item.file) -> Color(0xFF0078D4)
    item.isDirectory -> Color(0xFFE8A317)
    item.type == FileType.IMAGE -> Color(0xFF4CAF50)
    item.type == FileType.VIDEO -> Color(0xFF9C27B0)
    item.type == FileType.AUDIO -> Color(0xFFE91E63)
    item.type == FileType.DOCUMENT -> Color(0xFF2196F3)
    item.type == FileType.ARCHIVE -> Color(0xFFFF9800)
    item.type == FileType.APK -> Color(0xFF3DDC84)
    else -> Color(0xFF607D8B)
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.1f GB".format(mb / 1024.0)
}

private fun openFile(context: android.content.Context, file: File, chooser: Boolean = false) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.provider", file
        )
        val mime = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (chooser) context.startActivity(Intent.createChooser(intent, "Open with"))
        else context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(android.net.Uri.fromFile(file), "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Cannot open this file", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun pasteFiles(sources: List<File>, destDir: File, isCut: Boolean) {
    sources.forEach { src ->
        val dest = File(destDir, src.name)
        try {
            if (src.isDirectory) src.copyRecursively(dest, overwrite = true)
            else src.copyTo(dest, overwrite = true)
            if (isCut) src.deleteRecursively()
        } catch (_: Exception) {}
    }
}
