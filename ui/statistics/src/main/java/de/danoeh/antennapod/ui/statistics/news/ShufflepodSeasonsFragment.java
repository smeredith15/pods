package de.danoeh.antennapod.ui.statistics.news;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.shufflepod.WeeklyReleases;
import de.danoeh.antennapod.storage.database.ShufflepodReleaseStats;
import de.danoeh.antennapod.storage.database.ShufflepodSeasonStats;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.statistics.R;
import de.danoeh.antennapod.ui.statistics.listening.ShufflepodLineChartView;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * SHUFFLEPOD: audio released per week over the last year, for the shows turned on in the list below the chart,
 * to see when seasonal shows (such as sports) overlap.
 */
public class ShufflepodSeasonsFragment extends Fragment {
    private static final String TAG = "SeasonsFragment";
    private static final String KEY_SELECTED = "selected";
    private static final int LISTENING_WEEKS = 8;

    private Disposable disposable;
    private ProgressBar progressBar;
    private ScrollView scrollView;
    private LinearLayout content;
    private ShufflepodLineChartView chart;
    private TextView summaryText;
    private ShufflepodSeasonStats.Result result;
    private volatile long listenedPerWeek;
    private Set<Long> selected;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.shufflepod_news_releases, container, false);
        progressBar = root.findViewById(R.id.progressBar);
        scrollView = root.findViewById(R.id.scrollView);
        content = root.findViewById(R.id.content);
        if (savedInstanceState != null && savedInstanceState.getLongArray(KEY_SELECTED) != null) {
            selected = new HashSet<>();
            for (long id : savedInstanceState.getLongArray(KEY_SELECTED)) {
                selected.add(id);
            }
        }
        return root;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selected != null) {
            long[] ids = new long[selected.size()];
            int i = 0;
            for (long id : selected) {
                ids[i++] = id;
            }
            outState.putLongArray(KEY_SELECTED, ids);
        }
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
        disposable = Observable.fromCallable(() -> {
            listenedPerWeek = ShufflepodReleaseStats.compute(LISTENING_WEEKS).getWeeklyListenedAdjustedMs();
            return ShufflepodSeasonStats.compute();
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::show, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void show(ShufflepodSeasonStats.Result result) {
        this.result = result;
        progressBar.setVisibility(View.GONE);
        scrollView.setVisibility(View.VISIBLE);
        content.removeAllViews();
        if (result.getShows().isEmpty()) {
            addText(getString(R.string.shufflepod_seasons_empty));
            return;
        }
        if (selected == null) {
            selected = new HashSet<>();
            for (ShufflepodSeasonStats.Show show : result.getShows()) {
                if (show.isNews()) {
                    selected.add(show.getFeed().getId());
                }
            }
        }
        addText(getString(R.string.shufflepod_seasons_intro));
        chart = new ShufflepodLineChartView(requireContext());
        chart.setMonthLabels(true);
        chart.setReferenceValue(listenedPerWeek > 0 ? listenedPerWeek : -1);
        content.addView(chart);
        if (listenedPerWeek > 0) {
            addText(getString(R.string.shufflepod_seasons_reference, duration(listenedPerWeek)));
        }
        summaryText = addText("");
        summaryText.setTypeface(null, Typeface.BOLD);

        boolean otherHeading = false;
        if (result.getShows().get(0).isNews()) {
            addHeading(getString(R.string.shufflepod_seasons_news));
        }
        for (ShufflepodSeasonStats.Show show : result.getShows()) {
            if (!show.isNews() && !otherHeading) {
                addHeading(getString(R.string.shufflepod_seasons_other));
                addText(getString(R.string.shufflepod_seasons_preview_tip));
                otherHeading = true;
            }
            addShowRow(show);
        }
        updateChart();
    }

    private void addShowRow(ShufflepodSeasonStats.Show show) {
        View row = getLayoutInflater().inflate(R.layout.shufflepod_season_row, content, false);
        final CheckBox checkBox = row.findViewById(R.id.checkBox);
        TextView titleText = row.findViewById(R.id.titleText);
        TextView subtitleText = row.findViewById(R.id.subtitleText);
        final long feedId = show.getFeed().getId();
        String title = show.getFeed().getTitle();
        titleText.setText(show.isSubscribed() ? title : getString(R.string.shufflepod_seasons_not_subscribed, title));
        long[] weekly = show.getWeeklyAdjustedMs();
        String subtitle = getString(R.string.shufflepod_seasons_show,
                duration(show.getTotalAdjustedMs() / ShufflepodSeasonStats.WEEKS),
                WeeklyReleases.activeWeeks(weekly), ShufflepodSeasonStats.WEEKS);
        if (show.getFirstRelease() > result.getWeekStart(1)) {
            subtitle += "\n" + getString(R.string.shufflepod_seasons_history, date(show.getFirstRelease()));
        }
        subtitleText.setText(subtitle);
        checkBox.setChecked(selected.contains(feedId));
        row.setOnClickListener(v -> {
            if (!selected.remove(feedId)) {
                selected.add(feedId);
            }
            checkBox.setChecked(selected.contains(feedId));
            updateChart();
        });
        content.addView(row);
    }

    private void updateChart() {
        List<long[]> series = new ArrayList<>();
        for (ShufflepodSeasonStats.Show show : result.getShows()) {
            if (selected.contains(show.getFeed().getId())) {
                series.add(show.getWeeklyAdjustedMs());
            }
        }
        long[] totals = WeeklyReleases.sum(series, ShufflepodSeasonStats.WEEKS);
        long[] times = new long[totals.length];
        long sum = 0;
        for (int i = 0; i < totals.length; i++) {
            times[i] = result.getWeekStart(i);
            sum += totals[i];
        }
        chart.setData(times, totals);
        int peak = WeeklyReleases.peak(totals);
        if (peak < 0) {
            summaryText.setText(R.string.shufflepod_seasons_none_selected);
            return;
        }
        String summary = getString(R.string.shufflepod_seasons_summary, duration(sum / totals.length),
                duration(totals[peak]), date(result.getWeekStart(peak)));
        if (listenedPerWeek > 0) {
            int above = WeeklyReleases.weeksAbove(totals, listenedPerWeek);
            summary += " " + getResources().getQuantityString(R.plurals.shufflepod_seasons_above, above, above);
        }
        summaryText.setText(summary);
    }

    private String duration(long ms) {
        return Converter.getDurationStringLocalized(getResources(), ms, false);
    }

    private String date(long time) {
        return DateUtils.formatDateTime(getContext(), time, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
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
