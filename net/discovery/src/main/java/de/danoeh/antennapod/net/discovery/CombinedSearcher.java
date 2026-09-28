package de.danoeh.antennapod.net.discovery;

import android.text.TextUtils;
import android.util.Log;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit; // SHUFFLEPOD

public class CombinedSearcher implements PodcastSearcher {
    private static final String TAG = "CombinedSearcher";
    private static final long FIRST_RESULT_TIMEOUT_MS = 10000; // SHUFFLEPOD
    private static final long OTHERS_GRACE_MS = 1500; // SHUFFLEPOD

    public CombinedSearcher() {
    }

    public Single<List<PodcastSearchResult>> search(String query) {
        ArrayList<Disposable> disposables = new ArrayList<>();
        // SHUFFLEPOD: synchronized, results may be read before every provider has answered
        List<List<PodcastSearchResult>> singleResults = Collections.synchronizedList(new ArrayList<>(
                Collections.nCopies(PodcastSearcherRegistry.getSearchProviders().size(), null)));
        CountDownLatch firstResult = new CountDownLatch(1); // SHUFFLEPOD
        CountDownLatch latch = new CountDownLatch(PodcastSearcherRegistry.getSearchProviders().size());
        for (int i = 0; i < PodcastSearcherRegistry.getSearchProviders().size(); i++) {
            PodcastSearcherRegistry.SearcherInfo searchProviderInfo
                    = PodcastSearcherRegistry.getSearchProviders().get(i);
            PodcastSearcher searcher = searchProviderInfo.searcher;
            if (searchProviderInfo.weight <= 0.00001f || searcher.getClass() == CombinedSearcher.class) {
                latch.countDown();
                continue;
            }
            final int index = i;
            disposables.add(searcher.search(query).subscribe(e -> {
                        singleResults.set(index, e);
                        firstResult.countDown(); // SHUFFLEPOD
                        latch.countDown();
                    }, throwable -> {
                        Log.d(TAG, Log.getStackTraceString(throwable));
                        latch.countDown();
                    }
            ));
        }

        return Single.create((SingleOnSubscribe<List<PodcastSearchResult>>) subscriber -> {
            // SHUFFLEPOD: don't wait for the slowest provider. Once one has answered, give the others a
            // moment, then show what's there. Was latch.await().
            if (firstResult.await(FIRST_RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                latch.await(OTHERS_GRACE_MS, TimeUnit.MILLISECONDS);
            }
            List<List<PodcastSearchResult>> available;
            synchronized (singleResults) {
                available = new ArrayList<>(singleResults);
            }
            List<PodcastSearchResult> results = weightSearchResults(available); // SHUFFLEPOD
            subscriber.onSuccess(results);
        })
                .doOnDispose(() -> {
                    for (Disposable disposable : disposables) {
                        if (disposable != null) {
                            disposable.dispose();
                        }
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private List<PodcastSearchResult> weightSearchResults(List<List<PodcastSearchResult>> singleResults) {
        HashMap<String, Float> resultRanking = new HashMap<>();
        HashMap<String, PodcastSearchResult> urlToResult = new HashMap<>();
        for (int i = 0; i < singleResults.size(); i++) {
            float providerPriority = PodcastSearcherRegistry.getSearchProviders().get(i).weight;
            List<PodcastSearchResult> providerResults = singleResults.get(i);
            if (providerResults == null) {
                continue;
            }
            for (int position = 0; position < providerResults.size(); position++) {
                PodcastSearchResult result = providerResults.get(position);
                urlToResult.put(result.feedUrl, result);

                float ranking = 0;
                if (resultRanking.containsKey(result.feedUrl)) {
                    ranking = resultRanking.get(result.feedUrl);
                }
                ranking += 1.f / (position + 1.f);
                resultRanking.put(result.feedUrl, ranking * providerPriority);
            }
        }
        List<Map.Entry<String, Float>> sortedResults = new ArrayList<>(resultRanking.entrySet());
        Collections.sort(sortedResults, (o1, o2) -> Double.compare(o2.getValue(), o1.getValue()));

        List<PodcastSearchResult> results = new ArrayList<>();
        for (Map.Entry<String, Float> res : sortedResults) {
            results.add(urlToResult.get(res.getKey()));
        }
        return results;
    }

    @Override
    public Single<String> lookupUrl(String url) {
        return PodcastSearcherRegistry.lookupUrl(url);
    }

    @Override
    public boolean urlNeedsLookup(String url) {
        return PodcastSearcherRegistry.urlNeedsLookup(url);
    }

    @Override
    public String getName() {
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < PodcastSearcherRegistry.getSearchProviders().size(); i++) {
            PodcastSearcherRegistry.SearcherInfo searchProviderInfo
                    = PodcastSearcherRegistry.getSearchProviders().get(i);
            PodcastSearcher searcher = searchProviderInfo.searcher;
            if (searchProviderInfo.weight > 0.00001f && searcher.getClass() != CombinedSearcher.class) {
                names.add(searcher.getName());
            }
        }
        return TextUtils.join(", ", names);
    }
}
