package com.github.wolray.simu;

import java.util.*;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/**
 * @author wolray
 */
public abstract class Environment {
    public static final int TIME_BIT = 22;
    public final PriorityQueue<Event> queue = new PriorityQueue<>(this::compare);
    public final List<Starter> starters = new ArrayList<>();
    long count;

    protected abstract long makeEventId(long time, int prior, long eventCount);
    protected abstract int compare(Event e1, Event e2);

    public static Environment create() {
        return new Environment() {
            @Override
            protected long makeEventId(long time, int prior, long eventCount) {
                return eventCount;
            }
            @Override
            protected int compare(Event e1, Event e2) {
                int diff = Long.compare(e1.time, e2.time);
                if (diff != 0) {
                    return diff;
                }
                diff = e1.prior - e2.prior;
                if (diff != 0) {
                    return diff;
                }
                return Long.compare(e1.id, e2.id);
            }
        };
    }

    public static Environment fast(int maxPriorBits) {
        assert maxPriorBits >= 0 && maxPriorBits < 10;
        int priorBit = TIME_BIT - maxPriorBits;
        int countMask = (1 << maxPriorBits) - 1;
        return new Environment() {
            @Override
            protected long makeEventId(long time, int prior, long eventCount) {
                return (time << TIME_BIT) | ((long)prior << priorBit) | (eventCount & countMask);
            }
            @Override
            protected int compare(Event e1, Event e2) {
                return Long.compare(e1.id, e2.id);
            }
        };
    }

    public long getCount() {
        return count;
    }

    public void addStarter(Starter starter) {
        starters.add(starter);
    }

    public Event push(long time, LongConsumer action) {
        return push(time, 0, action);
    }

    public Event push(long time, int prior, LongConsumer action) {
        long id = makeEventId(time, prior, count++);
        Event event = new Event(time, prior, id, action);
        queue.add(event);
        return event;
    }

    public void play(int startTime, int simGap, int realGap, LongConsumer action, Terminator terminator) {
        addStarter(env -> repeat(startTime, simGap, t -> {
            long tic = System.currentTimeMillis();
            action.accept(t);
            try {
                Thread.sleep(Math.max(0, realGap - (System.currentTimeMillis() - tic)));
            } catch (InterruptedException ignore) {}
        }, terminator));
        run();
    }

    public void run() {
        run(t -> false);
    }

    public void run(Terminator terminator) {
        for (Starter starter : starters) {
            starter.start(this);
        }
        Event event;
        long time = 0;
        while ((event = queue.poll()) != null) {
            time = event.time;
            if (terminator.test(time)) {
                break;
            }
            event.action.accept(time);
        }
        endHook(time);
    }

    public void endHook(long time) {}

    public void repeat(long start, long period, LongConsumer action) {
        repeat(start, period, 0, action, null);
    }

    public void repeat(long start, long period, int prior, LongConsumer action) {
        repeat(start, period, prior, action, null);
    }

    public void repeat(long start, long period, LongConsumer action, Terminator terminator) {
        repeat(start, period, 0, action, terminator);
    }

    public void repeat(long start, long period, int prior, LongConsumer action, Terminator terminator) {
        new Repeater().run(start, period, prior, action, terminator);
    }

    public interface Starter {
        void start(Environment env);
    }

    public interface Terminator {
        boolean test(long time);
    }

    public static class Event {
        final long time;
        final int prior;
        final long id;
        LongConsumer action;

        public Event(long time, int prior, long id, LongConsumer action) {
            this.time = time;
            this.prior = prior;
            this.id = id;
            this.action = action;
        }

        public long getTime() {
            return time;
        }

        public Event append(LongConsumer nextAction) {
            action = action.andThen(nextAction);
            return this;
        }

        @Override
        public String toString() {
            return time + ":" + prior;
        }
    }

    public static class Agent {
        protected final Environment env;
        private final Map<Object, Queue<LongPredicate>> jobsMap = new HashMap<>();

        public Agent(Environment env) {
            this.env = env;
        }

        public void tryJob(Object id, long time, LongPredicate job) {
            addJob(id, job);
            resume(id, time);
        }

        public void addDisposable(Object id, LongConsumer job) {
            addJob(id, t -> {
                job.accept(t);
                return true;
            });
        }

        public void addJob(Object id, LongPredicate job) {
            jobsMap.computeIfAbsent(id, k -> new LinkedList<>()).add(job);
        }

        public void resume(Object id, long time) {
            Queue<LongPredicate> jobs = jobsMap.get(id);
            if (jobs != null) {
                jobs.removeIf(job -> job.test(time));
            }
        }
    }

    class Repeater {
        LongConsumer consumer;

        void run(long time, long period, int prior, LongConsumer action, Terminator terminator) {
            if (consumer == null) {
                if (terminator == null) {
                    consumer = t -> {
                        action.accept(t);
                        push(t + period, prior, consumer);
                    };
                } else {
                    consumer = t -> {
                        if (!terminator.test(t)) {
                            action.accept(t);
                            push(t + period, prior, consumer);
                        }
                    };
                }
            }
            push(time, prior, consumer);
        }
    }
}
