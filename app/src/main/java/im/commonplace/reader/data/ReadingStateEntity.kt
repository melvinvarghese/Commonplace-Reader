package im.commonplace.reader.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import im.commonplace.reader.reader.SavedLocation
import im.commonplace.reader.reader.Capture

@Entity(tableName = "reading_state", foreignKeys = [ForeignKey(
    entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"],
    onDelete = ForeignKey.CASCADE,
)], indices = [Index("sourceId")])
data class ReadingStateEntity(
    @PrimaryKey val sourceId: String,
    val location: SavedLocation,
    val modifiedAt: Long,
)
