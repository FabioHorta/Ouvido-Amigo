package pt.ubi.pdm.projetofinal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncWorker extends Worker {

    private sqlite db;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        db = new sqlite(context.getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return Result.retry();
        }
        String uid = user.getUid();
        FirebaseDatabase rtdb = FirebaseDatabase.getInstance();

        try {
            List<sqlite.OutboxOperation> pending = db.getPending(20);
            if (pending.isEmpty()) {
                return Result.success();
            }

            for (sqlite.OutboxOperation op : pending) {
                boolean ok;
                switch (op.type) {
                    case "UPSERT_DIARY":
                        ok = upsertDiary(rtdb, uid, op.payloadJson);
                        break;
                    case "UPSERT_MOOD":
                        ok = upsertMood(rtdb, uid, op.payloadJson);
                        break;
                    case "UPSERT_REFLECTION":
                        ok = upsertReflection(rtdb, uid, op.payloadJson);
                        break;
                    default:
                        db.markFailed(op.id, op.retries + 1);
                        continue;
                }

                if (ok) {
                    db.markSent(op.id);
                } else {
                    db.markFailed(op.id, op.retries + 1);
                    return Result.retry();
                }
            }

            return Result.success();

        } catch (Exception e) {
            return Result.retry();
        }
    }

    private boolean upsertDiary(FirebaseDatabase rtdb, String uid, String payloadJson) {
        try {
            JSONObject j = new JSONObject(payloadJson);
            String dateId    = j.getString("dateId");
            String text      = j.optString("text", "");
            long createdAt   = j.optLong("createdAt", System.currentTimeMillis());

            Map<String, Object> data = new HashMap<>();
            data.put("dateId", dateId);
            data.put("text", text);
            data.put("createdAt", createdAt); // ← regras exigem createdAt

            rtdb.getReference("users").child(uid).child("diary").child(dateId)
                    .setValue(data)
                    .getResult();

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean upsertMood(FirebaseDatabase rtdb, String uid, String payloadJson) {
        try {
            JSONObject j = new JSONObject(payloadJson);
            String dateId  = j.getString("dateId");
            int mood       = j.getInt("mood");
            long createdAt = j.optLong("createdAt", System.currentTimeMillis());

            Map<String, Object> data = new HashMap<>();
            data.put("dateId", dateId);
            data.put("mood", mood);
            data.put("createdAt", createdAt); // ← regras exigem createdAt

            rtdb.getReference("users").child(uid).child("moods").child(dateId)
                    .setValue(data)
                    .getResult();

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean upsertReflection(FirebaseDatabase rtdb, String uid, String payloadJson) {
        try {
            JSONObject j = new JSONObject(payloadJson);
            String dateId    = j.getString("dateId");
            String text      = j.optString("text", "");
            long createdAt   = j.optLong("createdAt", System.currentTimeMillis());

            Map<String, Object> data = new HashMap<>();
            data.put("dateId", dateId);
            data.put("text", text);
            data.put("createdAt", createdAt); // ← regras exigem createdAt

            rtdb.getReference("users").child(uid).child("reflections").child(dateId)
                    .push().setValue(data)
                    .getResult();

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
