package pt.ubi.pdm.projetofinal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class sqlite extends SQLiteOpenHelper {

    private static final String DB_NAME = "oa.db";
    private static final int DB_VERSION = 2; // ⬅️ subimos para 2

    // Tabela DIARY
    private static final String T_DIARY = "diary";
    private static final String C_DIARY_DATEID = "dateId";
    private static final String C_DIARY_TEXT = "text";
    private static final String C_DIARY_UPDATED = "updatedAt";

    // Tabela MOOD
    private static final String T_MOOD = "mood";
    private static final String C_MOOD_DATEID = "dateId";
    private static final String C_MOOD_VALUE = "mood";
    private static final String C_MOOD_UPDATED = "updatedAt";

    // Tabela OUTBOX
    private static final String T_OUTBOX = "outbox";
    private static final String C_OUTBOX_ID = "id";
    private static final String C_OUTBOX_TYPE = "type";
    private static final String C_OUTBOX_KEY = "keyRef";
    private static final String C_OUTBOX_PAYLOAD = "payloadJson";
    private static final String C_OUTBOX_UPDATED = "updatedAt";
    private static final String C_OUTBOX_STATUS = "status";
    private static final String C_OUTBOX_RETRIES = "retries";

    // Tabela REFLECTIONS (nova)
    private static final String T_REFLECTIONS = "reflections";
    private static final String C_REFL_ID = "id";         // UUID
    private static final String C_REFL_DATEID = "dateId"; // "yyyy-MM-dd"
    private static final String C_REFL_TEXT = "text";
    private static final String C_REFL_UPDATED = "updatedAt";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    public sqlite(@Nullable Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        // Diary
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_DIARY + " (" +
                C_DIARY_DATEID + " TEXT PRIMARY KEY," +
                C_DIARY_TEXT + " TEXT NOT NULL," +
                C_DIARY_UPDATED + " INTEGER NOT NULL)");
        // Mood
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_MOOD + " (" +
                C_MOOD_DATEID + " TEXT PRIMARY KEY," +
                C_MOOD_VALUE + " INTEGER NOT NULL," +
                C_MOOD_UPDATED + " INTEGER NOT NULL)");
        // Outbox
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_OUTBOX + " (" +
                C_OUTBOX_ID + " TEXT PRIMARY KEY," +
                C_OUTBOX_TYPE + " TEXT NOT NULL," +
                C_OUTBOX_KEY + " TEXT NOT NULL," +
                C_OUTBOX_PAYLOAD + " TEXT NOT NULL," +
                C_OUTBOX_UPDATED + " INTEGER NOT NULL," +
                C_OUTBOX_STATUS + " TEXT NOT NULL," +
                C_OUTBOX_RETRIES + " INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_outbox_status ON " + T_OUTBOX + "(" + C_OUTBOX_STATUS + ")");

        // Reflections (nova)
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_REFLECTIONS + " (" +
                C_REFL_ID + " TEXT PRIMARY KEY," +
                C_REFL_DATEID + " TEXT NOT NULL," +
                C_REFL_TEXT + " TEXT NOT NULL," +
                C_REFL_UPDATED + " INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reflections_date ON " + T_REFLECTIONS + "(" + C_REFL_DATEID + ")");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + T_REFLECTIONS + " (" +
                    C_REFL_ID + " TEXT PRIMARY KEY," +
                    C_REFL_DATEID + " TEXT NOT NULL," +
                    C_REFL_TEXT + " TEXT NOT NULL," +
                    C_REFL_UPDATED + " INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_reflections_date ON " + T_REFLECTIONS + "(" + C_REFL_DATEID + ")");
        }
    }

    // ===== Diary =====
    public static class DiaryEntry {
        public String dateId;
        public String text;
        public long updatedAt;
    }
    public void upsertDiary(String dateId, String text, long updatedAt) {
        ContentValues cv = new ContentValues();
        cv.put(C_DIARY_DATEID, dateId);
        cv.put(C_DIARY_TEXT, text);
        cv.put(C_DIARY_UPDATED, updatedAt);
        getWritableDatabase().insertWithOnConflict(T_DIARY, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }
    public DiaryEntry getDiaryByDate(String dateId) {
        Cursor c = getReadableDatabase().query(T_DIARY,
                new String[]{C_DIARY_DATEID, C_DIARY_TEXT, C_DIARY_UPDATED},
                C_DIARY_DATEID + "=?", new String[]{dateId}, null, null, null);
        try {
            if (c.moveToFirst()) {
                DiaryEntry e = new DiaryEntry();
                e.dateId = c.getString(0);
                e.text = c.getString(1);
                e.updatedAt = c.getLong(2);
                return e;
            }
            return null;
        } finally { c.close(); }
    }
    public List<DiaryEntry> getDiaryLastNDays(int n) {
        ArrayList<DiaryEntry> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(T_DIARY,
                new String[]{C_DIARY_DATEID, C_DIARY_TEXT, C_DIARY_UPDATED},
                null, null, null, null, C_DIARY_DATEID + " DESC", String.valueOf(n));
        try {
            while (c.moveToNext()) {
                DiaryEntry e = new DiaryEntry();
                e.dateId = c.getString(0);
                e.text = c.getString(1);
                e.updatedAt = c.getLong(2);
                list.add(e);
            }
        } finally { c.close(); }
        return list;
    }

    // ===== Mood =====
    public static class MoodLog {
        public String dateId;
        public int mood;
        public long updatedAt;
    }
    public void upsertMood(String dateId, int mood, long updatedAt) {
        ContentValues cv = new ContentValues();
        cv.put(C_MOOD_DATEID, dateId);
        cv.put(C_MOOD_VALUE, mood);
        cv.put(C_MOOD_UPDATED, updatedAt);
        getWritableDatabase().insertWithOnConflict(T_MOOD, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }
    public MoodLog getMoodByDate(String dateId) {
        Cursor c = getReadableDatabase().query(T_MOOD,
                new String[]{C_MOOD_DATEID, C_MOOD_VALUE, C_MOOD_UPDATED},
                C_MOOD_DATEID + "=?", new String[]{dateId}, null, null, null);
        try {
            if (c.moveToFirst()) {
                MoodLog m = new MoodLog();
                m.dateId = c.getString(0);
                m.mood = c.getInt(1);
                m.updatedAt = c.getLong(2);
                return m;
            }
            return null;
        } finally { c.close(); }
    }
    public List<MoodLog> getMoodLastNDays(int n) {
        ArrayList<MoodLog> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(T_MOOD,
                new String[]{C_MOOD_DATEID, C_MOOD_VALUE, C_MOOD_UPDATED},
                null, null, null, null, C_MOOD_DATEID + " DESC", String.valueOf(n));
        try {
            while (c.moveToNext()) {
                MoodLog m = new MoodLog();
                m.dateId = c.getString(0);
                m.mood = c.getInt(1);
                m.updatedAt = c.getLong(2);
                list.add(m);
            }
        } finally { c.close(); }
        return list;
    }

    // ===== Outbox =====
    public static class OutboxOperation {
        public String id;
        public String type;
        public String keyRef;
        public String payloadJson;
        public long updatedAt;
        public String status;
        public int retries;
    }
    public String enqueue(String type, String keyRef, String payloadJson, long updatedAt) {
        String id = UUID.randomUUID().toString();
        ContentValues cv = new ContentValues();
        cv.put(C_OUTBOX_ID, id);
        cv.put(C_OUTBOX_TYPE, type);
        cv.put(C_OUTBOX_KEY, keyRef);
        cv.put(C_OUTBOX_PAYLOAD, payloadJson);
        cv.put(C_OUTBOX_UPDATED, updatedAt);
        cv.put(C_OUTBOX_STATUS, STATUS_PENDING);
        cv.put(C_OUTBOX_RETRIES, 0);
        getWritableDatabase().insert(T_OUTBOX, null, cv);
        return id;
    }
    public List<OutboxOperation> getPending(int limit) {
        ArrayList<OutboxOperation> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(T_OUTBOX,
                new String[]{C_OUTBOX_ID, C_OUTBOX_TYPE, C_OUTBOX_KEY, C_OUTBOX_PAYLOAD, C_OUTBOX_UPDATED, C_OUTBOX_STATUS, C_OUTBOX_RETRIES},
                C_OUTBOX_STATUS + "=?", new String[]{STATUS_PENDING},
                null, null, C_OUTBOX_UPDATED + " ASC", String.valueOf(limit));
        try {
            while (c.moveToNext()) {
                OutboxOperation op = new OutboxOperation();
                op.id = c.getString(0);
                op.type = c.getString(1);
                op.keyRef = c.getString(2);
                op.payloadJson = c.getString(3);
                op.updatedAt = c.getLong(4);
                op.status = c.getString(5);
                op.retries = c.getInt(6);
                list.add(op);
            }
        } finally { c.close(); }
        return list;
    }
    public void markSent(String id) {
        ContentValues cv = new ContentValues();
        cv.put(C_OUTBOX_STATUS, STATUS_SENT);
        getWritableDatabase().update(T_OUTBOX, cv, C_OUTBOX_ID + "=?", new String[]{id});
    }
    public void markFailed(String id, int newRetries) {
        ContentValues cv = new ContentValues();
        cv.put(C_OUTBOX_STATUS, STATUS_FAILED);
        cv.put(C_OUTBOX_RETRIES, newRetries);
        getWritableDatabase().update(T_OUTBOX, cv, C_OUTBOX_ID + "=?", new String[]{id});
    }
    public int countPending() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + T_OUTBOX + " WHERE " + C_OUTBOX_STATUS + "=?", new String[]{STATUS_PENDING});
        try { return c.moveToFirst() ? c.getInt(0) : 0; }
        finally { c.close(); }
    }

    // ===== Reflections (novo) =====
    public static class Reflection {
        public String id;
        public String dateId;
        public String text;
        public long updatedAt;
    }
    /** Insere uma reflexão (linha separada por reflexão). */
    public String insertReflection(String dateId, String text, long updatedAt) {
        String id = UUID.randomUUID().toString();
        ContentValues cv = new ContentValues();
        cv.put(C_REFL_ID, id);
        cv.put(C_REFL_DATEID, dateId);
        cv.put(C_REFL_TEXT, text);
        cv.put(C_REFL_UPDATED, updatedAt);
        getWritableDatabase().insert(T_REFLECTIONS, null, cv);
        return id;
    }
    /** Todas as reflexões de um dia, por updatedAt ASC. */
    public List<Reflection> getReflectionsByDate(String dateId) {
        ArrayList<Reflection> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query(T_REFLECTIONS,
                new String[]{C_REFL_ID, C_REFL_DATEID, C_REFL_TEXT, C_REFL_UPDATED},
                C_REFL_DATEID + "=?", new String[]{dateId},
                null, null, C_REFL_UPDATED + " ASC");
        try {
            while (c.moveToNext()) {
                Reflection r = new Reflection();
                r.id = c.getString(0);
                r.dateId = c.getString(1);
                r.text = c.getString(2);
                r.updatedAt = c.getLong(3);
                out.add(r);
            }
        } finally { c.close(); }
        return out;
    }

    public List<String> getReflectionDaysLastNDays(int n) {
        ArrayList<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT DISTINCT " + C_REFL_DATEID + " FROM " + T_REFLECTIONS +
                        " ORDER BY " + C_REFL_DATEID + " DESC LIMIT ?", new String[]{String.valueOf(n)});
        try {
            while (c.moveToNext()) out.add(c.getString(0));
        } finally { c.close(); }
        return out;
    }
}
