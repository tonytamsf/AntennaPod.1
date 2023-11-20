package de.danoeh.antennapod;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.jsoup.internal.StringUtil;

import de.danoeh.antennapod.model.feed.Transcript;
import de.danoeh.antennapod.model.feed.TranscriptSegment;

import java.util.Hashtable;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * {@link RecyclerView.Adapter} that can display a {@link PlaceholderItem}.
 * TODO: Replace the implementation with code for your data type.
 */
public class ItemTranscriptRVAdapter extends RecyclerView.Adapter<ItemTranscriptRVAdapter.ViewHolder> {

    public String TAG = "ItemTranscriptRVAdapter";
    public Hashtable<Long, Integer> positions;
    public Hashtable<Integer, TranscriptSegment> snippets;
    private final Context context;


    private Transcript transcript;

    public ItemTranscriptRVAdapter(Transcript t, Context context) {
        this.context = context;
        positions = new Hashtable<Long, Integer>();
        snippets = new Hashtable<Integer, TranscriptSegment>();
        setTranscript(t);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        return new ViewHolder(inflater.inflate(R.layout.episode_transcript_listitem, parent, false));
    }

    public void setTranscript(Transcript t) {
        transcript = t;
        if (transcript == null) {
            return;
        }
        TreeMap<Long, TranscriptSegment> segmentsMap = transcript.getSegmentsMap();
        Object[] objs = segmentsMap.entrySet().toArray();
        for (int i = 0; i < objs.length; i++) {
            Map.Entry<Long, TranscriptSegment> seg;
            seg = (Map.Entry<Long, TranscriptSegment>) objs[i];
            positions.put((Long) seg.getKey(), i);
            snippets.put(i, seg.getValue());
        }
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        TreeMap<Long, TranscriptSegment> segmentsMap;
        SortedMap<Long, TranscriptSegment> map;

        segmentsMap = transcript.getSegmentsMap();
        TreeMap.Entry entry = (TreeMap.Entry) segmentsMap.entrySet().toArray()[position];
        TranscriptSegment seg = (TranscriptSegment) entry.getValue();
        Long k = (Long) entry.getKey();

        Log.d(TAG, "onBindViewHolder position " + position + " RV pos " + k);
        holder.mItem = seg;
        //holder.mIdView.setText(PodcastIndexTranscriptParser.secondsToTime(k));
        if (! StringUtil.isBlank(seg.getSpeaker())) {
            holder.mAuthorView.setText(seg.getSpeaker());
        }
        holder.mTitleView.setText(seg.getWords());
        holder.mCoverView.setVisibility(View.GONE);
    }

    @Override
    public int getItemCount() {
        if (transcript == null) {
            return 0;
        }
        return transcript.getSegmentsMap().size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView mTitleView;
        public final TextView mAuthorView;
        public final ImageView mCoverView;
        public TranscriptSegment mItem;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            mTitleView = itemView.findViewById(R.id.txtvTitle);
            mAuthorView = itemView.findViewById(R.id.txtvAuthor);
            mCoverView = itemView.findViewById(R.id.imgvCover);
        }

        /*        public ViewHolder(FragmentItemTranscriptRvBinding binding) {
                    super(binding.getRoot());
                    mIdView = binding.itemNumber;
                    mContentView = binding.content;
                    //mIdView.setVisibility(View.GONE);
                }
        */
        @Override
        public String toString() {
            return super.toString() + " '" + mTitleView.getText() + "'";
        }
    }
}