package de.danoeh.antennapod.ui.statistics.listening;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import de.danoeh.antennapod.storage.database.ShufflepodListeningStats;
import de.danoeh.antennapod.storage.database.StatisticsItem;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.statistics.R;

/**
 * SHUFFLEPOD: the "average per day of the week" block above the shows on the Podcasts statistics tab.
 */
public class ShufflepodWeekdayListening {
    private final WeekdayAdapter adapter = new WeekdayAdapter();
    private volatile long[] averages = new long[8];

    /**
     * Puts the weekday block above the shows. Call only after the shows adapter has data: it can't report
     * its item count before its first update, and ConcatAdapter asks for it right away.
     */
    public void attach(RecyclerView recyclerView, RecyclerView.Adapter<?> shows) {
        if (recyclerView.getAdapter() instanceof ConcatAdapter) {
            adapter.notifyDataSetChanged();
            return;
        }
        recyclerView.setAdapter(new ConcatAdapter(adapter, shows));
    }

    /**
     * Computes the averages for the filter range. Must be called off the main thread.
     */
    public void load(long from, long to) {
        averages = ShufflepodListeningStats.averagePerWeekday(from, to);
    }

    /**
     * Shows that were never listened to in the range are left out of the list.
     */
    public static void removeUnplayed(List<StatisticsItem> items) {
        Iterator<StatisticsItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().timePlayed <= 0) {
                iterator.remove();
            }
        }
    }

    private class WeekdayAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout layout = new LinearLayout(parent.getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (16 * parent.getResources().getDisplayMetrics().density);
            layout.setPadding(padding, padding / 2, padding, padding / 2);
            layout.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerView.ViewHolder(layout) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            LinearLayout layout = (LinearLayout) holder.itemView;
            layout.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(layout.getContext());
            TextView heading = new TextView(layout.getContext());
            heading.setText(R.string.shufflepod_listening_by_weekday);
            heading.setTypeface(null, Typeface.BOLD);
            layout.addView(heading);
            String[] dayNames = DateFormatSymbols.getInstance().getWeekdays();
            int firstDay = Calendar.getInstance().getFirstDayOfWeek();
            long[] current = averages;
            for (int i = 0; i < 7; i++) {
                int day = (firstDay - 1 + i) % 7 + 1;
                View row = inflater.inflate(R.layout.shufflepod_news_releases_row, layout, false);
                ((TextView) row.findViewById(R.id.labelText)).setText(dayNames[day]);
                ((TextView) row.findViewById(R.id.realText)).setText(
                        Converter.getDurationStringLocalized(layout.getResources(), current[day], false));
                row.findViewById(R.id.adjustedText).setVisibility(View.GONE);
                layout.addView(row);
            }
            TextView note = new TextView(layout.getContext());
            note.setText(R.string.shufflepod_listening_by_weekday_note);
            layout.addView(note);
        }

        @Override
        public int getItemCount() {
            return 1;
        }
    }
}
