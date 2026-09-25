package de.danoeh.antennapod.ui.statistics.news;

import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.DateFormatSymbols;
import java.text.DecimalFormat;
import java.util.Calendar;

import de.danoeh.antennapod.storage.database.ShufflepodReleaseStats;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.statistics.R;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * SHUFFLEPOD: average audio released by the News shows for each day of the week,
 * in real time and at the current playback speeds.
 */
public class ShufflepodNewsReleasesFragment extends Fragment {
    private static final String TAG = "NewsReleasesFragment";
    private static final int WEEKS = 8;

    private Disposable disposable;
    private ProgressBar progressBar;
    private ScrollView scrollView;
    private LinearLayout content;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.shufflepod_news_releases, container, false);
        progressBar = root.findViewById(R.id.progressBar);
        scrollView = root.findViewById(R.id.scrollView);
        content = root.findViewById(R.id.content);
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        load();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.statistics_reset).setVisible(false);
        menu.findItem(R.id.statistics_filter).setVisible(false);
    }

    private void load() {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Observable.fromCallable(() -> ShufflepodReleaseStats.compute(WEEKS))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::show, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void show(ShufflepodReleaseStats.Result result) {
        progressBar.setVisibility(View.GONE);
        scrollView.setVisibility(View.VISIBLE);
        content.removeAllViews();
        if (result.getShowCount() == 0) {
            addText(getString(R.string.shufflepod_news_releases_empty));
            return;
        }
        addText(getResources().getQuantityString(R.plurals.shufflepod_news_releases_summary,
                result.getShowCount(), result.getShowCount(), result.getWeeks()));
        addRow(getString(R.string.shufflepod_news_releases_day), getString(R.string.shufflepod_news_releases_real),
                getString(R.string.shufflepod_news_releases_adjusted), true);
        String[] dayNames = DateFormatSymbols.getInstance().getWeekdays();
        int firstDay = Calendar.getInstance().getFirstDayOfWeek();
        for (int i = 0; i < 7; i++) {
            int day = (firstDay - 1 + i) % 7 + 1;
            addRow(dayNames[day], duration(result.getAverageMs(day)),
                    duration(result.getAverageAdjustedMs(day)), false);
        }
        addRow(getString(R.string.shufflepod_news_releases_per_day), duration(result.weeklyMs() / 7),
                duration(result.weeklyAdjustedMs() / 7), true);
        addRow(getString(R.string.shufflepod_news_releases_per_week), duration(result.weeklyMs()),
                duration(result.weeklyAdjustedMs()), true);

        addHeading(getString(R.string.shufflepod_news_releases_by_show));
        DecimalFormat speedFormat = new DecimalFormat("0.##");
        for (ShufflepodReleaseStats.ShowTotal show : result.getShows()) {
            String label = getString(R.string.shufflepod_news_releases_show_speed,
                    show.getFeed().getTitle(), speedFormat.format(show.getSpeed()));
            addRow(label, duration(show.getWeeklyMs()), duration(show.getWeeklyAdjustedMs()), false);
        }

        addText(getString(R.string.shufflepod_news_releases_speed_note));
        if (result.getEpisodesWithoutDuration() > 0) {
            addText(getResources().getQuantityString(R.plurals.shufflepod_news_releases_no_duration,
                    result.getEpisodesWithoutDuration(), result.getEpisodesWithoutDuration()));
        }
    }

    private String duration(long ms) {
        return Converter.getDurationStringLocalized(getResources(), ms, false);
    }

    private void addRow(String label, String real, String adjusted, boolean bold) {
        View row = getLayoutInflater().inflate(R.layout.shufflepod_news_releases_row, content, false);
        TextView labelText = row.findViewById(R.id.labelText);
        TextView realText = row.findViewById(R.id.realText);
        TextView adjustedText = row.findViewById(R.id.adjustedText);
        labelText.setText(label);
        realText.setText(real);
        adjustedText.setText(adjusted);
        if (bold) {
            labelText.setTypeface(null, Typeface.BOLD);
            realText.setTypeface(null, Typeface.BOLD);
            adjustedText.setTypeface(null, Typeface.BOLD);
        }
        content.addView(row);
    }

    private void addHeading(String text) {
        TextView view = addText(text);
        view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, 32, 0, 8);
    }

    private TextView addText(String text) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setPadding(0, 8, 0, 16);
        content.addView(view);
        return view;
    }
}
