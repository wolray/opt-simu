package com.github.wolray.simu;

import java.util.LinkedList;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * @author wolray
 */
public class Conveyor extends Environment.Agent {
    private static final Integer PUT = 0, PULL = 1;
    private final int capacity;
    private final int period;
    public Conveyor next;
    public List<Object> cargoes = new LinkedList<>();

    public Conveyor(Environment env, int capacity, int period) {
        super(env);
        this.capacity = capacity;
        this.period = period;
    }

    public boolean isEmpty() {
        return cargoes.isEmpty();
    }

    private boolean isTerminal() {
        return next == null;
    }

    private boolean addCargo(Object cargo) {
        if (cargoes.size() < capacity) {
            cargoes.add(cargo);
            return true;
        }
        return false;
    }

    protected void refreshHook() {}

    private boolean pass() {
        if (next.isEmpty() || next.isTerminal()) {
            next.cargoes.addAll(cargoes);
            next.refreshHook();
            cargoes.clear();
            refreshHook();
            return true;
        }
        return false;
    }

    public void putOn(long time, Object cargo, LongConsumer after) {
        tryJob(PUT, time, t -> {
            boolean empty = isEmpty();
            boolean success = addCargo(cargo);
            if (empty) {
                deliver(t);
            }
            if (success) {
                refreshHook();
                if (after != null) {
                    after.accept(t);
                }
                return true;
            }
            return false;
        });
    }

    private void deliver(long time) {
        if (isEmpty() || isTerminal()) {
            return;
        }
        env.push(time + period, t -> {
            if (pass()) {
                resume(PUT, t);
                resume(PULL, t);
                next.deliver(t);
            } else {
                next.addDisposable(PULL, this::deliver);
            }
        });
    }
}
