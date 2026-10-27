package com.jo112ss.rpgmod.job.fx;

import com.jo112ss.rpgmod.network.FxPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Builds and sends {@link FxPayload}s to the players near an effect. */
public final class ServerFx {
    /** Players further than this do not get the effect (particles are culled long before that anyway). */
    public static final double RANGE = 80;

    private final String fx;
    private final Vec3 pos;
    private Vec3 vec = Vec3.ZERO;
    private float a, b;
    private int color;
    private int entity = -1;
    private final List<Vec3> points = new ArrayList<>();

    private ServerFx(String fx, Vec3 pos) {
        this.fx = fx;
        this.pos = pos;
    }

    public static ServerFx of(String fx, Vec3 pos) {
        return new ServerFx(fx, pos);
    }

    public ServerFx vec(Vec3 v) {
        this.vec = v;
        return this;
    }

    public ServerFx a(double v) {
        this.a = (float) v;
        return this;
    }

    public ServerFx b(double v) {
        this.b = (float) v;
        return this;
    }

    public ServerFx color(int rgb) {
        this.color = rgb;
        return this;
    }

    public ServerFx follow(Entity e) {
        this.entity = e == null ? -1 : e.getId();
        return this;
    }

    public ServerFx point(Vec3 p) {
        points.add(p);
        return this;
    }

    public ServerFx points(List<? extends Entity> entities) {
        for (Entity e : entities) points.add(e.position().add(0, e.getBbHeight() * 0.5, 0));
        return this;
    }

    public void send(ServerLevel level) {
        send(level, RANGE);
    }

    /** Dev tests: send no effects (a benchmark running the server at full speed). */
    public static boolean mute;

    public void send(ServerLevel level, double range) {
        if (mute) return;
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, range,
                new FxPayload(fx, pos, vec, a, b, color, entity, List.copyOf(points)));
    }
}
