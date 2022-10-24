package com.github.wolray.simu;

/**
 * @author wolray
 */
public abstract class SimpleStarter<T> implements Environment.Starter {
    protected abstract Iterable<T> readObjects();

    protected abstract long getTime(T t);

    protected abstract void process(long time, T t);

    protected void beforeHook(Environment env) {}

    protected void afterHook(Environment env) {}

    @Override
    public final void start(Environment env) {
        beforeHook(env);
        readObjects().forEach(o -> env.push(getTime(o), t -> process(t, o)));
        afterHook(env);
    }
}
