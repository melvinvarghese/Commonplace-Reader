package im.commonplace.reader.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import im.commonplace.reader.reader.SavedLocation
import im.commonplace.reader.reader.Capture

@Entity(tableName = "reader_drafts", foreignKeys = [ForeignKey(
    entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"],
    onDelete = ForeignKey.CASCADE,
)], indices = [Index("sourceId")])
data class ReaderDraftEntity(
    @PrimaryKey val sourceId: String,
    val capture: Capture,
    val reflection: String?,
    val colorArgb: Int?,
    val modifiedAt: Long,
)
