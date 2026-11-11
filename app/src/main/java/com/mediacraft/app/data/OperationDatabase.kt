package com.mediacraft.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.mediacraft.app.domain.DuplicateKind
import com.mediacraft.app.domain.MediaItem
import com.mediacraft.app.domain.MediaKind
import com.mediacraft.app.domain.OperationSpec
import com.mediacraft.app.domain.OperationStatus
import com.mediacraft.app.domain.OperationType
import com.mediacraft.app.domain.VideoMetadata
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

@Entity(tableName = "operations")
internal data class OperationEntity(
    @PrimaryKey val id: String,
    val type: String,
    val createdAtMillis: Long,
    val sourceId: String?,
)

@Entity(
    tableName = "operation_items",
    primaryKeys = ["operationId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = OperationEntity::class,
            parentColumns = ["id"],
            childColumns = ["operationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("operationId")],
)
internal data class OperationItemEntity(
    val operationId: String,
    val position: Int,
    val specJson: String,
    val status: String,
    val message: String,
)

internal data class StoredOperation(
    @Embedded val operation: OperationEntity,
    @Relation(parentColumn = "id", entityColumn = "operationId")
    val items: List<OperationItemEntity>,
)

@Dao
internal interface OperationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOperation(operation: OperationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<OperationItemEntity>)

    @Query("UPDATE operation_items SET status=:status, message=:message WHERE operationId=:operationId AND position=:position")
    suspend fun updateItem(operationId: String, position: Int, status: String, message: String)

    @Transaction
    @Query("SELECT * FROM operations ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<StoredOperation>>

    @Transaction
    @Query("SELECT * FROM operations WHERE id=:id")
    suspend fun get(id: String): StoredOperation?

    @Transaction
    @Query("SELECT * FROM operations WHERE id IN (SELECT DISTINCT operationId FROM operation_items WHERE status='PENDING')")
    suspend fun pending(): List<StoredOperation>
}

@Database(entities = [OperationEntity::class, OperationItemEntity::class], version = 1, exportSchema = false)
internal abstract class OperationDatabase : RoomDatabase() {
    abstract fun operations(): OperationDao

    companion object {
        @Volatile private var instance: OperationDatabase? = null

        fun get(context: Context): OperationDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                OperationDatabase::class.java,
                "mediacraft-operations.db",
            ).build().also { instance = it }
        }
    }
}

internal object OperationJson {
    fun encode(spec: OperationSpec): String = JSONObject().apply {
        put("media", encodeMedia(spec.media))
        putNullable("targetName", spec.targetName)
        putNullable("keeper", spec.keeper?.let(::encodeMedia))
        putNullable("duplicateKind", spec.duplicateKind?.name)
        putNullable("digest", spec.digest)
        putNullable("validationError", spec.validationError)
    }.toString()

    fun decode(value: String): OperationSpec = JSONObject(value).let { json ->
        OperationSpec(
            media = decodeMedia(json.getJSONObject("media")),
            targetName = json.optionalString("targetName"),
            keeper = json.optJSONObject("keeper")?.let(::decodeMedia),
            duplicateKind = json.optionalString("duplicateKind")?.let(DuplicateKind::valueOf),
            digest = json.optionalString("digest"),
            validationError = json.optionalString("validationError"),
        )
    }

    private fun encodeMedia(media: MediaItem): JSONObject = JSONObject().apply {
        put("id", media.id)
        put("displayName", media.displayName)
        put("kind", media.kind.name)
        put("mimeType", media.mimeType)
        put("relativePath", media.relativePath)
        put("size", media.size)
        put("width", media.width)
        put("height", media.height)
        put("dateTakenMillis", media.dateTakenMillis)
        put("dateAddedMillis", media.dateAddedMillis)
        put("dateModifiedSeconds", media.dateModifiedSeconds)
        put("generation", media.generation)
        put("durationMillis", media.durationMillis)
        put("favorite", media.favorite)
        putNullable("video", media.video?.let(::encodeVideo))
    }

    private fun decodeMedia(json: JSONObject): MediaItem = MediaItem(
        id = json.getString("id"),
        displayName = json.getString("displayName"),
        kind = MediaKind.valueOf(json.getString("kind")),
        mimeType = json.getString("mimeType"),
        relativePath = json.getString("relativePath"),
        size = json.getLong("size"),
        width = json.getInt("width"),
        height = json.getInt("height"),
        dateTakenMillis = json.getLong("dateTakenMillis"),
        dateAddedMillis = json.getLong("dateAddedMillis"),
        dateModifiedSeconds = json.getLong("dateModifiedSeconds"),
        generation = json.getLong("generation"),
        durationMillis = json.getLong("durationMillis"),
        favorite = json.getBoolean("favorite"),
        video = json.optJSONObject("video")?.let(::decodeVideo),
    )

    private fun encodeVideo(video: VideoMetadata): JSONObject = JSONObject().apply {
        putNullable("codec", video.codec)
        putNullable("bitrate", video.bitrate)
        putNullable("frameRate", video.frameRate?.toDouble())
        putNullable("dynamicRange", video.dynamicRange)
        putNullable("bitDepth", video.bitDepth)
        putNullable("audioSignature", video.audioSignature)
    }

    private fun decodeVideo(json: JSONObject): VideoMetadata = VideoMetadata(
        codec = json.optionalString("codec"),
        bitrate = json.optionalLong("bitrate"),
        frameRate = json.optionalDouble("frameRate")?.toFloat(),
        dynamicRange = json.optionalString("dynamicRange"),
        bitDepth = json.optionalInt("bitDepth"),
        audioSignature = json.optionalString("audioSignature"),
    )

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotEmpty)

    private fun JSONObject.optionalLong(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.optionalInt(key: String): Int? = if (isNull(key)) null else getInt(key)
    private fun JSONObject.optionalDouble(key: String): Double? = if (isNull(key)) null else getDouble(key)
}
