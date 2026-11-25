package deckers.thibault.aves.channel

import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction

// Collect outside transactions: decoding and hashing must never hold the database write lock.
internal class ScanWriteBatch<T>(private val commit: (List<T>) -> Unit) {
    private val pending = ArrayList<T>(32)
    var commits = 0
        private set

    fun add(item: T) {
        pending.add(item)
        if (pending.size == 32) flush()
    }

    fun flush() {
        if (pending.isEmpty()) return
        commit(pending)
        commits++
        pending.clear()
    }
}

internal fun placeholders(size: Int) = List(size) { "?" }.joinToString(",")

// Shared with SqfliteLocalMediaDb: catalog removal/reset still invalidates these rows.
private const val COLUMNS = "id,uri,sizeBytes,dateModifiedMillis,width,height,durationMillis,rotationDegrees,isFlipped,pageId,mimeType," +
    "algorithmVersion,kind,imagePHash,imageDHash,videoPHashes,videoDHashes,clipPHashes,clipDHashes"
private val INSERT = "INSERT OR REPLACE INTO similarFingerprints ($COLUMNS) VALUES (${placeholders(19)})"

internal fun SQLiteDatabase.readFingerprints(ids: Array<String>): MutableMap<Long, SimilarFingerprint> {
    val fingerprints = mutableMapOf<Long, SimilarFingerprint>()
    rawQuery("SELECT $COLUMNS FROM similarFingerprints WHERE id IN (${placeholders(ids.size)})", ids).use { rows ->
        fun nullableLong(i: Int) = if (rows.isNull(i)) null else rows.getLong(i)
        fun hashes(i: Int) = rows.getString(i)?.takeIf { it.isNotEmpty() }?.split(',').orEmpty()
        while (rows.moveToNext()) {
            val kind = rows.getString(12)
            val candidate = DuplicateCandidate(
                entryId = rows.getLong(0), uri = rows.getString(1), sizeBytes = rows.getLong(2), dateModifiedMillis = nullableLong(3),
                width = rows.getInt(4), height = rows.getInt(5), durationMillis = nullableLong(6), rotationDegrees = rows.getInt(7),
                isFlipped = rows.getInt(8) != 0, pageId = nullableLong(9)?.toInt(), mimeType = rows.getString(10), isVideo = kind == "video",
            )
            fingerprints[candidate.entryId] = SimilarFingerprint(candidate, rows.getInt(11), kind, rows.getString(13), rows.getString(14),
                hashes(15), hashes(16), hashes(17), hashes(18))
        }
    }
    return fingerprints
}

internal fun SQLiteDatabase.saveFingerprints(items: List<SimilarFingerprint>) = transaction {
    for (fp in items) {
        val c = fp.candidate
        execSQL(INSERT, arrayOf<Any?>(c.entryId, c.uri, c.sizeBytes, c.dateModifiedMillis, c.width, c.height, c.durationMillis,
            c.rotationDegrees, if (c.isFlipped) 1 else 0, c.pageId, c.mimeType, fp.algorithmVersion, fp.kind, fp.imagePHash, fp.imageDHash,
            fp.videoPHashes.joinToString(","), fp.videoDHashes.joinToString(","), fp.clipPHashes.joinToString(","), fp.clipDHashes.joinToString(",")))
    }
}
