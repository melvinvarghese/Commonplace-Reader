package im.commonplace.reader.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import im.commonplace.reader.reader.SavedLocation
import im.commonplace.reader.reader.Capture

@Entity(tableName = "bookmarks", foreignKeys = [ForeignKey(
    entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"],
    onDelete = ForeignKey.CASCADE,
)], indices = [Index("sourceId")])
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val location: SavedLocation,
    val createdAt: Long,
)
