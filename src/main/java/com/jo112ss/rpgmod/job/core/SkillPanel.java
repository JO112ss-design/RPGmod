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
 * <p>Default layout (radius 3, 37 nodes):
 * <ul>
 *   <li>centre: start (grants the job's first active skill, and first spell for spell users)</li>
 *   <li>ring 1: stat nodes (1 point)</li>
 *   <li>ring 2: three skill nodes (2 points) and stat nodes; spell users also get spell nodes</li>
 *   <li>ring 3: ultimate nodes (3 points), more spells, and stronger stat nodes (2 points)</li>
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

    private static SkillPanel build(JobDefs.Job job) {
        List<Node> nodes = new ArrayList<>();
        List<JobStat> stats = job.panelStats();
        nodes.add(new Node(0, 0, 0, NodeType.START, null, 0, null, 0));
        // ring 1
        List<int[]> r1 = ring(1);
        for (int i = 0; i < r1.size(); i++) {
            JobStat st = stats.get(i % stats.size());
            nodes.add(new Node(nodes.size(), r1.get(i)[0], r1.get(i)[1], NodeType.STAT, st, st.nodeAmount(), null, 1));
        }
        // ring 2: skills at 0/4/8, spells at 2/6/10 for spell users
        List<int[]> r2 = ring(2);
        for (int i = 0; i < r2.size(); i++) {
            int[] p = r2.get(i);
            if (i % 4 == 0 && i / 4 + 1 < job.actives().size()) {
                nodes.add(new Node(nodes.size(), p[0], p[1], NodeType.SKILL, null, 0, job.actives().get(i / 4 + 1), 2));
            } else if (job.usesSpells() && i % 4 == 2 && i / 4 + 1 < job.spells().size()) {
                nodes.add(new Node(nodes.size(), p[0], p[1], NodeType.SPELL, null, 0, job.spells().get(i / 4 + 1), 2));
            } else {
                JobStat st = stats.get((i + 1) % stats.size());
                nodes.add(new Node(nodes.size(), p[0], p[1], NodeType.STAT, st, st.nodeAmount(), null, 1));
            }
        }
        // ring 3: ultimates on opposite sides (cells 1 and 10), remaining spells at 5/14
        List<int[]> r3 = ring(3);
        int ult = 0, spell = 4;
        for (int i = 0; i < r3.size(); i++) {
            int[] p = r3.get(i);
            if ((i == 1 || i == 10) && ult < job.ultimates().size()) {
                nodes.add(new Node(nodes.size(), p[0], p[1], NodeType.ULTIMATE, null, 0, job.ultimates().get(ult++), 3));
            } else if (job.usesSpells() && (i == 5 || i == 14) && spell < job.spells().size()) {
                nodes.add(new Node(nodes.size(), p[0], p[1], NodeType.SPELL, null, 0, job.spells().get(spell++), 2));
            } else {
                JobStat st = stats.get((i + 2) % stats.size());
                nodes.add(new Node(nodes.size(), p[0], p[1], NodeType.STAT, st, st.nodeAmount() * 1.5, null, 2));
            }
        }
        return new SkillPanel(nodes);
    }
}
