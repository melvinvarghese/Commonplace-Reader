package im.commonplace.reader.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import im.commonplace.reader.notebook.NotebookFields

@Entity(tableName = "notebook_drafts")
data class NotebookDraftEntity(
    @PrimaryKey val targetId: String,
    val isNew: Boolean,
    val kind: EntryKind,
    val sourceId: String?,
    val sourceTitleSnapshot: String?,
    val sourceAuthorsSnapshot: String?,
    @Embedded val fields: NotebookFields,
    val baseEntryRevision: Long?,
    val sessionId: String,
    val draftRevision: Long,
    val commitToken: String,
    val modifiedAt: Long,
)
