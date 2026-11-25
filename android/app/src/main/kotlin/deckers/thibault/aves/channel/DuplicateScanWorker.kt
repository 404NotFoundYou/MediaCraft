package deckers.thibault.aves.channel

import android.content.Context
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
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
        if (File(dir, "result").isFile) return File(dir, "result").readMap()
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
            val media = DuplicateMedia(applicationContext)
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
                fun digest(c: DuplicateCandidate): String? {
                    val modified = c.dateModifiedMillis?.takeIf { it > 0 } ?: return null
                    return db.rawQuery("SELECT digest FROM digests WHERE id=? AND uri=? AND size=? AND modified=?", arrayOf("${c.entryId}", c.uri, "${c.sizeBytes}", "$modified")).use { if (it.moveToFirst()) it.getString(0) else null }
                }
                fun saveDigest(c: DuplicateCandidate, digest: String) {
                    if ((c.dateModifiedMillis ?: 0) > 0) db.execSQL("INSERT OR REPLACE INTO digests VALUES(?,?,?,?,?)", arrayOf<Any?>(c.entryId, c.uri, c.sizeBytes, c.dateModifiedMillis, digest))
                }
                val argsIssues = (request["issues"] as? List<*>).orEmpty()
                val result = if (mode == "scan") {
                    media.engine.scan(candidates, ::digest, ::saveDigest, ::progress).toMap(false)
                } else {
                    SQLiteDatabase.openOrCreateDatabase(File(dir, "checkpoint.db"), null).use { checkpoint ->
                        checkpoint.execSQL("CREATE TABLE IF NOT EXISTS items(id INTEGER PRIMARY KEY, failed INTEGER, data BLOB)")
                        val cached = (request["cachedFingerprints"] as? List<*>).orEmpty().associate { val fp = parseFingerprint(it as Map<*, *>); fp.candidate.entryId to fp }.toMutableMap()
                        val failed = mutableMapOf<Long, Map<*, *>>()
                        checkpoint.rawQuery("SELECT id,failed,data FROM items", null).use { rows ->
                            while (rows.moveToNext()) {
                                val map = decode(rows.getBlob(2))
                                if (rows.getInt(1) == 1) failed[rows.getLong(0)] = map else cached[rows.getLong(0)] = parseFingerprint(map)
                            }
                        }
                        fun checkpoint(id: Long, failed: Int, value: Map<*, *>) = checkpoint.execSQL("INSERT OR REPLACE INTO items VALUES(?,?,?)", arrayOf(id, failed, encode(value)))
                        val visual = media.similarEngine.scan(candidates, cached, failed.keys,
                            onFingerprint = { checkpoint(it.candidate.entryId, 0, it.toMap()) }, onIssue = { checkpoint(it.candidate.entryId, 1, it.toMap()) }, onProgress = ::progress)
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
                        val mapped = visual.copy(groups = groups, issues = issues.distinct()).toMap(false)
                        mapped + ("issues" to (issues.map { it.toMap() } + failed.values))
                    }
                }.toMutableMap()
                val issues = argsIssues + (result["issues"] as List<*>)
                result["issues"] = issues
                val invalid = issues.mapNotNull { ((it as Map<*, *>)["entryId"] as? Number)?.toLong() }.toSet()
                val successful = candidates.filter { it.entryId !in invalid }
                db.beginTransaction()
                try {
                    if (request["onlyNew"] == true) {
                        val known = db.rawQuery("SELECT id,uri FROM seen WHERE mode=?", arrayOf(mode)).use { rows -> buildMap { while (rows.moveToNext()) put(rows.getLong(0), rows.getString(1)) } }
                        val fresh = successful.filter { known[it.entryId] != it.uri }.mapTo(mutableSetOf()) { it.entryId }
                        result["groups"] = (result["groups"] as List<*>).filter { group -> ((group as Map<*, *>)["members"] as List<*>).any { ((it as Map<*, *>)["entryId"] as Number).toLong() in fresh } }
                    }
                    successful.forEach { db.execSQL("INSERT OR REPLACE INTO seen VALUES(?,?,?)", arrayOf<Any>(mode, it.entryId, it.uri)) }
                    currentCoroutineContext().ensureActive()
                    File(dir, "result").writeMap(result)
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
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
