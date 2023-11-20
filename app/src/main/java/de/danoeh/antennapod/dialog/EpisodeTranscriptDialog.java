package de.danoeh.antennapod.dialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import android.view.ViewGroup;
import android.view.Window;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;

import de.danoeh.antennapod.ItemTranscriptRVAdapter;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.util.PodcastIndexTranscriptUtils;
import de.danoeh.antennapod.core.util.gui.ShownotesCleaner;
import de.danoeh.antennapod.core.util.playback.PlaybackController;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.Transcript;
import de.danoeh.antennapod.model.feed.TranscriptSegment;
import de.danoeh.antennapod.model.playback.Playable;

public class EpisodeTranscriptDialog extends DialogFragment {
    public static String TAG = "EpisodeTranscriptDialog";
    private PlaybackController controller;
    static private EpisodeTranscriptDialog dialog;
    ItemTranscriptRVAdapter adapter = null;
    RecyclerView rv;
    View currentView = null;

    Transcript transcript;
    SortedMap<Long, TranscriptSegment> map;
    TreeMap<Long, TranscriptSegment> segmentsMap;

    public static EpisodeTranscriptDialog newInstance() {
        Bundle arguments = new Bundle();
        dialog = new EpisodeTranscriptDialog();
        dialog.setArguments(arguments);
        dialog.setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Holo_Light);
        return dialog;
    }

    public EpisodeTranscriptDialog() {
        // Empty constructor required for DialogFragment
    }

    @Override
    public void onStart() {
        super.onStart();
        controller = new PlaybackController(getActivity()) {
            @Override
            public void loadMediaInfo() {
                setupUi();
                setupAudioTracks();
            }
        };
        controller.init();
        setupUi();
    }

    @Override
    public void onStop() {
        super.onStop();
        controller.release();
        controller = null;
    }

    public void showDialog(FragmentActivity a, View v) {
        FragmentManager fragmentManager = a.getSupportFragmentManager();

        // The device is smaller, so show the fragment fullscreen.
        FragmentTransaction transaction = fragmentManager.beginTransaction();
            // For a polished look, specify a transition animation.
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            // To make it fullscreen, use the 'content' root view as the container
            // for the fragment, which is always the root view for the activity.
        transaction.add(android.R.id.content, this, "transcript");
        transaction.addToBackStack("transcript");
        transaction.commit();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.episode_transcript_list, container, false);
        rv = root.findViewById(R.id.transcript_recycler_view);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setRecycleChildrenOnDetach(true);
        rv.setLayoutManager(layoutManager);

        controller = new PlaybackController(getActivity()) {
            @Override
            public void loadMediaInfo() {
                load();
            }
        };
        controller.init();

        if (rv instanceof RecyclerView) {
            Context context = rv.getContext();
            RecyclerView recyclerView = (RecyclerView) rv;
            recyclerView.setLayoutManager(new LinearLayoutManager(context));

            Playable media = controller.getMedia();
            if (media != null && media instanceof FeedMedia) {
                FeedMedia feedMedia = ((FeedMedia) media);
                if (feedMedia.getItem() == null) {
                    feedMedia.setItem(DBReader.getFeedItem(feedMedia.getItemId()));
                }

                transcript = PodcastIndexTranscriptUtils.loadTranscript(feedMedia);

                adapter = new ItemTranscriptRVAdapter(transcript, context);
                recyclerView.setAdapter(adapter);
            }
        }

        return root;
    }
    private void load() {
        Log.d(TAG, "load()");
        /*
        if (webViewLoader != null) {
            webViewLoader.dispose();
        }
        */
        Context context = getContext();
        if (context == null) {
            return;
        }
        Playable media = controller.getMedia();
            if (media == null) {
                return;
            }
            String transcriptStr = "";
            if (media instanceof FeedMedia) {
                FeedMedia feedMedia = ((FeedMedia) media);
                if (feedMedia.getItem() == null) {
                    feedMedia.setItem(DBReader.getFeedItem(feedMedia.getItemId()));
                }

                transcript = PodcastIndexTranscriptUtils.loadTranscript(feedMedia);
                adapter = new ItemTranscriptRVAdapter(transcript, context);

                if (transcript != null) {
                    adapter.setTranscript(transcript);
                    adapter.notifyDataSetChanged();

                    segmentsMap = transcript.getSegmentsMap();
                    map = segmentsMap.tailMap(0L, true);
                    Iterator<Long> iter = map.keySet().iterator();
                    try {
                        while (true) {
                            Long l = iter.next();
                            long start = segmentsMap.get(l).getStartTime();
                            transcriptStr = transcriptStr.concat(
                                            "<a id=\"seg" + start + "\">"
                                                    + segmentsMap.get(l).getWords()
                                                    + "</a> "
                            );
                        }
                    } catch (NoSuchElementException e) {
                        // DONE
                    }
                    Log.d(TAG, "FULL TRANSCRIPT" + transcriptStr);
                }
            }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY | Window.FEATURE_LEFT_ICON);
        return dialog;
    }

    @SuppressLint("ResourceAsColor")
    public void scrollToPosition(long position) {
        if (getActivity() == null) {
            return;
        }
        if (segmentsMap == null) {
            return;
        }
        Map.Entry<Long, TranscriptSegment> entry = segmentsMap.floorEntry(position);
        if (entry != null) {
            Integer pos = adapter.positions.get(entry.getKey());
            if (pos != null) {
                Log.d(TAG, "Scrolling to position" + pos + " jump " + Long.toString(entry.getKey()));
                LinearSmoothScroller smoothScroller=new LinearSmoothScroller(getActivity()){
                    @Override
                    protected int getVerticalSnapPreference() {
                        return LinearSmoothScroller.SNAP_TO_START;
                    }
                };

                smoothScroller.setTargetPosition(pos);  // pos on which item you want to scroll recycler view
                rv.getLayoutManager().startSmoothScroll(smoothScroller);
                rv.scrollTo(0, 0);

                currentView = rv.getLayoutManager().findViewByPosition(pos);
                rv.getLayoutManager().findViewByPosition(pos);
                if (currentView != null) {
                    currentView.setBackgroundColor(R.color.light_gray);
                }
            }
        }
    }

    public void scrollToTop() {
        rv.getLayoutManager().scrollToPosition(0);
    }


    private void setupUi() {
    }

    private void setupAudioTracks() {
    }
}
