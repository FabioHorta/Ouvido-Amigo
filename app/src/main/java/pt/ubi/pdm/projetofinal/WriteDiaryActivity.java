package pt.ubi.pdm.projetofinal;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class WriteDiaryActivity extends AppCompatActivity {

    public static final String EXTRA_DATE_ID = "extra_date_id";

    private sqlite db;
    private String currentDateId;

    private MaterialToolbar topBar;
    private EditText etDiary;
    private MaterialButton btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write_diary);

        topBar = findViewById(R.id.topBar);
        etDiary = findViewById(R.id.etDiary);
        btnSave = findViewById(R.id.btnSave);

        db = new sqlite(this);

        // 1) Data alvo: vinda do calendário ou hoje
        String passed = getIntent().getStringExtra(EXTRA_DATE_ID);
        currentDateId = (passed != null && !passed.isEmpty())
                ? passed
                : todayId();

        setupUiFor(currentDateId);

        // 2) Guardar só no diário (NADA de reflexões aqui)
        btnSave.setOnClickListener(v -> {
            String text = etDiary.getText() == null ? "" : etDiary.getText().toString().trim();
            long now = System.currentTimeMillis();

            db.upsertDiary(currentDateId, text, now);
            String payload = "{\"dateId\":\"" + currentDateId + "\","
                    + "\"text\":" + quoteJson(text) + ","
                    + "\"createdAt\":" + now + "}";   // ← enviar createdAt (número)
            db.enqueue("UPSERT_DIARY", currentDateId, payload, now);

            Toast.makeText(this, "Diário guardado ✅", Toast.LENGTH_SHORT).show();

            // avisa o calendário para refrescar e marca RESULT_OK
            setResult(RESULT_OK);
            finish();
        });

        topBar.setNavigationOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        String now = todayId();
        if (getIntent().getStringExtra(EXTRA_DATE_ID) == null && !now.equals(currentDateId)) {
            currentDateId = now;
            setupUiFor(currentDateId);
        }
    }

    private void setupUiFor(String dateId) {
        if (topBar != null) topBar.setTitle("Diário de " + formatDateUi(dateId));
        etDiary.setText("");
        sqlite.DiaryEntry existing = db.getDiaryByDate(dateId);
        if (existing != null && existing.text != null) etDiary.setText(existing.text);
    }

    private static String todayId() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
    private static String quoteJson(String s) {
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"") + "\"";
    }
    private static String formatDateUi(String dateId) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateId);
            return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Objects.requireNonNull(d));
        } catch (Exception e) { return dateId; }
    }
}
