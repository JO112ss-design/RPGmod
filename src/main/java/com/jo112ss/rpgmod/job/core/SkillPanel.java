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
 * <p>Layout (radius 7, 169 cells, far more than the points available at max level):
 * <ul>
 *   <li>centre: start (grants the job's first active skill, and first spell for spell users)</li>
 *   <li>rings 1-3: stat cells (1 point), rings 4-5: x1.5 (2 points), rings 6-7: x2 (3 points)</li>
 *   <li>skills spread over rings 2-6 at different angles (2-4 points by ring), spells between them</li>
 *   <li>ultimates on opposite sides of the outer ring (5 points)</li>
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
    public static final int VERSION = 5;
    /** Layout version of the advanced panels (bump when their cells change). */
    public static final int ADV_VERSION = 2;

    /**
     * Layout version of one job's base panel: {@link #VERSION}, or higher for a job whose cells changed
     * on their own (only that job's players get their points back).
     */
    /** Layout version of the advanced panel of a base job ({@link #ADV_VERSION}, or higher where it changed alone). */
    public static int advVersion(String baseJob) {
        return "healer".equals(baseJob) ? ADV_VERSION + 1 : ADV_VERSION; // bishop: health cells became mana regeneration
    }

    public static int version(String job) {
        return "healer".equals(job) ? VERSION + 1 : VERSION; // healer: heals moved to the staff, three new skills
    }
    public static final int RADIUS = 7;

    /** Stat strength by ring: inner rings x1, middle x1.5, outer x2. */
    static double ringMultiplier(int k) {
        return k <= 3 ? 1.0 : k <= 5 ? 1.5 : 2.0;
    }

    /**
     * Job mastery (%) a cell of a base job's panel needs: none for stat cells and the inner rings'
     * skills and spells, 25 / 50 on rings 4 / 5, 75 further out, 100 for the ultimates. Advanced
     * panels need none (the advanced job itself needs a mastered job).
     */
    public static int masteryNeeded(Node n) {
        return switch (n.type()) {
            case STAT, START -> 0;
            case ULTIMATE -> 100;
            default -> n.ring() <= 3 ? 0 : n.ring() == 4 ? 25 : n.ring() == 5 ? 50 : 75;
        };
    }

    static int statCost(int k) {
        return k <= 3 ? 1 : k <= 5 ? 2 : 3;
    }

    static int skillCost(int k) {
        return k <= 3 ? 2 : k <= 5 ? 3 : 4;
    }

    /** Screen-style angle (degrees, 0 = right, counter-clockwise) of a hex cell. */
    private static double angleOf(int q, int r) {
        double x = Math.sqrt(3) * (q + r / 2.0), y = -1.5 * r;
        double a = Math.toDegrees(Math.atan2(y, x));
        return a < 0 ? a + 360 : a;
    }

    /** The free cell of ring k closest to the given angle. */
    private static int[] pick(int k, double angle, Map<Long, String> taken) {
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int[] c : ring(k)) {
            if (taken.containsKey(key(c[0], c[1]))) continue;
            double d = Math.abs(((angleOf(c[0], c[1]) - angle) % 360 + 540) % 360 - 180);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    /**
     * Places the skill cells: actives spiral outwards (one per ring from 2 to 6, at different angles),
     * spells fill the gaps between them on alternating rings, and the ultimates sit on opposite sides
     * of the outer ring. Everything else is stat cells.
     */
    private static Map<Long, String[]> skillCells(JobDefs.Job job) {
        Map<Long, String> taken = new HashMap<>();
        Map<Long, String[]> out = new HashMap<>();
        // a base job's first active comes with the job; an advanced job learns all of its skills here
        boolean adv = JobDefs.isAdvanced(job.id());
        List<String> actives = adv || job.actives().isEmpty() ? job.actives() : job.actives().subList(1, job.actives().size());
        for (int j = 0; j < actives.size(); j++) {
            int k = 2 + j % 5;
            double angle = 30 + j * (360.0 / Math.max(1, actives.size())) * 1.0 + (j / 5) * 37;
            int[] c = pick(k, angle, taken);
            taken.put(key(c[0], c[1]), actives.get(j));
            out.put(key(c[0], c[1]), new String[] {"skill", actives.get(j)});
        }
        if (job.usesSpells()) {
            List<String> spells = adv ? job.spells() : job.spells().subList(1, job.spells().size());
            for (int j = 0; j < spells.size(); j++) {
                int k = 2 + (j * 2 + 1) % 5;
                double angle = 75 + j * (360.0 / spells.size());
                int[] c = pick(k, angle, taken);
                taken.put(key(c[0], c[1]), spells.get(j));
                out.put(key(c[0], c[1]), new String[] {"spell", spells.get(j)});
            }
        }
        for (int u = 0; u < job.ultimates().size() && u < 2; u++) {
            int[] c = pick(RADIUS, 120 + u * 180, taken);
            taken.put(key(c[0], c[1]), job.ultimates().get(u));
            out.put(key(c[0], c[1]), new String[] {"ultimate", job.ultimates().get(u)});
        }
        return out;
    }

    private static SkillPanel build(JobDefs.Job job) {
        List<Node> nodes = new ArrayList<>();
        List<JobStat> stats = job.panelStats();
        Map<Long, String[]> skills = skillCells(job);
        nodes.add(new Node(0, 0, 0, NodeType.START, null, 0, null, 0));
        for (int k = 1; k <= RADIUS; k++) {
            List<int[]> cells = ring(k);
            for (int i = 0; i < cells.size(); i++) {
                int[] p = cells.get(i);
                String[] sk = skills.get(key(p[0], p[1]));
                Node node;
                if (sk != null) {
                    NodeType type = switch (sk[0]) {
                        case "spell" -> NodeType.SPELL;
                        case "ultimate" -> NodeType.ULTIMATE;
                        default -> NodeType.SKILL;
                    };
                    int cost = type == NodeType.ULTIMATE ? 5 : skillCost(k);
                    node = new Node(nodes.size(), p[0], p[1], type, null, 0, sk[1], cost);
                } else {
                    JobStat st = stats.get((i + k) % stats.size());
                    node = new Node(nodes.size(), p[0], p[1], NodeType.STAT, st, st.nodeAmount() * ringMultiplier(k), null, statCost(k));
                }
                nodes.add(node);
            }
        }
        return new SkillPanel(nodes);
    }
}
