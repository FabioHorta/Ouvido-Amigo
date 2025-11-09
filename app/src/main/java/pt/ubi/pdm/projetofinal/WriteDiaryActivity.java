package pt.ubi.pdm.projetofinal;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;



// Classe responsável por permitir ao utilizador escrever ou editar uma entrada no diário.
// Suporta escrita local (SQLite) e sincronização com Firebase Realtime Database.

public class WriteDiaryActivity extends AppCompatActivity {

    public static final String EXTRA_DATE_ID = "extra_date_id";
    private sqlite db;
    private String currentDateId;
    private MaterialToolbar topBar;
    private EditText etDiary;
    private DatabaseReference diaryRef;


    // Inicializa a interface e os componentes da UI.
    // Define a data da entrada (vinda do calendário ou data atual).
    // Inicializa a referência ao Firebase (se o utilizador estiver autenticado).
    // Configura o botão de guardar para salvar localmente e tentar sincronizar com a cloud.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write_diary);

        topBar = findViewById(R.id.topBar);
        etDiary = findViewById(R.id.etDiary);
        MaterialButton btnSave = findViewById(R.id.btnSave);

        db = new sqlite(this);

        // 1) Data alvo: vinda do calendário ou hoje
        String passed = getIntent().getStringExtra(EXTRA_DATE_ID);
        currentDateId = (passed != null && !passed.isEmpty())
                ? passed
                : todayId();

        //Iniciar Firebase (diário)
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            diaryRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(user.getUid())
                    .child("diary");
            diaryRef.keepSynced(true);
        }

        setupUiFor(currentDateId);

        // 2) Guardar no diário (cloud-first com fallback local/queue)
        btnSave.setOnClickListener(v -> {
            String text = etDiary.getText() == null ? "" : etDiary.getText().toString().trim();
            long now = System.currentTimeMillis();

            // escreve SEMPRE local (para histórico/offline)
            db.upsertDiary(currentDateId, text, now);

            // tenta gravar na cloud se disponível
            if (diaryRef != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("text", text);
                payload.put("createdAt", ServerValue.TIMESTAMP);
                payload.put("dateId", currentDateId);

                diaryRef.child(currentDateId).setValue(payload)
                        .addOnSuccessListener(x -> {
                            Toast.makeText(this, "Diário guardado na cloud ✅", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            // fallback: entra na fila para sincronizar depois
                            String q = "{\"dateId\":\"" + currentDateId + "\"," +
                                    "\"text\":" + quoteJson(text) + "," +
                                    "\"createdAt\":" + now + "}";
                            db.enqueue("UPSERT_DIARY", currentDateId, q, now);
                            Toast.makeText(this, "Sem ligação — guardado localmente (vai sincronizar)", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        });
            } else {
                // sem auth/ref → offline: enfileirar para sync posterior
                String q = "{\"dateId\":\"" + currentDateId + "\"," +
                        "\"text\":" + quoteJson(text) + "," +
                        "\"createdAt\":" + now + "}";
                db.enqueue("UPSERT_DIARY", currentDateId, q, now);
                Toast.makeText(this, "Sem ligação — guardado localmente (vai sincronizar)", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }
        });

        topBar.setNavigationOnClickListener(v -> finish());
    }

    // Atualiza a data da entrada se a Activity for retomada e a data atual tiver mudado.
    @Override
    protected void onResume() {
        super.onResume();
        String now = todayId();
        if (getIntent().getStringExtra(EXTRA_DATE_ID) == null && !now.equals(currentDateId)) {
            currentDateId = now;
            setupUiFor(currentDateId);
        }
    }

    // Atualiza a interface com base na data selecionada.
    // Define o título da toolbar e carrega o texto existente do diário.

    private void setupUiFor(String dateId) {
        if (topBar != null) topBar.setTitle("Diário de " + formatDateUi(dateId));
        etDiary.setText("");
        sqlite.DiaryEntry existing = db.getDiaryByDate(dateId);
        if (existing != null && existing.text != null) etDiary.setText(existing.text);
    }

    // Retorna a data atual no formato yyyy-MM-dd.
    private static String todayId() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
    // Envia uma string para ser usada em JSON.
    private static String quoteJson(String s) {
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"") + "\"";
    }
    // Converte uma data no formato yyyy-MM-dd para o formato dd/MM/yyyy para exibição na UI.
    private static String formatDateUi(String dateId) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateId);
            return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Objects.requireNonNull(d));
        } catch (Exception e) { return dateId; }
    }
}
