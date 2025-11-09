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



// Classe responsável por executar a sincronização de dados locais com o Firebase.
// Utiliza WorkManager para processar operações pendentes da base de dados local SQLITE.
public class SyncWorker extends Worker {

    private sqlite db;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        db = new sqlite(context.getApplicationContext());
    }



    // Método principal chamado pelo WorkManager.
    // - Verifica se o utilizador está autenticado.
    // - Obtém até 20 operações pendentes da base de dados local.
    // - Executa cada operação (diário, humor ou reflexão) conforme o tipo.
    // - Marca como enviada ou falhada dependendo do resultado.
    // - Retorna sucesso ou retry conforme o estado da sincronização.

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


    // Envia uma entrada de diário para o Firebase Realtime Database.
    // - Extrai os dados do JSON e envia para o nó /users/{uid}/diary/{dateId}.

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


    // Envia um registo de humor para o Firebase Realtime Database.
    // - Extrai os dados do JSON e envia para o nó /users/{uid}/moods/{dateId}.

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

    // Envia uma reflexão para o Firebase Realtime Database.
    // - Extrai os dados do JSON e envia para o nó /users/{uid}/reflections/{dateId}/{autoId}.
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
