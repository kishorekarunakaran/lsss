package com.accakyra.lsss.lsm.data.persistent.sst;

import java.nio.ByteBuffer;
import java.util.NavigableMap;
import java.util.NavigableSet;

public class Index {

    private final int level;
    private final NavigableMap<ByteBuffer, KeyInfo> keys;

    public Index(int level, NavigableMap<ByteBuffer, KeyInfo> keys) {
        this.level = level;
        this.keys = keys;
    }

    public KeyInfo getKeyInfo(ByteBuffer key) {
        return keys.get(key);
    }

    public NavigableSet<ByteBuffer> keys() {
        return keys.navigableKeySet();
    }

    public int getLevel() {
        return level;
    }

    public ByteBuffer firstKey() {
        return keys.firstKey();
    }

    public ByteBuffer lastKey() {
        return keys.lastKey();
    }
}
