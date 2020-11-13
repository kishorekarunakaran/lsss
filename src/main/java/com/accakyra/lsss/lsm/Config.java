package com.accakyra.lsss.lsm;

public class Config {
    /**
     * Maximum possible size of memtable before flushing
     * on persistence storage.
     */
    public final static int MEMTABLE_THRESHOLD = 4 * 1024 * 1024;

    /**
     * Maximum possible count of memtables storing in memory.
     */
    public final static int MAX_MEMTABLE_COUNT = 1;

    /**
     * The size ratio of adjacent levels.
     */
    public final static int FANOUT = 4;
}
