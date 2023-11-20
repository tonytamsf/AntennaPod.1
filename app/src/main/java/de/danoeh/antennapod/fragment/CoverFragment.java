package de.danoeh.antennapod.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.BlendModeColorFilterCompat;
import androidx.core.graphics.BlendModeCompat;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.resource.bitmap.FitCenter;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.snackbar.Snackbar;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.core.feed.util.ImageResourceUtils;
import de.danoeh.antennapod.core.util.ChapterUtils;
import de.danoeh.antennapod.core.util.DateFormatter;
import de.danoeh.antennapod.core.util.PodcastIndexTranscriptUtils;
import de.danoeh.antennapod.core.util.playback.PlaybackController;
import de.danoeh.antennapod.databinding.CoverFragmentBinding;
import de.danoeh.antennapod.dialog.AllEpisodesFilterDialog;
import de.danoeh.antennapod.dialog.EpisodeTranscriptDialog;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.EmbeddedChapterImage;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.Transcript;
import de.danoeh.antennapod.model.feed.TranscriptSegment;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.net.discovery.CombinedSearcher;
import io.reactivex.Maybe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.apache.commons.lang3.StringUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import java.util.Map;

import static android.widget.LinearLayout.LayoutParams.MATCH_PARENT;
import static android.widget.LinearLayout.LayoutParams.WRAP_CONTENT;

/**
 * Displays the cover and the title of a FeedItem.
 */
public class CoverFragment extends Fragment {
    private static final String TAG = "CoverFragment";
    private CoverFragmentBinding viewBinding;
    private PlaybackController controller;
    private Disposable disposable;
    private int displayedChapterIndex = -1;
    private Playable media;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        viewBinding = CoverFragmentBinding.inflate(inflater);
        viewBinding.imgvCover.setOnClickListener(v -> onPlayPause());
        viewBinding.openDescription.setOnClickListener(view -> ((AudioPlayerFragment) requireParentFragment())
                .scrollToPage(AudioPlayerFragment.POS_DESCRIPTION, true));
        ColorFilter colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                viewBinding.txtvPodcastTitle.getCurrentTextColor(), BlendModeCompat.SRC_IN);
        viewBinding.butNextChapter.setColorFilter(colorFilter);
        viewBinding.butPrevChapter.setColorFilter(colorFilter);
        viewBinding.descriptionIcon.setColorFilter(colorFilter);
        viewBinding.chapterButton.setOnClickListener(v ->
                new ChaptersFragment().show(getChildFragmentManager(), ChaptersFragment.TAG));
        viewBinding.butPrevChapter.setOnClickListener(v -> seekToPrevChapter());
        viewBinding.butNextChapter.setOnClickListener(v -> seekToNextChapter());
        viewBinding.txtvTranscript.setOnClickListener(v -> onTranscriptOverlay());
        return viewBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        configureForOrientation(getResources().getConfiguration());
    }

    private void loadMediaInfo(boolean includingChapters) {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Maybe.<Playable>create(emitter -> {
            Playable media = controller.getMedia();
            if (media != null) {
                if (includingChapters) {
                    ChapterUtils.loadChapters(media, getContext(), false);
                }
                emitter.onSuccess(media);
            } else {
                emitter.onComplete();
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(media -> {
                    this.media = media;
                    displayMediaInfo(media);
                    if (media.getChapters() == null && !includingChapters) {
                        loadMediaInfo(true);
                    }
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void displayMediaInfo(@NonNull Playable media) {
        String pubDateStr = DateFormatter.formatAbbrev(getActivity(), media.getPubDate());
        viewBinding.txtvPodcastTitle.setText(StringUtils.stripToEmpty(media.getFeedTitle())
                + "\u00A0"
                + "・"
                + "\u00A0"
                + StringUtils.replace(StringUtils.stripToEmpty(pubDateStr), " ", "\u00A0"));
        if (media instanceof FeedMedia) {
            Intent openFeed = MainActivity.getIntentToOpenFeed(requireContext(),
                    ((FeedMedia) media).getItem().getFeedId());
            viewBinding.txtvPodcastTitle.setOnClickListener(v -> startActivity(openFeed));
        } else {
            viewBinding.txtvPodcastTitle.setOnClickListener(null);
        }
        viewBinding.txtvPodcastTitle.setOnLongClickListener(v -> copyText(media.getFeedTitle()));
        viewBinding.txtvEpisodeTitle.setText(media.getEpisodeTitle());
        viewBinding.txtvEpisodeTitle.setOnLongClickListener(v -> copyText(media.getEpisodeTitle()));
        viewBinding.txtvEpisodeTitle.setOnClickListener(v -> {
            int lines = viewBinding.txtvEpisodeTitle.getLineCount();
            int animUnit = 1500;
            if (lines > viewBinding.txtvEpisodeTitle.getMaxLines()) {
                int titleHeight = viewBinding.txtvEpisodeTitle.getHeight()
                        - viewBinding.txtvEpisodeTitle.getPaddingTop()
                        - viewBinding.txtvEpisodeTitle.getPaddingBottom();
                ObjectAnimator verticalMarquee = ObjectAnimator.ofInt(
                        viewBinding.txtvEpisodeTitle, "scrollY", 0, (lines - viewBinding.txtvEpisodeTitle.getMaxLines())
                                        * (titleHeight / viewBinding.txtvEpisodeTitle.getMaxLines()))
                        .setDuration(lines * animUnit);
                ObjectAnimator fadeOut = ObjectAnimator.ofFloat(
                        viewBinding.txtvEpisodeTitle, "alpha", 0);
                fadeOut.setStartDelay(animUnit);
                fadeOut.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        viewBinding.txtvEpisodeTitle.scrollTo(0, 0);
                    }
                });
                ObjectAnimator fadeBackIn = ObjectAnimator.ofFloat(
                        viewBinding.txtvEpisodeTitle, "alpha", 1);
                AnimatorSet set = new AnimatorSet();
                set.playSequentially(verticalMarquee, fadeOut, fadeBackIn);
                set.start();
            }
        });
        
        displayedChapterIndex = -1;
        refreshChapterData(ChapterUtils.getCurrentChapterIndex(media, media.getPosition())); //calls displayCoverImage
        updateChapterControlVisibility();
        updateTranscriptControlVisibility();
    }

    private void updateTranscriptControlVisibility() {
        if (! (media instanceof FeedMedia)) {
            return;
        }

        if (! ((FeedMedia) media).getItem().hasTranscript()) {
            viewBinding.txtvTranscript.setVisibility(View.GONE);
        } else {
            viewBinding.txtvTranscript.setVisibility(View.VISIBLE);
        }
    }

    private void updateChapterControlVisibility() {
        boolean chapterControlVisible = false;
        if (media.getChapters() != null) {
            chapterControlVisible = media.getChapters().size() > 0;
        } else if (media instanceof FeedMedia) {
            FeedMedia fm = ((FeedMedia) media);
            // If an item has chapters but they are not loaded yet, still display the button.
            chapterControlVisible = fm.getItem() != null && fm.getItem().hasChapters();
        }
        int newVisibility = chapterControlVisible ? View.VISIBLE : View.GONE;
        if (viewBinding.chapterButton.getVisibility() != newVisibility) {
            viewBinding.chapterButton.setVisibility(newVisibility);
            ObjectAnimator.ofFloat(viewBinding.chapterButton,
                    "alpha",
                    chapterControlVisible ? 0 : 1,
                    chapterControlVisible ? 1 : 0)
                    .start();
        }
    }

    private void refreshChapterData(int chapterIndex) {
        if (chapterIndex > -1) {
            if (media.getPosition() > media.getDuration() || chapterIndex >= media.getChapters().size() - 1) {
                displayedChapterIndex = media.getChapters().size() - 1;
                viewBinding.butNextChapter.setVisibility(View.INVISIBLE);
            } else {
                displayedChapterIndex = chapterIndex;
                viewBinding.butNextChapter.setVisibility(View.VISIBLE);
            }
        }

        displayCoverImage();
    }

    private Chapter getCurrentChapter() {
        if (media == null || media.getChapters() == null || displayedChapterIndex == -1) {
            return null;
        }
        return media.getChapters().get(displayedChapterIndex);
    }

    private void seekToPrevChapter() {
        Chapter curr = getCurrentChapter();

        if (controller == null || curr == null || displayedChapterIndex == -1) {
            return;
        }

        if (displayedChapterIndex < 1) {
            controller.seekTo(0);
        } else if ((controller.getPosition() - 10000 * controller.getCurrentPlaybackSpeedMultiplier())
                < curr.getStart()) {
            refreshChapterData(displayedChapterIndex - 1);
            controller.seekTo((int) media.getChapters().get(displayedChapterIndex).getStart());
        } else {
            controller.seekTo((int) curr.getStart());
        }
    }

    private void seekToNextChapter() {
        if (controller == null || media == null || media.getChapters() == null
                || displayedChapterIndex == -1 || displayedChapterIndex + 1 >= media.getChapters().size()) {
            return;
        }

        refreshChapterData(displayedChapterIndex + 1);
        controller.seekTo((int) media.getChapters().get(displayedChapterIndex).getStart());
    }

    @Override
    public void onStart() {
        super.onStart();
        controller = new PlaybackController(getActivity()) {
            @Override
            public void loadMediaInfo() {
                CoverFragment.this.loadMediaInfo(false);
            }
        };
        controller.init();
        loadMediaInfo(false);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();

        if (disposable != null) {
            disposable.dispose();
        }
        controller.release();
        controller = null;
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlaybackPositionEvent event) {
        int newChapterIndex = ChapterUtils.getCurrentChapterIndex(media, event.getPosition());
        if (newChapterIndex > -1 && newChapterIndex != displayedChapterIndex) {
            refreshChapterData(newChapterIndex);
        }
    }

    private void displayCoverImage() {
        RequestOptions options = new RequestOptions()
                .dontAnimate()
                .transform(new FitCenter(),
                        new RoundedCorners((int) (16 * getResources().getDisplayMetrics().density)));

        RequestBuilder<Drawable> cover = Glide.with(this)
                .load(media.getImageLocation())
                .error(Glide.with(this)
                        .load(ImageResourceUtils.getFallbackImageLocation(media))
                        .apply(options))
                .apply(options);

        if (displayedChapterIndex == -1 || media == null || media.getChapters() == null
                || TextUtils.isEmpty(media.getChapters().get(displayedChapterIndex).getImageUrl())) {
            cover.into(viewBinding.imgvCover);
        } else {
            Glide.with(this)
                    .load(EmbeddedChapterImage.getModelFor(media, displayedChapterIndex))
                    .apply(options)
                    .thumbnail(cover)
                    .error(cover)
                    .into(viewBinding.imgvCover);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        configureForOrientation(newConfig);
    }

    private void configureForOrientation(Configuration newConfig) {
        boolean isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT;

        viewBinding.coverFragment.setOrientation(isPortrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);

        if (isPortrait) {
            viewBinding.coverHolder.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
            viewBinding.coverFragmentTextContainer.setLayoutParams(
                    new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        } else {
            viewBinding.coverHolder.setLayoutParams(new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
            viewBinding.coverFragmentTextContainer.setLayoutParams(new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        }

        ((ViewGroup) viewBinding.episodeDetails.getParent()).removeView(viewBinding.episodeDetails);
        if (isPortrait) {
            viewBinding.coverFragment.addView(viewBinding.episodeDetails);
        } else {
            viewBinding.coverFragmentTextContainer.addView(viewBinding.episodeDetails);
        }
    }

    void onPlayPause() {
        if (controller == null) {
            return;
        }
        controller.playPause();
    }

    void onTranscriptOverlay() {
        EpisodeTranscriptDialog.newInstance().show(getChildFragmentManager(), "transcript");
    }

    void updateTranscript(Playable media, int pos) {
        if (! (media instanceof FeedMedia)) {
            return;
        }
        Transcript transcript = PodcastIndexTranscriptUtils.loadTranscript((FeedMedia) media);
        if (transcript == null) {
            return;
        }
        Log.d(TAG, "looking for transcript at " + pos);

        TranscriptSegment seg = transcript.getSegmentAtTime(pos);
        if (seg != null) {
            Log.d(TAG, "showing transcript at " + pos + " -> " + seg.getWords());
            // TT TODO when we detect an ellipse on the textview, then remove the words from the end of the
            //  segment until the ellipse no longer
            Layout l;
            int lines = 0;
            do {
                viewBinding.txtvTranscript.setText(
                        StringUtils.stripToEmpty(StringUtils.replaceAll(seg.getWords(), " +", " ")));
                l = viewBinding.txtvTranscript.getLayout();
                if (l != null) {
                    lines = l.getLineCount();
                    //if (lines <= 1) {
                    //   break;
                    //}
                } else {
                    break;
                }
                // We have ellipsis, remove the last word
                Map.Entry<Long, TranscriptSegment> nextSeg = transcript.getSegmentAfterTime(seg.getEndTime());
                int origLen = 0;
                int ellipsisAway = l.getEllipsisCount(lines - 1);
                // TT TODO Using spaces to find which words we trim will not work for transcripts
                //  like Chinese that don't have spaces
                if (lines >= 2 && ellipsisAway > 0) {
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
                    nextSeg.getValue().setWords(lastTwoWords + " " + ellipsisStr + " " + nextSeg.getValue().getWords());
                    long duration = seg.getEndTime() - seg.getStartTime();
                    float ratio = ((float) (origLen - indexLastTwoWord) / (float) origLen);

                    nextSeg.getValue().setStartTime(nextSeg.getValue().getStartTime() - (long) (ratio * duration));
                    transcript.replace(nextSeg.getKey(), nextSeg.getValue().getStartTime());
                    viewBinding.txtvTranscript.setText(
                            StringUtils.stripToEmpty(StringUtils.replaceAll(seg.getWords(), " +", " ")));
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
    }

    private boolean copyText(String text) {
        ClipboardManager clipboardManager = ContextCompat.getSystemService(requireContext(), ClipboardManager.class);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("AntennaPod", text));
        }
        if (Build.VERSION.SDK_INT < 32) {
            ((MainActivity) requireActivity()).showSnackbarAbovePlayer(
                    getResources().getString(R.string.copied_to_clipboard), Snackbar.LENGTH_SHORT);
        }
        return true;
    }
}
