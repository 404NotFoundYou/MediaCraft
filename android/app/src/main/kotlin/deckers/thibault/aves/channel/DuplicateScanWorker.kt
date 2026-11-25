package deckers.thibault.aves.channel

import android.content.Context
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import androidx.core.database.sqlite.transaction
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import deckers.thibault.aves.R
import io.flutter.plugin.common.StandardMessageCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

private const val WORK = "duplicate-media-scan"
private fun encode(value: Any): ByteArray = StandardMessageCodec.INSTANCE.encodeMessage(value)!!.let { buffer ->
    ByteArray(buffer.position()).also { buffer.flip(); buffer.get(it) }
}
private fun decode(bytes: ByteArray) = StandardMessageCodec.INSTANCE.decodeMessage(ByteBuffer.wrap(bytes)) as Map<*, *>
private fun File.readMap() = decode(AtomicFile(this).readFully())
private fun File.writeMap(value: Map<*, *>) {
    val file = AtomicFile(this)
    val stream = file.startWrite()
    try { stream.write(encode(value)); file.finishWrite(stream) } catch (e: Throwable) { file.failWrite(stream); throw e }
}

internal class DuplicateScanFiles(private val context: Context) {
    val root = File(context.noBackupFilesDir, "duplicate-scans").apply { mkdirs() }
    private val manager = WorkManager.getInstance(context)
    fun folder(id: String) = File(root, UUID.fromString(id).toString())

    fun enqueue(mode: String, request: Map<*, *>): String = synchronized(lock) {
        check(manager.getWorkInfosForUniqueWork(WORK).get().none { !it.state.isFinished }) { "扫描正在进行" }
        val work = OneTimeWorkRequestBuilder<DuplicateScanWorker>().build()
        val id = work.id.toString()
        val dir = folder(id)
        check(dir.mkdir())
        try {
            File(dir, "request").writeMap(request)
            File(dir, "info").writeMap(mapOf("id" to id, "mode" to mode, "created" to System.currentTimeMillis(), "version" to ALGORITHM_VERSION))
            manager.enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, work).result.get()
        } catch (e: Exception) { dir.deleteRecursively(); throw e }
        // Only the five most recent reports belong to the history screen.
        history().drop(5).forEach { folder(it["id"] as String).deleteRecursively() }
        id
    }

    fun history() = root.listFiles().orEmpty().filter { it.isDirectory && File(it, "info").isFile }
        .map { File(it, "info").readMap() }.sortedByDescending { (it["created"] as Number).toLong() }

    fun cancel(id: String) { manager.cancelWorkById(UUID.fromString(id)).result.get() }

    fun status(id: String): Map<*, *> {
        val dir = folder(id)
        val work = manager.getWorkInfoById(UUID.fromString(id)).get()
        if (work?.state == WorkInfo.State.CANCELLED) return mapOf("groups" to emptyList<Any>(), "issues" to emptyList<Any>(), "cancelled" to true)
        if (File(dir, "result").isFile) return File(dir, "result").readMap().minus("fingerprints")
        check(work != null && !work.state.isFinished) { work?.outputData?.getString("error") ?: "扫描未能完成，请重新扫描" }
        return work.progress.keyValueMap.takeIf { it.isNotEmpty() } ?: mapOf("stage" to "queued", "processed" to 0, "total" to 0)
    }
    companion object { private val lock = Any() }
}

class DuplicateScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val files = DuplicateScanFiles(applicationContext)
        val dir = files.folder(id.toString())
        try {
            if (File(dir, "result").isFile) return@withContext Result.success()
            setForeground(getForegroundInfo())
            val info = File(dir, "info").readMap()
            check(info["version"] == ALGORITHM_VERSION) { "算法已更新，请重新扫描" }
            val request = File(dir, "request").readMap()
            val mode = info["mode"] as String
            var fullFrames = 0
            var clipFrames = 0
            var checkpointBatches = 0
            var fingerprintBatches = 0
            val media = DuplicateMedia(applicationContext) { clip -> if (clip) clipFrames++ else fullFrames++ }
            val candidates = (request["members"] as List<*>).map { parseCandidate(it as Map<*, *>) }
            val throttle = DuplicateScanProgressThrottle(450, SystemClock::elapsedRealtime)
            var stageStarted = SystemClock.elapsedRealtime()
            var lastStage = "queued"
            fun progress(n: Int, total: Int, stage: String) {
                if (lastStage != stage) {
                    val now = SystemClock.elapsedRealtime()
                    Log.d("DuplicateScan", "$lastStage ${now - stageStarted}ms; $stage candidates=$total")
                    stageStarted = now
                    lastStage = stage
                }
                if (throttle.shouldEmit(stage)) setProgressAsync(workDataOf("stage" to stage, "processed" to n, "total" to total))
            }
            SQLiteDatabase.openOrCreateDatabase(File(files.root, "cache.db"), null).use { db ->
                db.execSQL("CREATE TABLE IF NOT EXISTS digests (id INTEGER PRIMARY KEY, uri TEXT, size INTEGER, modified INTEGER, digest TEXT)")
                db.execSQL("CREATE TABLE IF NOT EXISTS seen (mode TEXT, id INTEGER, uri TEXT, PRIMARY KEY(mode,id))")
                val pending = File(dir, "pending-result")
                val report = File(dir, "result")
                // Publish only after the seen IDs commit. A killed worker can finish the rename on restart.
                db.execSQL("CREATE TABLE IF NOT EXISTS lastReport (slot INTEGER PRIMARY KEY, job TEXT)")
                if (pending.isFile && db.rawQuery("SELECT 1 FROM lastReport WHERE slot=1 AND job=?", arrayOf(id.toString())).use { it.moveToFirst() }) {
                    check(pending.renameTo(report)) { "无法恢复扫描报告" }
                    return@use
                }
                fun digest(c: DuplicateCandidate): String? {
                    val modified = c.dateModifiedMillis?.takeIf { it > 0 } ?: return null
                    return db.rawQuery("SELECT digest FROM digests WHERE id=? AND uri=? AND size=? AND modified=?", arrayOf("${c.entryId}", c.uri, "${c.sizeBytes}", "$modified")).use { if (it.moveToFirst()) it.getString(0) else null }
                }
                val digestWrites = ScanWriteBatch<Pair<DuplicateCandidate, String>> { items ->
                    db.transaction {
                        for ((c, hash) in items) execSQL("INSERT OR REPLACE INTO digests VALUES(?,?,?,?,?)", arrayOf<Any?>(c.entryId, c.uri, c.sizeBytes, c.dateModifiedMillis, hash))
                    }
                }
                fun saveDigest(c: DuplicateCandidate, hash: String) {
                    if ((c.dateModifiedMillis ?: 0) > 0) digestWrites.add(c to hash)
                }
                val argsIssues = (request["issues"] as? List<*>).orEmpty()
                val result = if (mode == "scan") {
                    media.engine.scan(candidates, ::digest, ::saveDigest, ::progress).toMap(false)
                } else {
                    SQLiteDatabase.openOrCreateDatabase(File(dir, "checkpoint.db"), null).use { checkpoint ->
                        checkpoint.execSQL("CREATE TABLE IF NOT EXISTS items(id INTEGER PRIMARY KEY, failed INTEGER, data BLOB)")
                        val failed = mutableMapOf<Long, Map<*, *>>()
                        checkpoint.rawQuery("SELECT id,data FROM items WHERE failed=1", null).use { rows ->
                            while (rows.moveToNext()) failed[rows.getLong(0)] = decode(rows.getBlob(1))
                        }
                        val changed = mutableSetOf<Long>()
                        val writes = ScanWriteBatch<Array<Any>> { items ->
                            checkpoint.transaction { for (item in items) execSQL("INSERT OR REPLACE INTO items VALUES(?,?,?)", item) }
                        }
                        val catalogPath = applicationContext.getDatabasePath("metadata.db").path
                        val visual = SQLiteDatabase.openDatabase(catalogPath, null, SQLiteDatabase.OPEN_READWRITE).use { catalog ->
                            fun loadCached(batch: List<DuplicateCandidate>): Map<Long, SimilarFingerprint> {
                                val ids = batch.map { it.entryId.toString() }.toTypedArray()
                                val cached = catalog.readFingerprints(ids)
                                checkpoint.rawQuery("SELECT id,data FROM items WHERE failed=0 AND id IN (${placeholders(ids.size)})", ids).use { rows ->
                                    while (rows.moveToNext()) {
                                        val id = rows.getLong(0)
                                        changed.add(id)
                                        cached[id] = parseFingerprint(decode(rows.getBlob(1)))
                                    }
                                }
                                return cached
                            }
                            val scan = media.similarEngine.scan(candidates, ::loadCached, failed.keys,
                                onFingerprint = { changed.add(it.candidate.entryId); writes.add(arrayOf(it.candidate.entryId, 0, encode(it.toMap()))) },
                                onIssue = { writes.add(arrayOf(it.candidate.entryId, 1, encode(it.toMap()))) }, onProgress = ::progress)
                            writes.flush()
                            checkpointBatches = writes.commits
                            for (batch in scan.fingerprints.asSequence().filter { it.candidate.entryId in changed }.chunked(32)) {
                                currentCoroutineContext().ensureActive()
                                catalog.saveFingerprints(batch)
                                fingerprintBatches++
                            }
                            scan
                        }
                        var groups = visual.groups
                        val bySize = candidates.groupBy { it.sizeBytes }.filterValues { it.size > 1 }
                        val checked = mutableSetOf<Long>()
                        val excluded = mutableSetOf<Long>()
                        val issues = visual.issues.toMutableList()
                        // Only sizes participating in a visual match can affect a displayed group.
                        while (groups.isNotEmpty()) {
                            val pool = groups.flatMap { it.members }.map { it.sizeBytes }.filter { it in bySize && checked.add(it) }.flatMap { bySize.getValue(it) }
                            if (pool.isEmpty()) break
                            val exact = media.engine.scan(pool, ::digest, ::saveDigest) { n, t, _ -> progress(n, t, "similar-exact") }
                            issues += exact.issues
                            if (exact.groups.isEmpty()) break
                            exact.groups.flatMap { it.members }.mapTo(excluded) { it.entryId }
                            groups = media.similarEngine.group(visual.fingerprints.filterNot { it.candidate.entryId in excluded }, ::progress)
                        }
                        visual.copy(groups = groups, issues = issues.distinct()).toMap(false).toMutableMap().apply {
                            if (failed.isNotEmpty()) put("issues", (get("issues") as List<*>) + failed.values)
                        }
                    }
                }.toMutableMap()
                digestWrites.flush()
                val issues = argsIssues + (result["issues"] as List<*>)
                result["issues"] = issues
                val invalid = issues.mapNotNull { ((it as Map<*, *>)["entryId"] as? Number)?.toLong() }.toSet()
                val successful = candidates.filter { it.entryId !in invalid }
                db.beginTransaction()
                try {
                    if (request["onlyNew"] == true) {
                        val fresh = mutableSetOf<Long>()
                        for (batch in successful.chunked(900)) {
                            val args = arrayOf(mode, *batch.map { it.entryId.toString() }.toTypedArray())
                            val known = db.rawQuery("SELECT id,uri FROM seen WHERE mode=? AND id IN (${placeholders(batch.size)})", args).use { rows ->
                                buildMap { while (rows.moveToNext()) put(rows.getLong(0), rows.getString(1)) }
                            }
                            for (item in batch) if (known[item.entryId] != item.uri) fresh.add(item.entryId)
                        }
                        result["groups"] = (result["groups"] as List<*>).filter { group -> ((group as Map<*, *>)["members"] as List<*>).any { ((it as Map<*, *>)["entryId"] as Number).toLong() in fresh } }
                    }
                    successful.forEach { db.execSQL("INSERT OR REPLACE INTO seen VALUES(?,?,?)", arrayOf<Any>(mode, it.entryId, it.uri)) }
                    currentCoroutineContext().ensureActive()
                    pending.writeMap(result)
                    db.execSQL("INSERT OR REPLACE INTO lastReport VALUES(1,?)", arrayOf(id.toString()))
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
                check(pending.renameTo(report)) { "无法保存扫描报告" }
                Log.d("DuplicateScan", "finished candidates=${candidates.size} fullFrames=$fullFrames clipFrames=$clipFrames checkpointBatches=$checkpointBatches " +
                    "fingerprintBatches=$fingerprintBatches digestBatches=${digestWrites.commits} resultBytes=${report.length()}")
            }
            File(dir, "request").delete()
            SQLiteDatabase.deleteDatabase(File(dir, "checkpoint.db"))
            Result.success()
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Log.w("DuplicateScan", "Scan failed", e)
            Result.failure(workDataOf("error" to (e.message ?: e.javaClass.simpleName).take(500)))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val channel = NotificationChannelCompat.Builder(WORK, NotificationManagerCompat.IMPORTANCE_LOW).setName("重复媒体扫描").build()
        NotificationManagerCompat.from(applicationContext).createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(applicationContext, WORK).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("正在扫描重复媒体").setOngoing(true)
            .addAction(0, "取消扫描", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return when {
            Build.VERSION.SDK_INT >= 35 -> ForegroundInfo(306, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            Build.VERSION.SDK_INT == 34 -> ForegroundInfo(306, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else -> ForegroundInfo(306, notification)
        }
    }
}
