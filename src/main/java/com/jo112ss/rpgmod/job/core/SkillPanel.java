package com.jo112ss.rpgmod.job.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A job's honeycomb skill panel. Nodes sit on a hex grid (axial coordinates q,r); the centre is
 * unlocked from the start and any node next to an unlocked node can be bought with skill points.
 *
 * <p>Layout (radius 5, 91 cells, far more than the points available at max level):
 * <ul>
 *   <li>centre: start (grants the job's first active skill, and first spell for spell users)</li>
 *   <li>rings 1-2: stat cells (1 point); ring 2 also holds three skills (2 points) and spells</li>
 *   <li>rings 3-4: stronger stat cells (2 points); ring 4 holds the last spell</li>
 *   <li>ring 5: the strongest stat cells (3 points) and two ultimates (4 points) on opposite sides</li>
 * </ul>
 */
public final class SkillPanel {
    public enum NodeType { START, STAT, SKILL, ULTIMATE, SPELL }

    public record Node(int id, int q, int r, NodeType type, JobStat stat, double amount, String skill, int cost) {
        public int ring() {
            return (Math.abs(q) + Math.abs(r) + Math.abs(-q - r)) / 2;
        }
    }

    private static final int[][] DIRS = {{1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}};

    private final List<Node> nodes;
    private final Map<Long, Node> byPos = new HashMap<>();

    private SkillPanel(List<Node> nodes) {
        this.nodes = Collections.unmodifiableList(nodes);
        for (Node n : nodes) byPos.put(key(n.q, n.r), n);
    }

    public List<Node> nodes() {
        return nodes;
    }

    public Node node(int id) {
        return id >= 0 && id < nodes.size() ? nodes.get(id) : null;
    }

    public Node at(int q, int r) {
        return byPos.get(key(q, r));
    }

    public List<Node> neighbours(Node n) {
        List<Node> out = new ArrayList<>(6);
        for (int[] d : DIRS) {
            Node o = at(n.q + d[0], n.r + d[1]);
            if (o != null) out.add(o);
        }
        return out;
    }

    /** True if the node can be bought: not owned yet and touching an owned node. */
    public boolean isReachable(Node n, Set<Integer> unlocked) {
        if (unlocked.contains(n.id)) return false;
        for (Node o : neighbours(n)) if (unlocked.contains(o.id)) return true;
        return false;
    }

    public int spent(Collection<Integer> unlocked) {
        int s = 0;
        for (int id : unlocked) {
            Node n = node(id);
            if (n != null) s += n.cost;
        }
        return s;
    }

    private static long key(int q, int r) {
        return ((long) q << 32) ^ (r & 0xffffffffL);
    }

    // ------------------------------------------------------------------ layouts

    private static final Map<String, SkillPanel> CACHE = new HashMap<>();

    public static synchronized SkillPanel forJob(String jobId) {
        return CACHE.computeIfAbsent(jobId, id -> build(JobDefs.job(id)));
    }

    /** Cells of ring k, walking around from the "east-south" corner. */
    private static List<int[]> ring(int k) {
        List<int[]> out = new ArrayList<>();
        if (k == 0) {
            out.add(new int[] {0, 0});
            return out;
        }
        int q = DIRS[4][0] * k, r = DIRS[4][1] * k;
        for (int side = 0; side < 6; side++) {
            for (int step = 0; step < k; step++) {
                out.add(new int[] {q, r});
                q += DIRS[side][0];
                r += DIRS[side][1];
            }
        }
        return out;
    }

    /** Panel layout version; bump when node ids change so saved progress is refunded. */
    public static final int VERSION = 2;
    public static final int RADIUS = 5;

    private static SkillPanel build(JobDefs.Job job) {
        List<Node> nodes = new ArrayList<>();
        List<JobStat> stats = job.panelStats();
        nodes.add(new Node(0, 0, 0, NodeType.START, null, 0, null, 0));
        for (int k = 1; k <= RADIUS; k++) {
            List<int[]> cells = ring(k);
            int n = cells.size();
            for (int i = 0; i < n; i++) {
                int[] p = cells.get(i);
                Node node = special(job, k, i, n, nodes.size(), p);
                if (node == null) {
                    JobStat st = stats.get((i + k) % stats.size());
                    double mult = k <= 2 ? 1.0 : k == 3 ? 1.25 : k == 4 ? 1.5 : 2.0;
                    int cost = k <= 2 ? 1 : k <= 4 ? 2 : 3;
                    node = new Node(nodes.size(), p[0], p[1], NodeType.STAT, st, st.nodeAmount() * mult, null, cost);
                }
                nodes.add(node);
            }
        }
        return new SkillPanel(nodes);
    }

    /**
     * Skill / spell / ultimate cells: actives spread around ring 2, spells between them (ring 2) and
     * on ring 4, ultimates at opposite points of the outer ring.
     */
    private static Node special(JobDefs.Job job, int ring, int i, int n, int id, int[] p) {
        if (ring == 2) {
            if (i % 4 == 0 && i / 4 + 1 < job.actives().size()) {
                return new Node(id, p[0], p[1], NodeType.SKILL, null, 0, job.actives().get(i / 4 + 1), 2);
            }
            if (job.usesSpells() && i % 4 == 2 && i / 4 + 1 < job.spells().size()) {
                return new Node(id, p[0], p[1], NodeType.SPELL, null, 0, job.spells().get(i / 4 + 1), 2);
            }
        }
        if (ring == 4 && job.usesSpells() && i == n / 2 && 4 < job.spells().size()) {
            return new Node(id, p[0], p[1], NodeType.SPELL, null, 0, job.spells().get(4), 3);
        }
        if (ring == RADIUS) {
            int u = i == 2 ? 0 : i == 2 + n / 2 ? 1 : -1;
            if (u >= 0 && u < job.ultimates().size()) {
                return new Node(id, p[0], p[1], NodeType.ULTIMATE, null, 0, job.ultimates().get(u), 4);
            }
        }
        return null;
    }
}
