package com.getpcpanel.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.schedulers.ExecutorScheduler;
import io.reactivex.subjects.PublishSubject;
import jakarta.annotation.PreDestroy;

@Service
public class Debouncer {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorScheduler rxScheduler = new ExecutorScheduler(scheduler, false);

    private final Map<Object, RxScheduled> delayedMap = new ConcurrentHashMap<>();

    public void debounce(Object key, Runnable runnable, long delay, TimeUnit unit) {
        delayedMap.compute(key, computeStream(x -> x.debounce(delay, unit, rxScheduler), runnable));
    }

    public void rateLimit(Object key, Runnable runnable, long delay, TimeUnit unit) {
        delayedMap.compute(key, computeStream(x -> x.throttleLatest(delay, unit), runnable));
    }

    private BiFunction<Object, RxScheduled, RxScheduled> computeStream(
            Function<Observable<Object>, Observable<Object>> operator,
            Runnable runnable) {
        return (k, v) -> {
            if (v != null) {
                v.runnable.set(runnable);
                v.subject.onNext("");
                return v;
            }

            var subject = PublishSubject.create();
            var result = new RxScheduled(new AtomicReference<>(runnable), subject);
            result.disposer.set(operator.apply(subject).subscribe(ignored -> result.runnable.get().run()));

            subject.onNext("");
            return result;
        };
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    record RxScheduled(AtomicReference<Runnable> runnable, PublishSubject<Object> subject, AtomicReference<Disposable> disposer) {
        RxScheduled(AtomicReference<Runnable> runnable, PublishSubject<Object> subject) {
            this(runnable, subject, new AtomicReference<>(null));
        }
    }
}
