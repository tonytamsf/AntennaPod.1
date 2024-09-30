package de.danoeh.antennapod.model.feed;

import android.database.Cursor;
import android.util.Log;
import androidx.annotation.NonNull;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;

public class Note implements Serializable {

    private static final String TAG = Note.class.getSimpleName();
    private static final long serialVersionUID = 1L;

    long noteId;
    String notes;
    String feedTitle;
    String feedItemTitle;

    public Note() {
    }

    public Note(long noteId, String notes, String feedTitle, String feedItemTitle) {
        this.noteId = noteId;
        this.notes = notes;
        this.feedTitle = feedTitle;
        this.feedItemTitle = feedItemTitle;
    }

    public long getNoteId() {
        return noteId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNoteId(long noteId) {
        this.noteId = noteId;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getFeedTitle() {
        return feedTitle;
    }

    public String getFeedItemTitle() {
        return feedItemTitle;
    }

    @NonNull
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
