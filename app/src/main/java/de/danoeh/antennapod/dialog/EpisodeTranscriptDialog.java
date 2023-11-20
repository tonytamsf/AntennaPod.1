package de.danoeh.antennapod.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.core.util.playback.PlaybackController;
import java.util.List;

public class EpisodeTranscriptDialog extends DialogFragment {
    private PlaybackController controller;
    static private EpisodeTranscriptDialog dialog;

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
        View rv = root.findViewById(R.id.transcript_recycler_view);

        return root;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY | Window.FEATURE_LEFT_ICON);
        return dialog;
    }

    private void setupUi() {
    }

    private void setupAudioTracks() {
    }
}
