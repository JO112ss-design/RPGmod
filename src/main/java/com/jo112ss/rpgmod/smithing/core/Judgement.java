package com.jo112ss.rpgmod.smithing.core;

public enum Judgement {
    PERFECT, GOOD, BAD, MISS;

    public static Judgement byId(int id) {
        Judgement[] v = values();
        return id >= 0 && id < v.length ? v[id] : MISS;
    }
}
