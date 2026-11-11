package com.mediacraft.app.domain

import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RenamePlanner(
    private val random: SecureRandom = SecureRandom(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun create(
        selected: List<MediaItem>,
        allMedia: List<MediaItem>,
        options: RenameOptions,
    ): RenamePlan {
        if (selected.isEmpty()) return RenamePlan(emptyList(), "请选择要重命名的媒体")
        if (options.start < 0) return RenamePlan(emptyList(), "起始序号不能小于 0")
        val uniqueSelected = selected.distinctBy { it.id }.sortedWith(mediaOrder)
        val templateError = if (options.mode == RenameMode.CUSTOM) validateTemplate(options.template) else null
        if (templateError != null) return RenamePlan(emptyList(), templateError)

        val occupiedByDirectory = allMedia.groupBy { it.directoryKey }.mapValues { (_, media) ->
            media.associateBy { normalizedMediaName(it.displayName) }
        }
        val reserved = mutableMapOf<String, MutableSet<String>>()
        val planned = uniqueSelected.mapIndexed { index, media ->
            val extension = extension(media.displayName)
            val rawBase = when (options.mode) {
                RenameMode.CUSTOM -> applyTemplate(options.template, media, options.start + index)
                RenameMode.RANDOM -> randomBase(media.directoryKey, extension, occupiedByDirectory, reserved)
            }
            val newName = if (extension.isEmpty()) rawBase else "$rawBase.$extension"
            val error = if (rawBase.isEmpty() && options.mode == RenameMode.RANDOM) {
                "无法生成不冲突的随机名称"
            } else validateName(newName)
                ?: collisionError(media, newName, occupiedByDirectory, reserved)
            val warnings = buildList {
                if (options.mode == RenameMode.CUSTOM && "{date}" in options.template && media.dateTakenMillis <= 0) {
                    add(
                        if (media.dateAddedMillis > 0) "缺少拍摄日期，已使用添加日期"
                        else "缺少拍摄日期和添加日期，已使用 unknown_date",
                    )
                }
            }
            if (error == null) reserved.getOrPut(media.directoryKey) { mutableSetOf() }
                .add(normalizedMediaName(newName))
            RenamePlanItem(media, newName, error, warnings)
        }
        return RenamePlan(planned)
    }

    private fun collisionError(
        media: MediaItem,
        newName: String,
        occupied: Map<String, Map<String, MediaItem>>,
        reserved: Map<String, Set<String>>,
    ): String? {
        val normalized = normalizedMediaName(newName)
        if (normalized in reserved[media.directoryKey].orEmpty()) return "本批次存在重复目标名称"
        val owner = occupied[media.directoryKey]?.get(normalized) ?: return null
        if (owner.id == media.id && owner.displayName == newName) return null
        return if (owner.id == media.id) null else "目标名称已被占用；不支持交换或循环改名"
    }

    private fun randomBase(
        directory: String,
        extension: String,
        occupied: Map<String, Map<String, MediaItem>>,
        reserved: Map<String, Set<String>>,
    ): String {
        repeat(RANDOM_ATTEMPTS) {
            val suffix = buildString(RANDOM_LENGTH) {
                repeat(RANDOM_LENGTH) { append(RANDOM_ALPHABET[random.nextInt(RANDOM_ALPHABET.length)]) }
            }
            val base = "MC_$suffix"
            val name = if (extension.isEmpty()) base else "$base.$extension"
            val normalized = normalizedMediaName(name)
            if (normalized !in occupied[directory].orEmpty() && normalized !in reserved[directory].orEmpty()) return base
        }
        return ""
    }

    private fun validateTemplate(template: String): String? {
        if (template.isBlank()) return "命名模板不能为空"
        var cursor = 0
        TOKEN.findAll(template).forEach { match ->
            val literal = template.substring(cursor, match.range.first)
            if ('{' in literal || '}' in literal) return "模板包含未知变量"
            val token = match.value
            if (token != "{date}" && token != "{original}" && !SEQ.matches(token)) return "模板包含未知变量：$token"
            cursor = match.range.last + 1
        }
        val tail = template.substring(cursor)
        if ('{' in tail || '}' in tail) return "模板包含未知变量"
        return null
    }

    private fun applyTemplate(template: String, media: MediaItem, sequence: Int): String =
        TOKEN.replace(template) { match ->
            when (match.value) {
                "{date}" -> formatDate(media.dateTakenMillis.takeIf { it > 0 } ?: media.dateAddedMillis)
                "{original}" -> baseName(media.displayName)
                else -> {
                    val digits = SEQ.matchEntire(match.value)?.groupValues?.get(1)?.toInt() ?: 1
                    sequence.toString().padStart(digits, '0')
                }
            }
        }

    private fun validateName(name: String): String? {
        val base = baseName(name)
        if (base.isBlank()) return "文件名不能为空"
        if (base == "." || base == "..") return "文件名不能为 . 或 .."
        if (name.any { it == '/' || it == '\\' || it.code < 32 || it.code == 127 }) return "文件名包含非法字符"
        if (name.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES) return "文件名过长"
        return null
    }

    private fun formatDate(millis: Long): String =
        if (millis > 0) DATE_FORMAT.format(Instant.ofEpochMilli(millis).atZone(zoneId)) else "unknown_date"

    private fun baseName(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    private fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && dot < name.lastIndex) name.substring(dot + 1) else ""
    }

    private companion object {
        val mediaOrder = compareBy<MediaItem> { it.dateTakenMillis.takeIf { date -> date > 0 } ?: it.dateAddedMillis }
            .thenBy { it.id }
        val TOKEN = Regex("\\{[^{}]+\\}")
        val SEQ = Regex("\\{seq:([1-6])\\}")
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        const val RANDOM_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val RANDOM_LENGTH = 10
        const val RANDOM_ATTEMPTS = 32
        const val MAX_NAME_BYTES = 255
    }
}
