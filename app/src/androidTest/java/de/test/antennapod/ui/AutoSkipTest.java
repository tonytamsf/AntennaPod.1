package de.test.antennapod.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.feed.FeedMedia;
import de.danoeh.antennapod.core.feed.FeedPreferences;
import de.danoeh.antennapod.core.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.core.preferences.PlaybackPreferences;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.service.playback.PlayerStatus;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.util.playback.Playable;
import de.danoeh.antennapod.core.util.playback.PlaybackController;
import de.danoeh.antennapod.fragment.QueueFragment;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.IgnoreOnCi;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static de.test.antennapod.EspressoTestUtils.waitForView;
import static de.test.antennapod.NthMatcher.first;
import static org.hamcrest.Matchers.allOf;

/**
 * User interface tests for auto skip intro and ending
 */
@RunWith(AndroidJUnit4.class)
@IgnoreOnCi
public class AutoSkipTest {

    @Rule
    public ActivityTestRule<MainActivity> activityRule = new ActivityTestRule<>(MainActivity.class, false, false);
    private UITestUtils uiTestUtils;
    private String[] availableSpeeds;
    private PlaybackController controller;

    @Before
    public void setUp() throws Exception {
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.makeNotFirstRun();
        EspressoTestUtils.clearDatabase();
        EspressoTestUtils.setLastNavFragment(QueueFragment.TAG);

        Context context = getInstrumentation().getTargetContext();

        uiTestUtils = new UITestUtils(context);
        uiTestUtils.setup();
        uiTestUtils.setMediaFileName("30sec.mp3");
        uiTestUtils.addLocalFeedData(true);

        // April 19, 2020 TT TODO: Should we just create a test feed preference ??
        Feed feed = uiTestUtils.hostedFeeds.get(0);
        FeedPreferences prefs = new FeedPreferences(
                feed.getId(),
                true,
                FeedPreferences.AutoDeleteAction.GLOBAL,
                VolumeAdaptionSetting.OFF,
                "whatever",
                "whatever"
        );
        feed.setPreferences(prefs);

        prefs.setFeedSkipIntro(5);
        prefs.setFeedSkipEnding(10);
        prefs.save(context);

        List<FeedItem> queue = DBReader.getQueue();
        PlaybackPreferences.writeMediaPlaying(queue.get(0).getMedia(), PlayerStatus.PAUSED, false);

        EspressoTestUtils.tryKillPlaybackService();
        activityRule.launchActivity(new Intent().putExtra(MainActivity.EXTRA_OPEN_PLAYER, true));
        controller = new PlaybackController(activityRule.getActivity());
        controller.init();
        FeedMedia playable = (FeedMedia) controller.getMedia(); // To load media
        playable.getItem().getFeed().setPreferences(prefs);
    }

    @After
    public void tearDown() throws Exception {
        uiTestUtils.tearDown();
    }


    @Test
    public void testSkipIntro() {
        onView(isRoot()).perform(waitForView(withId(R.id.butPlay), 1000));
        controller.playPause();
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(()
                -> controller.getStatus() == PlayerStatus.PLAYING);
        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(()
                -> controller.getStatus() == PlayerStatus.PAUSED);
    }

}
