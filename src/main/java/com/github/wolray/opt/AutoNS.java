package com.github.wolray.opt;

import java.util.*;

/**
 * @author wolray
 */
public abstract class AutoNS<S, V> {
    private final Comparator<V> comparator;
    protected TreeSet<Node> solutionSet = new TreeSet<>();

    protected long maxIterations = 0L;
    protected long maxNoImprovements = 0L;
    public int globalBetterScore = 5;
    public int localBetterScore = 3;
    public int localWorseScore = 2;
    public int maxSolutionSetSize = 10;
    public long maxTimeMillis = -1L;
    public Random random = new Random(42);

    protected Node best;
    protected int solutionCount = 0;
    protected int iteration = 0;
    protected int noImprovement = 0;

    protected abstract S initSolution();
    protected abstract S deepCopy(S solution);
    protected abstract V eval(S solution);
    protected abstract void epoch();
    protected abstract void updateOperator(int score);
    protected void initHook(Node node) {}
    protected void acceptHook(Node node) {}
    protected void solutionHook(Node curr, Node next, int score) {}
    protected void globalBetterHook() {}
    protected void localBetterHook() {}
    protected void localWorseHook() {}
    protected void timeoutHook(long duration) {}
    protected void reachMaxIterationsHook() {}
    protected void reachMaxNoImprovementsHook() {}
    protected boolean extraTerminatorHook() {return false;}
    protected void endHook() {}

    public AutoNS(Comparator<V> comparator) {this.comparator = comparator;}

    public Session session(long maxIterations, long maxNoImprovements) {
        this.maxIterations = maxIterations;
        this.maxNoImprovements = maxNoImprovements;
        return new Session();
    }

    private S solve(S initSolution, long startTimeMillis) {
        Node node = toNode(initSolution, null);
        accept(node);
        best = node;
        initHook(node);
        while (true) {
            epoch();
            if (terminate(startTimeMillis)) {
                break;
            }
            iteration++;
        }
        endHook();
        return best.solution;
    }

    protected int addNext(Node curr, Node next) {
        int score = 0;
        if (next.compareTo(best) < 0) {
            noImprovement = 0;
            best = next;
            globalBetterHook();
            score = globalBetterScore;
        } else {
            noImprovement++;
            if (next.compareTo(solutionSet.last()) < 0) {
                if (next.compareTo(curr) < 0) {
                    localBetterHook();
                    score = localBetterScore;
                } else {
                    localWorseHook();
                    score = localWorseScore;
                }
            }
        }
        if (score > 0 || solutionSet.size() < maxSolutionSetSize) {
            accept(next);
        } else {
            curr.miss++;
        }
        solutionHook(curr, next, score);
        return score;
    }

    private boolean terminate(Long startTimeMillis) {
        if (iteration >= maxIterations) {
            reachMaxIterationsHook();
            return true;
        }
        if (noImprovement >= maxNoImprovements) {
            reachMaxNoImprovementsHook();
            return true;
        }
        if (maxTimeMillis > 0) {
            long duration = System.currentTimeMillis() - startTimeMillis;
            if (duration >= maxTimeMillis) {
                timeoutHook(duration);
                return true;
            }
        }
        return extraTerminatorHook();
    }

    protected Node toNode(S solution, Node parent) {
        V value = eval(solution);
        return new Node(solution, value, ++solutionCount, parent);
    }

    private void accept(Node node) {
        acceptHook(node);
        solutionSet.add(node);
        if (solutionSet.size() > maxSolutionSetSize) {
            solutionSet.pollLast();
        }
    }

    public class Node implements Comparable<Node> {
        public final S solution;
        public final V value;
        public final int id;
        public final Node parent;
        int miss = 0;

        public Node(S solution, V value, int id, Node parent) {
            this.solution = solution;
            this.value = value;
            this.id = id;
            this.parent = parent;
        }

        public int getParentId() {
            return parent != null ? parent.id : -1;
        }

        @Override
        public String toString() {
            return Integer.toString(id);
        }

        @Override
        public int compareTo(Node other) {
            return comparator.compare(value, other.value);
        }
    }

    public class Session {
        public S solve() {
            long startTimeMillis = System.currentTimeMillis();
            S initSolution = initSolution();
            return AutoNS.this.solve(initSolution, startTimeMillis);
        }

        public S solve(S initSolution) {
            return AutoNS.this.solve(initSolution, System.currentTimeMillis());
        }
    }

    public static abstract class One<S, V> extends AutoNS<S, V> {
        public One(Comparator<V> comparator) {super(comparator);}
        protected abstract Node select();
    }

    public static abstract class OneToOne<S, V> extends One<S, V> {
        public OneToOne(Comparator<V> comparator) {super(comparator);}
        protected abstract void neighbor(Node curr, S copy);

        @Override
        public void epoch() {
            Node curr = select();
            S copy = deepCopy(curr.solution);
            neighbor(curr, copy);
            Node next = toNode(copy, curr);
            int score = addNext(curr, next);
            updateOperator(score);
        }
    }

    public static abstract class OneToMulti<S, V> extends One<S, V> {
        public OneToMulti(Comparator<V> comparator) {super(comparator);}
        protected abstract List<S> neighbors(Node curr);

        @Override
        public void epoch() {
            Node curr = select();
            List<S> neighbors = neighbors(curr);
            int score = 0;
            for (S nb : neighbors) {
                Node next = toNode(nb, curr);
                score = Math.max(score, addNext(curr, next));
            }
            updateOperator(score);
        }
    }

    public static <T> T rouletteWheelSelect(Map<T, Double> scores, Random random) {
        double sum = scores.values().stream().mapToDouble(i -> i).sum();
        double randomNumber = sum * random.nextDouble();
        for (Map.Entry<T, Double> score : scores.entrySet()) {
            randomNumber -= score.getValue();
            if (randomNumber < 0) {
                return score.getKey();
            }
        }
        return scores.keySet().iterator().next();
    }
}
