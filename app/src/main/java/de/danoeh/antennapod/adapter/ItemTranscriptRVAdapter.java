package de.danoeh.antennapod.adapter;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import de.danoeh.antennapod.R;

import android.content.Context;
import android.text.Layout;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.internal.StringUtil;

import de.danoeh.antennapod.model.feed.Transcript;
import de.danoeh.antennapod.model.feed.TranscriptSegment;

import java.util.Hashtable;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * {@link RecyclerView.Adapter} that can display a {@link ViewHolder }.
 * TODO: Replace the implementation with code for your data type.
 */
public class ItemTranscriptRVAdapter extends RecyclerView.Adapter<ItemTranscriptRVAdapter.ViewHolder> {
    static public String TAG = "ItemTranscriptRVAdapter";
    public Hashtable<Long, Integer> positions;
    public Hashtable<Integer, TranscriptSegment> snippets;
    private final Context context;
    Boolean initialized = false;


    private Transcript transcript;

    public ItemTranscriptRVAdapter(Transcript t, Context context) {
        this.context = context;
        transcript = t;
        positions = new Hashtable<Long, Integer>();
        snippets = new Hashtable<Integer, TranscriptSegment>();
        initialized = false;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        return new ViewHolder(inflater.inflate(R.layout.episode_transcript_listitem, parent, false));
    }


    public void setTranscript(Transcript t, TextView v) {
        transcript = t;
        if (transcript == null) {
            return;
        }
        initialized = true;

        TreeMap<Long, TranscriptSegment> segmentsMap = transcript.getSegmentsMap();
        Object[] objs = segmentsMap.entrySet().toArray();
        for (int i = 0; i < objs.length; i++) {
            Map.Entry<Long, TranscriptSegment> seg;
            seg = (Map.Entry<Long, TranscriptSegment>) objs[i];
            displayTranscript(v, seg.getValue(), t);
            positions.put((Long) seg.getKey(), i);
            snippets.put(i, seg.getValue());
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        TreeMap<Long, TranscriptSegment> segmentsMap;
        SortedMap<Long, TranscriptSegment> map;

        segmentsMap = transcript.getSegmentsMap();
        TranscriptSegment seg = snippets.get(position);
        if (seg == null) {
            return;
        }
        Long k = seg.getStartTime();

        Log.d(TAG, "onBindViewHolder position " + position + " RV pos " + k);
        holder.mItem = seg;
        //holder.mIdView.setText(PodcastIndexTranscriptParser.secondsToTime(k));
        if (! StringUtil.isBlank(seg.getSpeaker())) {
            holder.mAuthorView.setText(seg.getSpeaker());
        }
        holder.mTitleView.setText(seg.getWords());
        holder.mCoverView.setVisibility(View.GONE);
    }

    static public TranscriptSegment displayTranscript(TextView txtView, TranscriptSegment seg, Transcript transcript) {
        if (transcript == null) {
            return null;
        }

        long position = seg.getStartTime();
        if (seg != null) {
            Log.d(TAG, "showing transcript at " + position + " -> " + seg.getWords());
            // TT TODO when we detect an ellipse on the textview, then remove the words from the end of the
            //  segment until the ellipse no longer
            do {
                txtView.setText(
                        StringUtils.stripToEmpty(StringUtils.replaceAll(seg.getWords(), " +", " ")));
                // We have ellipsis, remove the last word
                Map.Entry<Long, TranscriptSegment> nextSeg = transcript.getSegmentAfterTime(seg.getEndTime());
                int origLen = 0;
                TextUtils.TruncateAt ta = txtView.getEllipsize();

                txtView.getPaint();
                int ellipsisAway = StringUtils.strip(StringUtils.replaceAll(seg.getWords(), " +", " ")).length() -
                        StringUtils.strip(StringUtils.replaceAll(txtView.getText().toString(), " +", " ")).length();
                // TT TODO Using spaces to find which words we trim will not work for transcripts
                //  like Chinese that don't have spaces
                // https://careers.wolt.com/en/blog/tech/expandable-text-with-read-more-action-in-android-not-an-easy-task
                // https://stackoverflow.com/questions/18381990/get-part-of-string-which-was-not-truncated-by-ellipsize

                if (ellipsisAway > 0) {
                    origLen = seg.getWords().length();
                    int ellipsisStart = seg.getWords().length() - ((int) (ellipsisAway));
                    int indexLastWord = seg.getWords().lastIndexOf(" ", ellipsisStart);
                    if (indexLastWord == -1) {
                        break;
                    }
                    String firstWords = seg.getWords().substring(0, indexLastWord);
                    int indexLastTwoWord = firstWords.lastIndexOf(" ");
                    if (indexLastTwoWord == -1) {
                        break;
                    }
                    String firstWordsMinusLast = firstWords.substring(0, indexLastTwoWord);
                    String lastTwoWords = firstWords.substring(indexLastTwoWord);

                    String ellipsisStr  = seg.getWords().substring(indexLastWord);
                    Log.d(TAG, "sink trim ellipsis [" + ellipsisStr
                            + "] along with {" + lastTwoWords + "}"
                            + "from (" + seg.getWords() + ")");
                    seg.setWords(firstWordsMinusLast);
                    seg.setTrimmed(true);
                    if (nextSeg != null) {
                        nextSeg.getValue().setWords(lastTwoWords + " " + ellipsisStr + " " + nextSeg.getValue().getWords());
                        long duration = seg.getEndTime() - seg.getStartTime();
                        float ratio = ((float) (origLen - indexLastTwoWord) / (float) origLen);

                        nextSeg.getValue().setStartTime(nextSeg.getValue().getStartTime() - (long) (ratio * duration));
                        transcript.replace(nextSeg.getKey(), nextSeg.getValue().getStartTime());
                        txtView.setText(
                                StringUtils.stripToEmpty(StringUtils.replaceAll(seg.getWords(), " +", " ")));
                    }
                    break;
                } else {
                    // Keep on filling until we have ellipse
                    if (seg.isTrimmed()) {
                        break;
                    }

                    if (nextSeg != null && seg.getStartTime() == nextSeg.getValue().getStartTime()) {
                        break;
                    }
                    if (nextSeg == null) {
                        break;
                    }
                    Log.d(TAG, "start seg " + seg.getStartTime()
                            + " next seg " + nextSeg.getValue().getStartTime());
                    Log.d(TAG, "sink combining " + seg.getWords() + " + " + nextSeg.getValue().getWords());
                    seg.setWords(StringUtils.stripToEmpty(seg.getWords().replaceAll(" +", " ") + " "
                            + StringUtils.stripToEmpty(
                            nextSeg.getValue().getWords().replaceAll(" +", " "))));
                    seg.setEndTime(nextSeg.getValue().getEndTime());
                    transcript.remove(nextSeg);
                }
            } while (true);
        }
        return seg;
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