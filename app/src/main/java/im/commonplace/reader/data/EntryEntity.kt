package im.commonplace.reader.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import im.commonplace.reader.reader.SavedLocation
import im.commonplace.reader.reader.Capture

enum class EntryKind { HIGHLIGHT, ANNOTATION }

@Entity(tableName = "entries", foreignKeys = [ForeignKey(
    entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"],
    onDelete = ForeignKey.SET_NULL,
)], indices = [Index("sourceId")])
data class EntryEntity(
    @PrimaryKey val id: String,
    val kind: EntryKind,
    val sourceId: String?,
    val location: SavedLocation?,
    val title: String?,
    val body: String?,
    val quotation: String?,
    val attributionTitle: String,
    val attributionAuthors: String?,
    val colorArgb: Int?,
    val createdAt: Long,
    val modifiedAt: Long,
)
