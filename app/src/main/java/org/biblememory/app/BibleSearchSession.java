package org.biblememory.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Main-thread search state. The worker only reads the shared corpus and index. */
final class BibleSearchSession {
    enum Scope { READER, PICKER }

    interface Host {
        Scope scope();
        void showSearchUpdate(boolean reuseResultsList, String message);
    }

    private final Context applicationContext;
    private final Bible bible;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor =
        Executors.newSingleThreadExecutor(r -> new Thread(r, "OfflineBibleSearch"));
    private Host host;
    private String query = "";
    private String error = "";
    private boolean active;
    private boolean loading;
    private List<BibleSearchLogic.Hit> hits = Collections.emptyList();
    private int generation;
    private Future<?> task;
    private Runnable debounce;
    private boolean needsRefresh;

    BibleSearchSession(Context applicationContext, Bible bible, Host host) {
        this.applicationContext = applicationContext;
        this.bible = bible;
        this.host = host;
    }

    String query() { return query; }
    String error() { return error; }
    boolean active() { return active; }
    boolean loading() { return loading; }
    List<BibleSearchLogic.Hit> hits() { return hits; }

    void cancel() {
        generation++;
        if (debounce != null) handler.removeCallbacks(debounce);
        debounce = null;
        if (task != null) task.cancel(true);
        task = null;
    }

    void suspend() {
        if (!loading) return;
        cancel();
        loading = false;
        needsRefresh = true;
    }

    void resume() {
        Scope origin = scope();
        if (origin == null || !needsRefresh) return;
        needsRefresh = false;
        if (BibleSearchLogic.isBlankQuery(query)) return;
        active = true;
        loading = true;
        int expected = generation;
        handler.post(() -> {
            if (current(origin, expected)) submit(query);
        });
    }

    void reset() {
        cancel();
        needsRefresh = false;
        query = "";
        error = "";
        active = false;
        loading = false;
        hits = Collections.emptyList();
    }

    void changed(String value) {
        Scope origin = scope();
        if (origin == null) return;
        if (!begin(value)) return;
        int expected = generation;
        debounce = () -> {
            if (current(origin, expected)) submit(value);
        };
        handler.postDelayed(debounce, 300);
    }

    void submit(String value) {
        Scope origin = scope();
        if (origin == null || !begin(value)) return;
        String requested = query;
        int expected = generation;
        task = executor.submit(() -> perform(origin, expected, requested));
    }

    private boolean begin(String value) {
        cancel();
        needsRefresh = false;
        query = value == null ? "" : value;
        boolean blank = BibleSearchLogic.isBlankQuery(query);
        boolean changed = active || loading || !hits.isEmpty() || !error.isEmpty();
        active = loading = !blank;
        hits = Collections.emptyList();
        error = "";
        if (blank) {
            if (changed) host.showSearchUpdate(false, null);
            return false;
        }
        host.showSearchUpdate(true, "Поиск...");
        return true;
    }

    private void perform(Scope origin, int expected, String requested) {
        List<BibleSearchLogic.Hit> result;
        String failure = "";
        try {
            bible.loadInBackground(applicationContext).get();
            BibleSearchLogic.prepare(applicationContext);
            result = BibleSearchLogic.search(bible, requested);
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception exception) {
            result = Collections.emptyList();
            failure = "Не удалось выполнить поиск.";
        }
        if (Thread.currentThread().isInterrupted()) return;
        List<BibleSearchLogic.Hit> ready = result;
        String problem = failure;
        Runnable publish = () -> {
            if (!current(origin, expected)) return;
            loading = false;
            hits = ready;
            error = problem;
            host.showSearchUpdate(problem.isEmpty(), null);
        };
        if (Looper.myLooper() == handler.getLooper()) publish.run();
        else handler.post(publish);
    }

    private Scope scope() { return host == null ? null : host.scope(); }

    private boolean current(Scope origin, int expected) {
        return generation == expected && scope() == origin;
    }

    void close() {
        host = null;
        cancel();
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }
}
