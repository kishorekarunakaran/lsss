# lsss

lsss is yet another log-structured merge-tree implementation

Note: It isn't production ready k/v storage, so don't use it in production.

## Implementation details 

### Write

All new updates handled by in-memory memtable, and when memtable size become bigger
than the special threshold (16 MB by default) it asynchronously flushes to disk.

### Read

First all read request go to memtable. If memtable contains record we are looking for,
so we are just return this record. If memtable doesn't contain value lsss tries to
find value in levels. For this purpose each level scan throw all sparse indexes to find one which might 
contain value. If level has a sparse index which might contains record, lsss reads part of full index from disk, 
parse it, and if index contains the right key, value will be read from sst file on disk.

### Deletion

Special value (tombstone) will be inserted for mark some key as deleted.
Tombstone will never be returned to client. It's just mean storage doesn't contain some key.

### Iteration

lsss support full and range scan with "from" (inclusive) and "to" (not inclusive).
Iterator represent view of database at some time point
(between calling iterator method and getting iterator object back).
So, iterator will never see records inserted after iterators creation.
This behavior is the reason why lsss provide closable iterator. It helps
don't remove old sst files which unnecessary for work with new requests,
but probably contain records for iterator. Don't forget to close it.

### Memtable

Memtable is just binary search tree. For support lsm functionality memtable store some metrics
about itself. Based on this metrics lsss understand when it has to be compacted.

### SST

SST is a bunch of sorted k/v pairs.

### Index

Index is bunch of sorted map (key -> info about key).
Main component of info is a offset of value in sst file.

Sparse index contains part of real index to decrease ram usage.

### Level

Level is bunch of sorted SST. Every level bigger than previous one in T(10 by default) times.
   
### Level-0

Level-0 contains new flushed SST. It works just like usual level but SST might overlap each other.

### Compaction

When some level reach it's capacity (10 SST for level-0, 100 sst for level-1 and so on)
lsss start compaction process. In case of level-0 it collects all SST from level,
for any another it randomly collects SST while level will not fit in normal size.  
All collected SSTs merge with underlying level. So, underlying level might reaches its own capacity too 
and compaction will be called on this level too.

### Performance

Note: Tests are were made depending on my naive understanding of OS and disk I/O. So be carefully analyzing this.

Setup:
A million records per test. Each record has a 16 byte key, and a 100 byte value. 
       
    SSD:        SAMSUNG MZVLB512HAJQ-00000
    CPU:        Intel(R) Core(TM) i7-10510U CPU @ 1.80GHz
    Keys:       16 bytes each
    Values:     100 bytes each
    Records:    1000000
    Raw Size:   110.6 MB

See actual code in test/PerformanceTest

    Random read/write
    Read: 4.8 MB per second
    Write: 59 MB per second

## Example Usage

```java
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class Main {
    public static void main(String[] args) {

        // Insert and get
        try (DAO dao = DAOFactory.create(new File("/home/accakyra/temp/"))) {
            ByteBuffer key1 = stringToByteBuffer("key1");
            ByteBuffer key2 = stringToByteBuffer("key2");

            ByteBuffer value1 = stringToByteBuffer("value1");
            ByteBuffer value2 = stringToByteBuffer("value2");

            dao.upsert(key1, value1);
            dao.upsert(key2, value2);

            System.out.println(byteBufferToString(dao.get(key1)));
            System.out.println(byteBufferToString(dao.get(key2)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // iteration
        try (DAO dao = DAOFactory.create(new File("/home/accakyra/temp/"))) {
            ByteBuffer key1 = stringToByteBuffer("key1");
            ByteBuffer key2 = stringToByteBuffer("key2");

            ByteBuffer value1 = stringToByteBuffer("value1");
            ByteBuffer value2 = stringToByteBuffer("value2");

            dao.upsert(key1, value1);
            dao.upsert(key2, value2);

            try (CloseableIterator<Record> iterator = dao.iterator()) {
                while (iterator.hasNext()) {
                    String value = byteBufferToString(iterator.next().getValue());
                    System.out.println(value);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }        
    }

    private static ByteBuffer stringToByteBuffer(String data) {
        return ByteBuffer.wrap(data.getBytes());
    }

    private static String byteBufferToString(ByteBuffer buffer) {
        int size = buffer.capacity();
        byte[] data = new byte[size];
        buffer.get(data);
        return new String(data);
    }
}
```
