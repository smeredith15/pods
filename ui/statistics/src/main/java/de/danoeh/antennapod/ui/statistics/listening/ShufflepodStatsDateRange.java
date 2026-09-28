package de.danoeh.antennapod.ui.statistics.listening;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;

import org.greenrobot.eventbus.EventBus;

import java.util.Calendar;
import java.util.TimeZone;

import de.danoeh.antennapod.event.StatisticsEvent;
import de.danoeh.antennapod.ui.statistics.R;
import de.danoeh.antennapod.ui.statistics.StatisticsFragment;

/**
 * SHUFFLEPOD: "Choose dates" in the statistics filter, for filtering by day instead of by month.
 */
public final class ShufflepodStatsDateRange {
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private ShufflepodStatsDateRange() {
    }

    public static void attach(Context context, View button, AlertDialog dialog) {
        if (!(context instanceof FragmentActivity)) {
            button.setVisibility(View.GONE);
            return;
        }
        button.setOnClickListener(v -> {
            MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText(R.string.shufflepod_stats_choose_dates)
                    .setCalendarConstraints(new CalendarConstraints.Builder()
                            .setValidator(DateValidatorPointBackward.now()).build())
                    .build();
            picker.addOnPositiveButtonClickListener(selection -> {
                if (selection.first == null || selection.second == null) {
                    return;
                }
                SharedPreferences prefs = context.getSharedPreferences(StatisticsFragment.PREF_NAME,
                        Context.MODE_PRIVATE);
                prefs.edit()
                        .putBoolean(StatisticsFragment.PREF_INCLUDE_MARKED_PLAYED, false)
                        .putLong(StatisticsFragment.PREF_FILTER_FROM, localStartOfDay(selection.first))
                        .putLong(StatisticsFragment.PREF_FILTER_TO, localStartOfDay(selection.second) + DAY_MS)
                        .apply();
                EventBus.getDefault().post(new StatisticsEvent());
            });
            picker.show(((FragmentActivity) context).getSupportFragmentManager(), "shufflepod_stats_dates");
            dialog.dismiss();
        });
    }

    /**
     * The picker returns midnight UTC of the chosen day; converts it to midnight in the local time zone.
     */
    private static long localStartOfDay(long utcMidnight) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMidnight);
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
        return local.getTimeInMillis();
    }
}
