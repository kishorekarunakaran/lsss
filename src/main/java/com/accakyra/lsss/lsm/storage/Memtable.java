package com.accakyra.lsss.lsm.storage;

import com.accakyra.lsss.Record;
import com.accakyra.lsss.lsm.Config;

import java.nio.ByteBuffer;
import java.util.*;

public class Memtable implements Resource {

    private class SnapshotKey implements Comparable<SnapshotKey> {
        private final ByteBuffer key;
        private final int snapshot;

        public SnapshotKey(ByteBuffer key, int snapshot) {
            this.key = key;
            this.snapshot = snapshot;
        }

        public ByteBuffer getKey() {
            return key;
        }

        public int getSnapshot() {
            return snapshot;
        }

        @Override
        public int compareTo(SnapshotKey o) {
            int compare = key.compareTo(o.getKey());
            if (compare == 0) {
                return o.getSnapshot() - snapshot;
            } else {
                return compare;
            }
        }
    }

    private final NavigableMap<SnapshotKey, ByteBuffer> memtable;
    private int snapshot;
    private int keysCapacity;
    private int valuesCapacity;
    private int uniqueKeysCount;

    public Memtable() {
        this.memtable = new TreeMap<>();
    }

    public boolean canStore(ByteBuffer key, ByteBuffer value) {
        return key.limit() + value.limit() + getTotalBytesCapacity() <= Config.MEMTABLE_THRESHOLD;
    }

    @Override
    public Record get(ByteBuffer key) {
        SnapshotKey keyToFind = new SnapshotKey(key, snapshot);
        SnapshotKey recentKey = memtable.ceilingKey(keyToFind);
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
        SnapshotKey snapshotKey = new SnapshotKey(key, snapshot++);
        memtable.put(snapshotKey, value);
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

    @Override
    public Iterator<Record> iterator() {
        if (memtable.isEmpty()) {
            return Collections.emptyIterator();
        }
        SnapshotKey fromKey = new SnapshotKey(memtable.firstKey().getKey(), snapshot);
        return new MemtableIterator(fromKey);
    }

    @Override
    public Iterator<Record> iterator(ByteBuffer from) {
        SnapshotKey fromKey = new SnapshotKey(from, snapshot);
        return new MemtableIterator(fromKey);
    }

    @Override
    public Iterator<Record> iterator(ByteBuffer from, ByteBuffer to) {
        SnapshotKey fromKey = new SnapshotKey(from, snapshot);
        SnapshotKey toKey = new SnapshotKey(to, snapshot);
        return new MemtableIterator(fromKey, toKey);
    }

    private class MemtableIterator implements Iterator<Record> {

        private SnapshotKey current;
        private SnapshotKey to;
        private final int snapshot;

        public MemtableIterator(SnapshotKey from) {
            this.current = from;
            this.snapshot = from.snapshot;
            current = memtable.ceilingKey(from);
        }

        public MemtableIterator(SnapshotKey from, SnapshotKey to) {
            this.current = from;
            this.to = to;
            this.snapshot = from.snapshot;
            current = memtable.ceilingKey(from);
        }

        @Override
        public boolean hasNext() {
            if (current == null) return false;
            else if (to != null) {
                return current.getKey().compareTo(to.getKey()) < 0;
            } else return true;
        }

        @Override
        public Record next() {
            SnapshotKey nextKey = nextKey();
            return new Record(nextKey.getKey(), memtable.get(nextKey));
        }

        private SnapshotKey nextKey() {
            SnapshotKey key = current;
            while (true) {
                SnapshotKey next = memtable.higherKey(current);
                if (next == null) {
                    current = null;
                    break;
                } else {
                    if (next.getKey().compareTo(current.getKey()) > 0 && next.getSnapshot() < snapshot) {
                        current = next;
                        break;
                    }
                    current = next;
                }
            }
            return key;
        }
    }
}