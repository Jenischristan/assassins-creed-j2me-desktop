package javax.microedition.rms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecordStore {
    private static final int MAGIC = 0x41435253;
    private static final int VERSION = 1;
    private static final Map<String, RecordStore> OPEN_STORES =
            new HashMap<String, RecordStore>();

    private final String name;
    private final File file;
    private final List<byte[]> records = new ArrayList<byte[]>();
    private boolean open = true;

    private RecordStore(String name, File file) {
        this.name = name;
        this.file = file;
    }

    public static File getStorageDirectory() {
        String home = System.getProperty("user.home", ".");
        return new File(home, ".local/share/assassins-creed/records");
    }

    private static String sanitize(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        if (sb.length() == 0) {
            sb.append("_recordstore");
        }
        return sb.toString();
    }

    public static synchronized RecordStore openRecordStore(String recordStoreName,
            boolean createIfNecessary) throws RecordStoreException {
        RecordStore existing = OPEN_STORES.get(recordStoreName);
        if (existing != null && existing.open) {
            return existing;
        }

        File dir = getStorageDirectory();
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new RecordStoreException("cannot create " + dir);
        }
        File file = new File(dir, sanitize(recordStoreName) + ".rms");

        RecordStore store = new RecordStore(recordStoreName, file);
        if (file.isFile()) {
            try {
                store.load();
            } catch (IOException e) {
                throw new RecordStoreException("cannot read " + file + ": " + e);
            }
        } else if (!createIfNecessary) {
            throw new RecordStoreNotFoundException("no record store " + recordStoreName);
        }

        OPEN_STORES.put(recordStoreName, store);
        return store;
    }

    public String getName() {
        return name;
    }

    public int getNumRecords() {
        return records.size();
    }

    public int getNextRecordID() {
        return records.size() + 1;
    }

    public synchronized int addRecord(byte[] data, int offset, int numBytes)
            throws RecordStoreException {
        checkOpen();
        if (data == null && numBytes > 0) {
            throw new NullPointerException("data is null");
        }
        byte[] copy = new byte[numBytes];
        if (numBytes > 0) {
            System.arraycopy(data, offset, copy, 0, numBytes);
        }
        records.add(copy);
        save();
        return records.size();
    }

    public synchronized void setRecord(int recordId, byte[] newData,
            int offset, int numBytes) throws RecordStoreException {
        checkOpen();
        int index = recordId - 1;
        if (index < 0 || index >= records.size()) {
            throw new InvalidRecordIDException("invalid record id " + recordId);
        }
        byte[] copy = new byte[numBytes];
        if (numBytes > 0) {
            System.arraycopy(newData, offset, copy, 0, numBytes);
        }
        records.set(index, copy);
        save();
    }

    public synchronized int getRecord(int recordId, byte[] buffer, int offset)
            throws RecordStoreException {
        checkOpen();
        int index = recordId - 1;
        if (index < 0 || index >= records.size()) {
            throw new InvalidRecordIDException("invalid record id " + recordId);
        }
        byte[] record = records.get(index);
        System.arraycopy(record, 0, buffer, offset, record.length);
        return record.length;
    }

    public synchronized byte[] getRecord(int recordId) throws RecordStoreException {
        checkOpen();
        int index = recordId - 1;
        if (index < 0 || index >= records.size()) {
            throw new InvalidRecordIDException("invalid record id " + recordId);
        }
        return records.get(index).clone();
    }

    public synchronized int getRecordSize(int recordId) throws RecordStoreException {
        checkOpen();
        int index = recordId - 1;
        if (index < 0 || index >= records.size()) {
            throw new InvalidRecordIDException("invalid record id " + recordId);
        }
        return records.get(index).length;
    }

    public synchronized void deleteRecord(int recordId) throws RecordStoreException {
        checkOpen();
        int index = recordId - 1;
        if (index < 0 || index >= records.size()) {
            throw new InvalidRecordIDException("invalid record id " + recordId);
        }
        records.remove(index);
        save();
    }

    public synchronized void closeRecordStore() throws RecordStoreException {
        if (!open) {
            throw new RecordStoreNotOpenException("record store is closed");
        }
        save();
        open = false;
        if (OPEN_STORES.get(name) == this) {
            OPEN_STORES.remove(name);
        }
    }

    public synchronized void deleteRecordStore() throws RecordStoreException {
        if (open) {
            throw new RecordStoreException("record store is open");
        }
        if (file.exists() && !file.delete()) {
            throw new RecordStoreException("cannot delete " + file);
        }
    }

    private void checkOpen() throws RecordStoreNotOpenException {
        if (!open) {
            throw new RecordStoreNotOpenException("record store is closed");
        }
    }

    private void load() throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        int magic = in.readInt();
        int version = in.readInt();
        if (magic != MAGIC || version != VERSION) {
            throw new IOException("bad record store format");
        }
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            int length = in.readInt();
            byte[] data = new byte[length];
            in.readFully(data);
            records.add(data);
        }
    }

    private void save() throws RecordStoreException {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(records.size());
            for (byte[] record : records) {
                out.writeInt(record.length);
                out.write(record);
            }
            out.flush();

            File temp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.write(temp.toPath(), bytes.toByteArray());
            try {
                Files.move(temp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(temp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RecordStoreException("cannot save " + file + ": " + e);
        }
    }
}
