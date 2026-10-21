package im.commonplace.reader.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import im.commonplace.reader.reader.DocumentFormat

enum class SourceAvailability { AVAILABLE, MISSING }

@Entity(tableName = "sources", indices = [Index(value = ["checksum"], unique = true)])
data class SourceEntity(
    @PrimaryKey val id: String,
    val title: String,
    val authors: String?,
    val format: DocumentFormat,
    val originalUri: String,
    val checksum: String,
    val assetPath: String,
    val availability: SourceAvailability,
    val createdAt: Long,
    val modifiedAt: Long,
)
