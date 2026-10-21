package im.commonplace.reader.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import im.commonplace.reader.reader.Capture
import im.commonplace.reader.reader.SavedLocation
import kotlinx.serialization.json.*

@Database(entities = [SourceEntity::class, EntryEntity::class, BookmarkEntity::class,
    ReadingStateEntity::class, ReaderDraftEntity::class], version = 1, exportSchema = true)
@TypeConverters(ReaderConverters::class)
abstract class CommonplaceDatabase : RoomDatabase() {
    abstract fun readerDao(): ReaderDao
}

class ReaderConverters {
    @TypeConverter fun locationToJson(value: SavedLocation?): String? = value?.toJson()
    @TypeConverter fun locationFromJson(value: String?): SavedLocation? = value?.let(SavedLocation::fromJson)
    @TypeConverter fun captureToJson(value: Capture): String = buildJsonObject {
        put("quotation", value.quotation)
        put("location", value.location.toJson())
    }.toString()
    @TypeConverter fun captureFromJson(value: String): Capture {
        val json = Json.parseToJsonElement(value).jsonObject
        return Capture(json.getValue("quotation").jsonPrimitive.content,
            SavedLocation.fromJson(json.getValue("location").jsonPrimitive.content))
    }
}
