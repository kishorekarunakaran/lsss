package com.accakyra.lsss.lsm.data.memory;

import com.accakyra.lsss.Record;
import com.accakyra.lsss.lsm.Config;
import com.accakyra.lsss.lsm.data.Resource;
import com.accakyra.lsss.lsm.util.iterators.IteratorsUtil;
import com.google.common.collect.Iterators;

import java.nio.ByteBuffer;
import java.util.*;

public class Memtable implements Resource {

    private final NavigableMap<VersionedKey, ByteBuffer> memtable;
    private int snapshot;
    private int keysCapacity;
    private int valuesCapacity;
    private int uniqueKeysCount;

    public Memtable() {
        this.memtable = new TreeMap<>();
    }

    public boolean hasSpace() {
        return getTotalBytesCapacity() <= Config.MEMTABLE_THRESHOLD;
    }

    @Override
    public Record get(ByteBuffer key) {
        VersionedKey keyToFind = new VersionedKey(key, snapshot);
        VersionedKey recentKey = memtable.ceilingKey(keyToFind);
        if (recentKey != null && recentKey.getKey().equals(key)) {
            ByteBuffer value = memtable.get(recentKey);
            return new Record(key, value);
        }
        return null;
    }

    public void upsert(ByteBuffer key, ByteBuffer value) {
        if (get(key) == null) {
            uniqueKeysCount++;
            keysCapacity += key.limit();
        }
        valuesCapacity += value.limit();
        VersionedKey versionedKey = new VersionedKey(key, snapshot++);
        memtable.put(versionedKey, value);
    }

    public int getTotalBytesCapacity() {
        return keysCapacity + valuesCapacity;
    }

    public int getKeysCapacity() {
        return keysCapacity;
    }

    public int getUniqueKeysCount() {
        return uniqueKeysCount;
    }

    public boolean isEmpty() {
        return getUniqueKeysCount() == 0;
    }

    @Override
    public Iterator<Record> iterator() {
        if (memtable.isEmpty()) {
            return Collections.emptyIterator();
        }
        return iterator(memtable.firstKey().getKey());
    }

    @Override
    public Iterator<Record> iterator(ByteBuffer from) {
        return iterator(from, null);
    }

    @Override
    public Iterator<Record> iterator(ByteBuffer from, ByteBuffer to) {
        VersionedKey fromKey = new VersionedKey(from, snapshot);
        VersionedKey toKey = null;
        if (to != null) toKey = new VersionedKey(to, snapshot);

        Iterator<VersionedKey> versionedKeyIterator = IteratorsUtil.navigableIterator(memtable.navigableKeySet(), fromKey, toKey);
        Iterator<Record> recordIterator = Iterators.transform(
                versionedKeyIterator,
                (versionedKey) -> new Record(versionedKey.getKey(), memtable.get(versionedKey)));

        return IteratorsUtil.distinctIterator(recordIterator);
    }
}
