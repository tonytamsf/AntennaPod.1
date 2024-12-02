package de.danoeh.antennapod.model.feed;

import android.database.Cursor;
import android.util.Log;
import androidx.annotation.NonNull;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.json.JSONObject;

import java.io.Serializable;

public class Note implements Serializable {

    private static final String TAG = Note.class.getSimpleName();
    private static final long serialVersionUID = 1L;

    String notes;
    long timestamp;

    public Note() {
    }

    public Note(long timestamp, String notes) {
        this.notes = notes;
        this.timestamp = timestamp;
    }

    public Note(String notesJson) {
        JSONObject json = null;
        json.parse(notesJson);

        this.notes = notes;
        this.timestamp = timestamp;
    }

    public String getNotes() {
        return notes;
    }


    public void setNotes(String notes) {
        this.notes = notes;
    }

    @NonNull
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

}
