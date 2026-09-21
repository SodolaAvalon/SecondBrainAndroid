package com.lifeos.secondbrain.sync

import com.lifeos.secondbrain.ai.AiOrganizeResult
import com.lifeos.secondbrain.data.markdown.MarkdownPatcher
import com.lifeos.secondbrain.data.markdown.NoteMapper
import com.lifeos.secondbrain.database.NoteDao
import com.lifeos.secondbrain.database.PendingOperationDao
import com.lifeos.secondbrain.database.PendingOperationEntity
import com.lifeos.secondbrain.database.SyncMetaDao
import com.lifeos.secondbrain.database.SyncMetaEntity
import com.lifeos.secondbrain.database.toEntity
import com.lifeos.secondbrain.drive.DriveApi
import com.lifeos.secondbrain.drive.DriveFile
import com.lifeos.secondbrain.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class SyncEngine(
    private val drive: DriveApi,
    private val noteDao: NoteDao,
    private val pendingDao: PendingOperationDao,
    private val metaDao: SyncMetaDao,
    private val settings: AppSettings
) {
    data class CaptureResult(val remoteFileId: String?, val queued: Boolean)
    private data class ResolvedCaptureFolder(val id: String, val location: CapturePathPolicy.Location)

    private companion object {
        const val FILTER_VERSION_KEY = "filterVersion"
        const val FILTER_VERSION = "3"
    }

    private val _state = MutableStateFlow(com.lifeos.secondbrain.domain.SyncState())
    val state: StateFlow<com.lifeos.secondbrain.domain.SyncState> = _state
    private val syncMutex = Mutex()

    suspend fun discoverDefaultVault(): DriveFile? = drive.findFolderByName("Obsidion").firstOrNull()

    suspend fun fullScan(vaultId: String) = syncMutex.withLock { fullScanLocked(vaultId) }

    private suspend fun fullScanLocked(vaultId: String) {
        ensureFilterVersion()
        _state.value = _state.value.copy(isSyncing = true, message = "正在整理你的档案", error = null)
        val ignored = SyncFilter.parseFolders(settings.state.first().ignoredFolders)
        runCatching {
            val all = recursiveChildren(vaultId)
            val markdown = all.filter { indexed ->
                indexed.file.isMarkdown() && !SyncFilter.isIgnoredPath(indexed.path, ignored)
            }
            val remoteIds = markdown.mapTo(mutableSetOf()) { it.file.id }
            markdown.chunked(20).forEach { batch ->
                batch.forEach { indexed ->
                    val file = indexed.file
                    val raw = runCatching { drive.downloadText(file.id) }.getOrNull() ?: return@forEach
                    if (SyncFilter.isTemplate(raw)) {
                        remoteIds.remove(file.id)
                        return@forEach
                    }
                    val note = runCatching {
                        NoteMapper.fromMarkdown(file.id, file.name, indexed.path, raw, file.modifiedTime, file.md5Checksum)
                    }.getOrNull() ?: return@forEach
                    noteDao.upsertIndexed(note.toEntity())
                }
            }
            noteDao.allIds()
                .filterNot { it in remoteIds || it.startsWith("pending:") }
                .forEach { noteDao.deleteIndexed(it) }
            metaDao.put(SyncMetaEntity("changePageToken", drive.startPageToken()))
            metaDao.put(SyncMetaEntity("vaultId", vaultId))
            _state.value = _state.value.copy(isSyncing = false, message = null, lastSuccessEpochMs = System.currentTimeMillis())
        }.onFailure { t ->
            _state.value = _state.value.copy(isSyncing = false, message = null, error = friendly(t))
        }
    }

    suspend fun resetLocalIndex() = syncMutex.withLock {
        noteDao.clearIndexed()
        metaDao.delete("changePageToken")
    }

    suspend fun incrementalSync(vaultId: String) = syncMutex.withLock {
        ensureFilterVersion()
        processPendingLocked()
        val token = metaDao.get("changePageToken")
        if (token == null) {
            fullScanLocked(vaultId)
            return@withLock
        }
        val ignored = SyncFilter.parseFolders(settings.state.first().ignoredFolders)
        _state.value = _state.value.copy(isSyncing = true, message = "正在看看最近有什么变化……", error = null)
        var folderTopologyChanged = false
        val outcome = runCatching {
            var page: String? = token
            var finalToken: String? = null
            while (page != null) {
                val response = drive.changes(page)
                response.changes.forEach { change ->
                    val file = change.file
                    when {
                        change.removed || file?.trashed == true -> noteDao.deleteIndexed(change.fileId)
                        file == null -> Unit
                        file.mimeType == DriveApi.FOLDER_MIME -> folderTopologyChanged = true
                        file.isMarkdown() -> {
                            if (isInVault(file, vaultId)) {
                                runCatching {
                                    val raw = drive.downloadText(file.id)
                                    if (SyncFilter.isTemplate(raw) || isInIgnoredFolder(file, ignored)) {
                                        noteDao.deleteIndexed(file.id)
                                    } else {
                                        noteDao.upsertIndexed(
                                            NoteMapper.fromMarkdown(file.id, file.name, null, raw, file.modifiedTime, file.md5Checksum).toEntity()
                                        )
                                    }
                                }
                            } else {
                                noteDao.deleteIndexed(file.id)
                            }
                        }
                    }
                }
                finalToken = response.newStartPageToken ?: finalToken
                page = response.nextPageToken
            }
            if (finalToken != null) metaDao.put(SyncMetaEntity("changePageToken", finalToken))
        }
        if (outcome.isFailure) {
            _state.value = _state.value.copy(isSyncing = false, message = null, error = friendly(outcome.exceptionOrNull()!!))
            return@withLock
        }
        if (folderTopologyChanged) {
            // Moving a folder does not emit a change for every descendant; reconcile the index safely.
            fullScanLocked(vaultId)
        } else {
            _state.value = _state.value.copy(isSyncing = false, message = null, lastSuccessEpochMs = System.currentTimeMillis())
        }
    }

    /** Returns true when the write is queued for later instead of already present in Drive. */
    suspend fun completeTask(fileId: String): Boolean {
        val updated = Instant.now().toString()
        noteDao.get(fileId)?.let { noteDao.upsertIndexed(it.copy(status = "done", updated = updated)) }
        return runCatching {
            val remote = drive.downloadText(fileId)
            drive.updateText(fileId, MarkdownPatcher.patch(remote, mapOf("status" to "done", "updated" to updated)))
            false
        }.getOrElse { error ->
            pendingDao.upsert(
                PendingOperationEntity(
                    id = UUID.randomUUID().toString(),
                    type = "PATCH_TASK_DONE",
                    fileId = fileId,
                    targetFolderId = null,
                    targetFolderName = null,
                    fileName = null,
                    payload = updated,
                    createdEpochMs = System.currentTimeMillis(),
                    lastError = error.message
                )
            )
            true
        }
    }

    suspend fun captureText(text: String): CaptureResult = captureRaw(text, "android-text", "Quick Capture")
    suspend fun captureVoice(text: String): CaptureResult = captureRaw(text, "android-voice", "Voice Inbox")

    private suspend fun captureRaw(text: String, source: String, folderName: String): CaptureResult {
        require(text.isNotBlank()) { "记录不能为空" }
        val snapshot = settings.state.first()
        val vaultId = snapshot.vaultFolderId ?: throw IllegalStateException("请先连接并选择 Vault")
        val now = Instant.now()
        val fileName = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss")
            .withZone(ZoneId.systemDefault()).format(now) + ".md"
        val body = buildString {
            appendLine("---")
            appendLine("type: raw")
            appendLine("created: $now")
            appendLine("source: $source")
            appendLine("processed: false")
            appendLine("processed_at:")
            appendLine("---")
            appendLine()
            append(text.trim())
            appendLine()
        }
        return runCatching {
            val folder = resolveCaptureFolder(vaultId, folderName)
            val created = drive.createMarkdown(folder.id, fileName, body)
            noteDao.upsertIndexed(
                NoteMapper.fromMarkdown(
                    fileId = created.id,
                    name = created.name,
                    path = CapturePathPolicy.relativePath(folder.location, folderName, created.name),
                    markdown = body,
                    modifiedTime = created.modifiedTime ?: now.toString(),
                    md5Checksum = created.md5Checksum
                ).toEntity()
            )
            CaptureResult(created.id, queued = false)
        }.getOrElse { error ->
            val operationId = UUID.randomUUID().toString()
            val localId = "pending:$operationId"
            noteDao.upsertIndexed(
                NoteMapper.fromMarkdown(
                    fileId = localId,
                    name = fileName,
                    path = CapturePathPolicy.relativePath(CapturePathPolicy.Location.INBOX, folderName, fileName),
                    markdown = body,
                    modifiedTime = now.toString(),
                    md5Checksum = null
                ).toEntity()
            )
            pendingDao.upsert(
                PendingOperationEntity(
                    id = operationId,
                    type = "CREATE_CAPTURE",
                    fileId = localId,
                    targetFolderId = null,
                    targetFolderName = folderName,
                    fileName = fileName,
                    payload = body,
                    createdEpochMs = System.currentTimeMillis(),
                    lastError = error.message
                )
            )
            CaptureResult(remoteFileId = null, queued = true)
        }
    }

    suspend fun writeAiItems(sourceFileId: String, result: AiOrganizeResult) {
        val snapshot = settings.state.first()
        val vaultId = snapshot.vaultFolderId ?: error("Vault 未配置")
        result.items.forEach { item ->
            val type = item.type.lowercase()
            val folderName = targetFolderFor(type)
            val folderId = findChildFolder(vaultId, folderName)?.id ?: drive.createFolder(vaultId, folderName).id
            val now = Instant.now()
            val id = UUID.randomUUID().toString()
            val content = buildString {
                appendLine("---")
                appendLine("id: $id")
                appendLine("type: ${yamlScalar(type)}")
                appendLine("title: ${yamlScalar(item.title)}")
                appendLine("status: ${yamlScalar(item.status ?: "active")}")
                appendLine("created: $now")
                appendLine("updated: $now")
                appendLine("source: drive:$sourceFileId")
                item.due?.let { appendLine("due: ${yamlScalar(it)}") }
                item.priority?.let { appendLine("priority: ${yamlScalar(it)}") }
                item.project?.let { appendLine("project: ${yamlScalar(it)}") }
                if (item.tags.isNotEmpty()) appendLine("tags: [${item.tags.joinToString(", ") { yamlScalar(it) }}]")
                appendLine("---")
                appendLine()
                if (item.body.isNotBlank()) appendLine(item.body.trim())
            }
            val safe = item.title.replace(Regex("[\\\\/:*?\"<>|]"), "-").trim().take(80).ifBlank { type }
            val fileName = "$safe-${id.take(8)}.md"
            drive.createMarkdown(folderId, fileName, content)
        }
        val raw = drive.downloadText(sourceFileId)
        drive.updateText(
            sourceFileId,
            MarkdownPatcher.patch(raw, mapOf("processed" to "true", "processed_at" to Instant.now().toString()))
        )
    }

    suspend fun processPending() = syncMutex.withLock { processPendingLocked() }

    private suspend fun processPendingLocked() {
        val vaultId = settings.state.first().vaultFolderId
        pendingDao.all().forEach { op ->
            val result = runCatching {
                when (op.type) {
                    "CREATE_CAPTURE" -> {
                        val resolved = op.targetFolderId?.let {
                            ResolvedCaptureFolder(it, CapturePathPolicy.Location.INBOX)
                        } ?: resolveCaptureFolder(
                            requireNotNull(vaultId), op.targetFolderName ?: "Quick Capture"
                        )
                        val created = drive.createMarkdown(resolved.id, requireNotNull(op.fileName), op.payload)
                        op.fileId?.takeIf { it.startsWith("pending:") }?.let { noteDao.deleteIndexed(it) }
                        noteDao.upsertIndexed(
                            NoteMapper.fromMarkdown(
                                fileId = created.id,
                                name = created.name,
                                path = CapturePathPolicy.relativePath(
                                    resolved.location,
                                    op.targetFolderName ?: "Quick Capture",
                                    created.name
                                ),
                                markdown = op.payload,
                                modifiedTime = created.modifiedTime,
                                md5Checksum = created.md5Checksum
                            ).toEntity()
                        )
                    }
                    "PATCH_TASK_DONE" -> {
                        val fileId = requireNotNull(op.fileId)
                        val remote = drive.downloadText(fileId)
                        drive.updateText(fileId, MarkdownPatcher.patch(remote, mapOf("status" to "done", "updated" to op.payload)))
                    }
                    else -> error("Unknown pending operation ${op.type}")
                }
            }
            if (result.isSuccess) pendingDao.delete(op)
            else pendingDao.upsert(op.copy(attempts = op.attempts + 1, lastError = result.exceptionOrNull()?.message))
        }
    }

    private data class IndexedDriveFile(val file: DriveFile, val path: String)

    private suspend fun recursiveChildren(root: String): List<IndexedDriveFile> {
        val result = mutableListOf<IndexedDriveFile>()
        val queue = ArrayDeque<Pair<String, String>>()
        queue += root to ""
        while (queue.isNotEmpty()) {
            val (parent, parentPath) = queue.removeFirst()
            var token: String? = null
            do {
                val page = drive.listChildren(parent, token)
                page.files.forEach { file ->
                    val path = if (parentPath.isBlank()) file.name else "$parentPath/${file.name}"
                    result += IndexedDriveFile(file, path)
                    if (file.mimeType == DriveApi.FOLDER_MIME) queue += file.id to path
                }
                token = page.nextPageToken
            } while (token != null)
        }
        return result
    }

    private suspend fun isInIgnoredFolder(file: DriveFile, ignored: Set<String>): Boolean {
        if (ignored.isEmpty()) return false
        val queue = ArrayDeque<String>().apply { addAll(file.parents) }
        val seen = mutableSetOf<String>()
        while (queue.isNotEmpty() && seen.size < 16) {
            val id = queue.removeFirst()
            if (!seen.add(id)) continue
            val parent = runCatching { drive.getFile(id) }.getOrNull() ?: continue
            if (SyncFilter.isIgnoredFolderName(parent.name, ignored)) return true
            queue.addAll(parent.parents)
        }
        return false
    }

    private suspend fun ensureFilterVersion() {
        if (metaDao.get(FILTER_VERSION_KEY) == FILTER_VERSION) return
        noteDao.clearIndexed()
        metaDao.delete("changePageToken")
        metaDao.put(SyncMetaEntity(FILTER_VERSION_KEY, FILTER_VERSION))
    }

    private suspend fun isInVault(file: DriveFile, vaultId: String): Boolean {
        if (file.id == vaultId || vaultId in file.parents) return true
        val queue = ArrayDeque<String>().apply { addAll(file.parents) }
        val seen = mutableSetOf<String>()
        while (queue.isNotEmpty() && seen.size < 128) {
            val id = queue.removeFirst()
            if (!seen.add(id)) continue
            if (id == vaultId) return true
            val parent = runCatching { drive.getFile(id) }.getOrNull() ?: continue
            if (vaultId in parent.parents) return true
            queue.addAll(parent.parents)
        }
        return false
    }

    private suspend fun resolveCaptureFolder(vaultId: String, folderName: String): ResolvedCaptureFolder {
        val inbox = findChildFolder(vaultId, "00 Inbox")
        if (inbox != null) {
            val nested = findChildFolder(inbox.id, folderName)
            if (nested != null) return ResolvedCaptureFolder(nested.id, CapturePathPolicy.Location.INBOX)
        }
        val rootExisting = findChildFolder(vaultId, folderName)
        if (rootExisting != null) return ResolvedCaptureFolder(rootExisting.id, CapturePathPolicy.Location.ROOT)
        val inboxId = inbox?.id ?: drive.createFolder(vaultId, "00 Inbox").id
        val created = drive.createFolder(inboxId, folderName)
        return ResolvedCaptureFolder(created.id, CapturePathPolicy.Location.INBOX)
    }

    private suspend fun findChildFolder(parentId: String, name: String): DriveFile? {
        var token: String? = null
        do {
            val page = drive.listChildren(parentId, token)
            page.files.firstOrNull { it.mimeType == DriveApi.FOLDER_MIME && it.name == name }?.let { return it }
            token = page.nextPageToken
        } while (token != null)
        return null
    }

    private fun targetFolderFor(type: String): String = when (type) {
        "task" -> "Tasks"
        "idea" -> "Ideas"
        "plan" -> "Plans"
        "writing" -> "Writing"
        "learning" -> "Learning"
        "journal" -> "Journal"
        "decision" -> "Decisions"
        "reference" -> "References"
        else -> "Quick Capture"
    }

    private fun DriveFile.isMarkdown(): Boolean = mimeType == "text/markdown" || name.endsWith(".md", ignoreCase = true)

    private fun yamlScalar(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun friendly(t: Throwable): String = when (t) {
        is com.lifeos.secondbrain.drive.AuthorizationRequiredException -> "Google Drive 连接需要重新授权。"
        is java.net.SocketTimeoutException -> "网络或代理响应超时；本地内容仍然安全。"
        is java.net.UnknownHostException -> "无法解析服务器地址，可能是代理或 DNS 问题。"
        is java.net.SocketException -> "连不上网络或代理；本地内容仍然安全。"
        else -> "这次同步没有完成，本地内容仍然安全。"
    }
}
