package de.danoeh.antennapod.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.fragment.app.Fragment;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.util.PodcastIndexTranscriptUtils;
import de.danoeh.antennapod.core.util.playback.PlaybackController;
import de.danoeh.antennapod.core.util.gui.ShownotesCleaner;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.Transcript;
import de.danoeh.antennapod.model.feed.TranscriptSegment;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.view.ShownotesWebView;
import io.reactivex.Maybe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Displays the description of a Playable object in a Webview.
 */
public class ItemTranscriptFragment extends Fragment {
    private static final String TAG = "ItemTranscriptFragment";

    private static final String PREF = "ItemTranscriptFragmentPrefs";
    private static final String PREF_SCROLL_Y = "prefScrollY";
    private static final String PREF_PLAYABLE_ID = "prefPlayableId";

    private ShownotesWebView webvDescription;
    private Disposable webViewLoader;
    private PlaybackController controller;

    SortedMap<Long, TranscriptSegment> map;
    TreeMap<Long, TranscriptSegment> segmentsMap;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "Creating view");
        View root = inflater.inflate(R.layout.item_description_fragment, container, false);
        webvDescription = root.findViewById(R.id.webview);
        ((WebView)webvDescription).getSettings().setJavaScriptEnabled(true);
        webvDescription.setTimecodeSelectedListener(time -> {
            if (controller != null) {
                controller.seekTo(time);
            }
        });
        webvDescription.setPageFinishedListener(() -> {
            // Restoring the scroll position might not always work
            webvDescription.postDelayed(ItemTranscriptFragment.this::restoreFromPreference, 50);
        });

        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right,
                                       int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (root.getMeasuredHeight() != webvDescription.getMinimumHeight()) {
                    webvDescription.setMinimumHeight(root.getMeasuredHeight());
                }
                root.removeOnLayoutChangeListener(this);
            }
        });
        registerForContextMenu(webvDescription);
        return root;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Fragment destroyed");
        if (webvDescription != null) {
            webvDescription.removeAllViews();
            webvDescription.destroy();
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return webvDescription.onContextItemSelected(item);
    }

    private void load() {
        Log.d(TAG, "load()");
        if (webViewLoader != null) {
            webViewLoader.dispose();
        }
        Context context = getContext();
        if (context == null) {
            return;
        }
        webViewLoader = Maybe.<String>create(emitter -> {
                    Playable media = controller.getMedia();
                    if (media == null) {
                        emitter.onComplete();
                        return;
                    }
                    String transcriptStr = "";
                    if (media instanceof FeedMedia) {
                        FeedMedia feedMedia = ((FeedMedia) media);
                        if (feedMedia.getItem() == null) {
                            feedMedia.setItem(DBReader.getFeedItem(feedMedia.getItemId()));
                        }
                        Transcript transcript = PodcastIndexTranscriptUtils.loadTranscript(feedMedia);
                        if (transcript != null) {
                            segmentsMap = transcript.getSegmentsMap();
                            map = segmentsMap.tailMap(0L, true);
                            Iterator<Long> iter = map.keySet().iterator();

                            try {
                                while (true) {
                                    Long l = iter.next();
                                    transcriptStr = transcriptStr.concat(
                                            "<a id=\"seg" + segmentsMap.get(l).getStartTime() + "\">"
                                           + segmentsMap.get(l).getWords() + "</a> "

                                    );
                                }
                            } catch (NoSuchElementException e) {
                                // DONE
                            }
                            Log.d(TAG, "FULL TRANSCRIPT" + transcriptStr);
                        }
                    }
                    String fullText = transcriptStr;
                    ShownotesCleaner shownotesCleaner = new ShownotesCleaner(
                            context, fullText, media.getDuration());
                    emitter.onSuccess(shownotesCleaner.processShownotes());
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(data -> {
                    webvDescription.loadDataWithBaseURL("https://127.0.0.1", data, "text/html",
                            "utf-8", "about:blank");
                    Log.d(TAG, "Webview loaded with data " + data);
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    @Override
    public void onPause() {
        super.onPause();
        savePreference();
    }

    public void scrollToPosition(long position) {
        Map.Entry<Long, TranscriptSegment> entry = segmentsMap.floorEntry(position);
        Log.d(TAG, "Scrolling to position" + position + " jump seg" + Long.toString(entry.getKey()));
        // TT TODO WebView.setWebContentsDebuggingEnabled(true);
        webvDescription.loadUrl("javascript:scrollAnchor(\"seg" + entry.getKey() + "\");");
    }
    private void savePreference() {
        Log.d(TAG, "Saving preferences");
        SharedPreferences prefs = getActivity().getSharedPreferences(PREF, Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (controller != null && controller.getMedia() != null && webvDescription != null) {
            Log.d(TAG, "Saving scroll position: " + webvDescription.getScrollY());
            editor.putInt(PREF_SCROLL_Y, webvDescription.getScrollY());
            editor.putString(PREF_PLAYABLE_ID, controller.getMedia().getIdentifier()
                    .toString());
        } else {
            Log.d(TAG, "savePreferences was called while media or webview was null");
            editor.putInt(PREF_SCROLL_Y, -1);
            editor.putString(PREF_PLAYABLE_ID, "");
        }
        editor.apply();
    }

    private boolean restoreFromPreference() {
        Log.d(TAG, "Restoring from preferences");
        Activity activity = getActivity();
        if (activity != null) {
            SharedPreferences prefs = activity.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
            String id = prefs.getString(PREF_PLAYABLE_ID, "");
            int scrollY = prefs.getInt(PREF_SCROLL_Y, -1);
            if (controller != null && scrollY != -1 && controller.getMedia() != null
                    && id.equals(controller.getMedia().getIdentifier().toString())
                    && webvDescription != null) {
                Log.d(TAG, "Restored scroll Position: " + scrollY);
                webvDescription.scrollTo(webvDescription.getScrollX(), scrollY);
                return true;
            }
        }
        return false;
    }

    public void scrollToTop() {
        webvDescription.scrollTo(0, 0);
        savePreference();
    }

    @Override
    public void onStart() {
        super.onStart();
        controller = new PlaybackController(getActivity()) {
            @Override
            public void loadMediaInfo() {
                load();
            }
        };
        controller.init();
        load();
    }

    @Override
    public void onStop() {
        super.onStop();

        if (webViewLoader != null) {
            webViewLoader.dispose();
        }
        controller.release();
        controller = null;
    }

    WebView getWebview() {
        return webvDescription;
    }
}
