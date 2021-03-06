package com.accakyra.lsss.lsm.data.persistent.sst;

import com.accakyra.lsss.Record;
import com.accakyra.lsss.lsm.data.Resource;
import com.accakyra.lsss.lsm.data.TableConverter;
import com.accakyra.lsss.lsm.io.FileReader;
import lombok.extern.java.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;

@Log
public class SST implements Resource {

    private final NavigableMap<ByteBuffer, KeyInfo> index;
    private final Path sstFileName;
    private final Path indexFileName;
    private final int id;
    private final int level;

    public SST(NavigableMap<ByteBuffer, KeyInfo> index, int id, Path sstFileName, Path indexFileName, int level) {
        this.index = index;
        this.sstFileName = sstFileName;
        this.indexFileName = indexFileName;
        this.id = id;
        this.level = level;
    }

    @Override
    public Record get(ByteBuffer key) {
        if (key.compareTo(firstKey()) < 0) return null;
        if (index.get(key) != null) return get(key, index.get(key));

        return Optional.ofNullable(loadIndex(key, key))
                .map(index -> TableConverter.extractInfoFromIndexBuffer(index, key))
                .map(info -> get(key, info))
                .orElse(null);
    }

    public ByteBuffer firstKey() {
        return index.firstKey();
    }

    public ByteBuffer lastKey() {
        return index.lastKey();
    }

    public int getId() {
        return id;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public Iterator<Record> iterator() {
        return rangeIterator(index.firstKey(), index.lastKey(), true);
    }

    @Override
    public Iterator<Record> iterator(ByteBuffer from) {
        return rangeIterator(from, index.lastKey(), true);
    }

    @Override
    public Iterator<Record> iterator(ByteBuffer from, ByteBuffer to) {
        return rangeIterator(from, to, false);
    }

    public Iterator<Record> rangeIterator(ByteBuffer from, ByteBuffer to, boolean toInclusive) {
        ByteBuffer fromKey = index.floorKey(from);
        if (fromKey == null) fromKey = index.firstKey();

        ByteBuffer toKey = index.ceilingKey(to);
        if (toKey == null) toKey = index.lastKey();

        ByteBuffer indexBuffer = loadIndex(fromKey, toKey);
        if (indexBuffer == null) return Collections.emptyIterator();

        return TableConverter.parseIndexBuffer(indexBuffer)
                .entrySet()
                .stream()
                .map(entry -> get(entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull)
                .filter(record -> record.getKey().compareTo(from) >= 0)
                .filter(record -> {
                    int compare = record.getKey().compareTo(to);
                    return toInclusive ? compare <= 0 : compare < 0;
                })
                .iterator();
    }

    private ByteBuffer loadIndex(ByteBuffer from, ByteBuffer to) {
        ByteBuffer fromKey = index.floorKey(from);
        ByteBuffer toKey = index.ceilingKey(to);

        if (fromKey == null) fromKey = index.firstKey();
        if (toKey == null) toKey = index.lastKey();

        int readFrom = index.get(fromKey).getIndexOffset();
        KeyInfo toKeyInfo = index.get(toKey);
        int readTo = toKeyInfo.getIndexOffset() + toKeyInfo.getKeySize() + 12;
        int length = readTo - readFrom;

        try {
            return FileReader.read(indexFileName, readFrom + 4, length);
        } catch (IOException e) {
            log.log(java.util.logging.Level.SEVERE, "Cannot read index file : " + indexFileName.toString(), e);
            return null;
        }
    }

    private Record get(ByteBuffer key, KeyInfo info) {
        try {
            ByteBuffer value = FileReader.read(sstFileName, info.getSstOffset(), info.getValueSize());
            return new Record(key, value);
        } catch (IOException e) {
            log.log(java.util.logging.Level.SEVERE, "Cannot read sst file : " + sstFileName.toString(), e);
            return null;
        }
    }
}
