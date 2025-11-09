package pt.ubi.pdm.projetofinal;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.*;

    // Ecrã principal da aplicação.
    // Funcionalidades:
        // - Saudação personalizada e frase motivacional.
        // - Registo e visualização do humor diário.
        // - Sugestões de bem-estar com base no tempo de ecrã.
        // - Reflexões guiadas com sincronização local/cloud.
        // - Botões de emergência com chamadas rápidas.
        // - Sincronização com Firebase e suporte offline via SQLite.

public class MainActivity extends BaseBottomNavActivity {

    // Frases motivacionais diárias
    private final String[] frases = {
            "Acredita em ti — és mais forte do que imaginas 💪",
            "Um pequeno passo hoje pode mudar o teu amanhã 🌱",
            "Respira fundo, tudo vai ao lugar certo 🌤️",
            "És suficiente, exatamente como és 💚",
            "Não precisas ser perfeito, só persistente 🌻",
            "Sorrir é o primeiro passo para mudar o dia 😄",
            "Cada amanhecer é uma nova oportunidade ☀️"
    };

    // --- UI ---
    private MaterialButtonToggleGroup moodGroup;
    private TextView tvProgressEmojis, tvProgressAvg;
    private LinearProgressIndicator progressMood;
    private TextView tvWellnessTitle, tvWellnessBody;
    private Button btnWellnessAction;

    // --- Dados ---
    private sqlite db;
    private String todayId;
    private Boolean lastConnectionStatus = true;
    private int currentSuggestion = -1;
    private FirebaseUser user;
    private DatabaseReference moodsRef;
    private ChildEventListener moodsChildListener;
    private final TreeMap<String,Integer> cloudMoods = new TreeMap<>();
    private boolean suppressMoodSave = false;
    private boolean isOnline = false;
    private DatabaseReference reflectionsRef;


    // ============================================================
    // Ciclo de vida
    // ============================================================

    // Método chamado ao iniciar a Activity.
    //  - Configura a UI, base de dados local e componentes Firebase.
    //  - Liga listeners para sincronização de humor e reflexões.
    //  - Mostra saudação e frase motivacional.
    //  Inicia sugestões de bem-estar e monitorização da ligação à internet.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupBottomBar();
        setupEmergencyButtons();
        setupUI();
        setupDatabase();

        // =====================  init Firebase (mood) =====================
        user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            moodsRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(user.getUid())
                    .child("moods");
            moodsRef.keepSynced(true);
        }
        // =====================  init Firebase (Reflections) =====================
        if (user != null) {
            reflectionsRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(user.getUid())
                    .child("reflections");
            reflectionsRef.keepSynced(true);
        }

        setupDailyMoodLocal();
        renderLast7DaysProgress();
        setupWellnessSuggestion();
        monitorFirebaseConnection();
    }

    // ===================== Ligar/Desligar listener cloud =====================
    @Override
    protected void onStart() {
        super.onStart();
        attachMoodChildListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (moodsChildListener != null && moodsRef != null) {
            moodsRef.removeEventListener(moodsChildListener);
            moodsChildListener = null;
        }
    }
    // ===============================================================================

    @Override
    protected void onResume() {
        super.onResume();
        refreshProfileHeader();
        refreshWellness();

        // =====================Render cloud-first =====================
        renderProgressCloudFirst(); // tenta cloud; se não houver usa SQLite
    }

    @Override
    protected int currentNavItemId() {
        return R.id.nav_home;
    }

    // ============================================================
    // Configuração inicial
    // ============================================================

     // Inicializa os elementos visuais da interface:
     // - Saudação com nome do utilizador.
     // - Frase motivacional aleatória.
     // - Botão de perfil e botão para abrir o diário/reflexão.


    private void setupBottomBar() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        attachBottomNav(bottomNav);
    }

    private void setupUI() {
        tvWellnessTitle = findViewById(R.id.tvWellnessTitle);
        tvWellnessBody = findViewById(R.id.tvWellnessBody);
        btnWellnessAction = findViewById(R.id.btnWellnessAction);
        tvProgressEmojis = findViewById(R.id.tvProgressEmojis);
        progressMood = findViewById(R.id.progressMood);
        tvProgressAvg = findViewById(R.id.tvProgressAvg);
        moodGroup = findViewById(R.id.moodGroup);

        TextView tvGreeting = findViewById(R.id.tvGreeting);
        TextView tvQuote = findViewById(R.id.tvQuote);
        ShapeableImageView btnProfile = findViewById(R.id.btnProfile);

        String nome = getSharedPreferences("oa", MODE_PRIVATE).getString("nome", "Utilizador");
        tvGreeting.setText("Olá, " + nome + "!");
        tvQuote.setText(frases[new Random().nextInt(frases.length)]);
        btnProfile.setOnClickListener(v -> startActivity(new Intent(this, PerfilActivity.class)));

        if (findViewById(R.id.btnOpenDiary) != null)
            findViewById(R.id.btnOpenDiary).setOnClickListener(v -> showReflectionPopup());
    }

    private void setupDatabase() {
        db = new sqlite(this);
        todayId = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private void monitorFirebaseConnection() {
        FirebaseDatabase.getInstance().getReference(".info/connected")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot s) {
                        Boolean ok = s.getValue(Boolean.class);
                        if (ok != null) isOnline = ok; //Atualiza flag online/offline

                        if (ok != null && !ok.equals(lastConnectionStatus)) {
                            Toast.makeText(MainActivity.this,
                                    ok ? "Ligação restabelecida ✅"
                                            : "Sem ligação • a trabalhar offline",
                                    Toast.LENGTH_SHORT).show();
                            lastConnectionStatus = ok;

                            // sempre que muda, tenta render cloud-first
                            renderProgressCloudFirst();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    // ============================================================
    // Secção: Botões de emergência
    // ============================================================

    //Configura os botões de emergência
    // - Ligações rápidas para 112, SNS24 e Voz Amiga.
    // - Suporte para clique longo (cópia do número para a área de transferência).
    // - Mostra diálogo para escolher número da Voz Amiga.

    private void setupEmergencyButtons() {
        setupEmergencyButton(R.id.btnCall112, "112", "Emergência 112");
        setupEmergencyButton(R.id.btnCallSNS24, "808242424", "SNS 24");
        setupVozAmigaButton();
    }

    private void setupEmergencyButton(int id, String number, String label) {
        Button b = findViewById(id);
        if (b == null) return;
        b.setOnClickListener(v -> dial(number));
        b.setOnLongClickListener(v -> { copyToClipboard(label, number); return true; });
    }

    private void setupVozAmigaButton() {
        Button b = findViewById(R.id.btnCallVozAmiga);
        if (b == null) return;

        final String[] labels = {
                "Voz Amiga (213 544 545)",
                "Voz Amiga (912 802 669)",
                "Voz Amiga (963 524 660)"
        };
        final String[] numbers = {"213544545", "912802669", "963524660"};

        b.setOnClickListener(v -> showNumberPicker("SOS Voz Amiga", labels, numbers));
        b.setOnLongClickListener(v -> { copyToClipboard("SOS Voz Amiga", numbers[0]); return true; });
    }

    private void showNumberPicker(String title, String[] labels, String[] numbers) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setItems(labels, (d, which) -> {
                    if (which >= 0 && which < numbers.length) dial(numbers[which]);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void dial(String number) {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Não foi possível abrir o marcador.", Toast.LENGTH_LONG).show();
        }
    }

    private void copyToClipboard(String label, String number) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null)
            cm.setPrimaryClip(ClipData.newPlainText(label, number));
        Toast.makeText(this, "Número copiado: " + number, Toast.LENGTH_SHORT).show();
    }

    // ============================================================
    // Secção: Humor diário
    // ============================================================

     // Permite ao utilizador registar o humor do dia:
     // - Se offline, carrega o humor localmente.
     // - Ao selecionar um humor, guarda localmente e sincroniza com Firebase (se online).
     // - Se offline, adiciona à fila de sincronização.
    private void setupDailyMoodLocal() {
        // Se estiver offline, mostra seleção local para hoje.
        if (!isOnline) {
            sqlite.MoodLog today = db.getMoodByDate(todayId);
            if (today != null && today.mood >= 1 && today.mood <= 5)
                moodGroup.check(new int[]{R.id.mood1, R.id.mood2, R.id.mood3, R.id.mood4, R.id.mood5}[today.mood - 1]);
        }

        moodGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            if (suppressMoodSave) return;

            int mood = checkedId == R.id.mood1 ? 1 :
                    checkedId == R.id.mood2 ? 2 :
                            checkedId == R.id.mood3 ? 3 :
                                    checkedId == R.id.mood4 ? 4 : 5;

            long now = System.currentTimeMillis();

            // ===================== Cloud-first =====================
            if (isOnline && user != null && moodsRef != null) {
                Map<String, Object> m = new HashMap<>();
                m.put("value", mood);
                m.put("at", ServerValue.TIMESTAMP);
                moodsRef.child(todayId).setValue(m);

                db.upsertMood(todayId, mood, now);
            } else {
                // offline: só local + fila
                db.upsertMood(todayId, mood, now);

                String payload = "{\"dateId\":\"" + todayId + "\"," +
                        "\"mood\":" + mood + "," +
                        "\"createdAt\":" + now + "}";
                db.enqueue("UPSERT_MOOD", todayId, payload, now);
            }
            // ===============================================================

            Toast.makeText(this, "Humor guardado ✅", Toast.LENGTH_SHORT).show();
            renderProgressCloudFirst();
        });
    }

     // Mostra os últimos 7 dias de humor:
         // - Usa emojis para representar o humor diário.
         // - Calcula a média e mostra em percentagem.
         // - Usa dados locais (SQLite).
    private void renderLast7DaysProgress() {
        List<sqlite.MoodLog> last = db.getMoodLastNDays(7);
        StringBuilder line = new StringBuilder("Últimos 7 dias: ");
        int sum = 0, count = 0;

        for (int i = 0; i < 7; i++) {
            if (i < last.size()) {
                int m = last.get(i).mood;
                line.append(emojiFor(m)).append(' ');
                sum += m;
                count++;
            } else line.append("— ");
        }

        tvProgressEmojis.setText(line.toString().trim());
        int percent = count > 0 ? (int) Math.round(((sum / (double) count - 1) / 4) * 100) : 0;
        progressMood.setProgress(percent);
        tvProgressAvg.setText("Média: " + percent + "%");
    }

    private String emojiFor(int mood) {
        switch (mood) {
            case 1: return "😞";
            case 2: return "😕";
            case 3: return "😐";
            case 4: return "🙂";
            case 5: return "😄";
            default: return "—";
        }
    }

    // ===================== Listener cloud (tempo-real) =====================
     // Liga um listener ao nó de humor no Firebase:
     // - Escuta alterações em tempo real (últimos 14 dias).
     // - Atualiza cache local e interface.
     // - Se o humor de hoje mudar, atualiza o botão selecionado.
    private void attachMoodChildListener() {
        if (user == null || moodsRef == null || moodsChildListener != null) return;

        Query q = moodsRef.orderByKey().limitToLast(14);

        moodsChildListener = new ChildEventListener() {
            private void upsertFromCloud(String dateId, Integer value, Long at) {
                if (dateId == null || value == null) return;

                // 1) cache cloud
                cloudMoods.put(dateId, value);

                // 2) espelho local (para offline)
                long when = (at != null ? at : System.currentTimeMillis());
                db.upsertMood(dateId, value, when);

                // 3) se for hoje, refletir no toggle
                if (dateId.equals(todayId) && value >= 1 && value <= 5) {
                    int[] ids = {R.id.mood1, R.id.mood2, R.id.mood3, R.id.mood4, R.id.mood5};
                    int target = ids[value - 1];
                    if (moodGroup.getCheckedButtonId() != target) {
                        suppressMoodSave = true;
                        moodGroup.check(target);
                        moodGroup.postDelayed(() -> suppressMoodSave = false, 120);
                    }
                }

                renderProgressCloudFirst();
            }

            @Override public void onChildAdded(@NonNull DataSnapshot d, String prev) {
                upsertFromCloud(d.getKey(),
                        d.child("value").getValue(Integer.class),
                        d.child("at").getValue(Long.class));
            }

            @Override public void onChildChanged(@NonNull DataSnapshot d, String prev) {
                upsertFromCloud(d.getKey(),
                        d.child("value").getValue(Integer.class),
                        d.child("at").getValue(Long.class));
            }

            @Override public void onChildRemoved(@NonNull DataSnapshot d) {
                String k = d.getKey();
                if (k != null) cloudMoods.remove(k);
                renderProgressCloudFirst();
            }
            @Override
            public void onChildMoved(@NonNull DataSnapshot d, String prev) {}
            @Override
            public void onCancelled(@NonNull DatabaseError e) {}
        };

        q.addChildEventListener(moodsChildListener);
    }
    // ==============================================================================

    // =====================Render cloud-first (fallback SQLite) =====================
         // Mostra o progresso dos últimos 7 dias:
             // - Se online e com dados da cloud, usa Firebase.
             // - Caso contrário, recorre à base de dados local.
             // - Garante que o humor de hoje está refletido na UI.
    private void renderProgressCloudFirst() {
        // Se online e houver dados cloud, usa cloud
        if (isOnline && !cloudMoods.isEmpty()) {
            List<Integer> vals = new ArrayList<>();
            // últimos 7 por ordem decrescente de data
            NavigableMap<String,Integer> desc = cloudMoods.descendingMap();
            int c = 0;
            for (Map.Entry<String,Integer> e : desc.entrySet()) {
                vals.add(e.getValue());
                if (++c == 7) break;
            }
            renderFromValues(vals);

            // garante toggle de hoje
            Integer todayCloud = cloudMoods.get(todayId);
            if (todayCloud != null && todayCloud >= 1 && todayCloud <= 5) {
                int[] ids = {R.id.mood1, R.id.mood2, R.id.mood3, R.id.mood4, R.id.mood5};
                if (moodGroup.getCheckedButtonId() != ids[todayCloud - 1]) {
                    suppressMoodSave = true;
                    moodGroup.check(ids[todayCloud - 1]);
                    moodGroup.postDelayed(() -> suppressMoodSave = false, 120);
                }
            }
            return;
        }

        // Caso contrário, usa SQLite
        renderLast7DaysProgress();
        if (!isOnline) {
            sqlite.MoodLog today = db.getMoodByDate(todayId);
            if (today != null && today.mood >= 1 && today.mood <= 5) {
                int[] ids = {R.id.mood1, R.id.mood2, R.id.mood3, R.id.mood4, R.id.mood5};
                if (moodGroup.getCheckedButtonId() != ids[today.mood - 1]) {
                    suppressMoodSave = true;
                    moodGroup.check(ids[today.mood - 1]);
                    moodGroup.postDelayed(() -> suppressMoodSave = false, 120);
                }
            }
        }
    }

    private void renderFromValues(List<Integer> vals) {
        StringBuilder line = new StringBuilder("Últimos 7 dias: ");
        int sum = 0, count = 0;

        for (int i = 0; i < 7; i++) {
            if (i < vals.size()) {
                int m = vals.get(i);
                line.append(emojiFor(m)).append(' ');
                sum += m; count++;
            } else line.append("— ");
        }

        tvProgressEmojis.setText(line.toString().trim());
        int percent = count > 0 ? (int) Math.round(((sum / (double) count - 1) / 4) * 100) : 0;
        progressMood.setProgress(percent);
        tvProgressAvg.setText("Média: " + percent + "%");
    }
    // =========================================================================================

    // ============================================================
    // Secção: Reflexão guiada
    // ============================================================

     // Mostra um diálogo para o utilizador escrever uma reflexão:
         // - Limita a 50 palavras.
         // - Valida em tempo real.
         // - Guarda localmente e tenta sincronizar com Firebase.
         // - Se offline, adiciona à fila de sincronização.

    private void showReflectionPopup() {
        TextInputLayout til = new TextInputLayout(this);
        til.setHint("Escreve a tua reflexão (máx. 50 palavras)");
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setHelperText("0 / 50 palavras");

        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setMinLines(2);
        et.setMaxLines(6);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        til.addView(et);

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, 0);
        container.addView(til);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("Reflexão rápida")
                .setView(container)
                .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                .setPositiveButton("Guardar", null);

        final androidx.appcompat.app.AlertDialog alert = builder.create();
        alert.setOnShowListener(d -> {
            android.widget.Button btnSave = alert.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            btnSave.setEnabled(false);

            et.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    int words = countWords(s);
                    boolean ok = words > 0 && words <= 50;
                    btnSave.setEnabled(ok);
                    til.setError(ok ? null : "Máximo 50 palavras (" + words + ")");
                    til.setHelperText(words + " / 50 palavras");
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });

            btnSave.setOnClickListener(v -> {
                String text = et.getText() == null ? "" : et.getText().toString().trim();
                int words = countWords(text);
                if (words > 0 && words <= 50) {
                    saveReflectionToLocalAndQueue(text);
                    alert.dismiss();
                }
            });
        });
        alert.show();
    }

    private int countWords(CharSequence s) {
        if (s == null) return 0;
        String t = s.toString().trim();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    private void saveReflectionToLocalAndQueue(String text) {
        String dateId = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        long now = System.currentTimeMillis();

        // guarda SEMPRE local (histórico + offline)
        db.insertReflection(dateId, text, now);

        // cloud-first
        if (reflectionsRef != null) {
            String entryId = reflectionsRef.child(dateId).push().getKey();
            if (entryId != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("text", text);
                payload.put("createdAt", ServerValue.TIMESTAMP);
                payload.put("dateId", dateId);

                reflectionsRef.child(dateId).child(entryId).setValue(payload)
                        .addOnSuccessListener(x ->
                                Toast.makeText(this, "Reflexão guardada na cloud ✅", Toast.LENGTH_SHORT).show()
                        )
                        .addOnFailureListener(e -> {
                            String q = "{\"dateId\":\"" + dateId + "\","
                                    + "\"text\":" + quoteJson(text) + ","
                                    + "\"createdAt\":" + now + "}";
                            db.enqueue("UPSERT_REFLECTION", dateId, q, now);
                            Toast.makeText(this, "Sem ligação — guardado localmente (vai sincronizar)", Toast.LENGTH_SHORT).show();
                        });
            }
        } else {
            // sem auth/ligação: segue fila de sync
            String q = "{\"dateId\":\"" + dateId + "\","
                    + "\"text\":" + quoteJson(text) + ","
                    + "\"createdAt\":" + now + "}";
            db.enqueue("UPSERT_REFLECTION", dateId, q, now);
            Toast.makeText(this, "Sem ligação — guardado localmente (vai sincronizar)", Toast.LENGTH_SHORT).show();
        }

        SyncScheduler.kickNow(this);
    }

    private static String quoteJson(String s) {
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"") + "\"";
    }

    // ============================================================
    // Secção: Sugestões de bem-estar
    // ============================================================

     // Sugere atividades de bem-estar:
     // - Se tempo de ecrã for elevado, sugere pausa.
     // - Caso contrário, escolhe sugestão aleatória.
     // - Ações incluem hidratar, alongar, caminhar, etc.

    private void setupWellnessSuggestion() {
        tvWellnessTitle.setText("Sugestão de bem-estar");
        refreshWellness();

        btnWellnessAction.setOnClickListener(v -> {
            if (currentSuggestion == 10) {
                Intent i = new Intent(this, PauseActivity.class);
                i.putExtra(PauseActivity.EXTRA_DURATION_MS, 10 * 60 * 1000L);
                startActivity(i);
                return;
            }
            if (!hasUsageAccess() && "Ativar acesso".contentEquals(btnWellnessAction.getText())) {
                startActivity(new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS));
                return;
            }
            showSuggestionByIndex(currentSuggestion);
        });
    }

    private void showSuggestionByIndex(int index) {
        switch (index) {
            case 0:
                showSuggestionDialog("Hidrata-te", "Bebe um copo de água agora.");
                break;
            case 1:
                showSuggestionDialog("Alongamento", "Faz um breve alongamento de 2 minutos.");
                break;
            case 2:
                showSuggestionDialog("Mini-caminhada", "Caminha 100–200 passos e respira fundo.");
                break;
            case 3:
                showSuggestionDialog("Postura", "Endireita as costas e relaxa os ombros.");
                break;
            default:
                openBreathingExercise();
                break;
        }
    }

    private void showSuggestionDialog(String title, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Ok", null)
                .show();
    }

    private void refreshWellness() {
        if (!hasUsageAccess()) {
            tvWellnessBody.setText("Ativa o acesso a 'Uso de apps' para personalizar as pausas.");
            btnWellnessAction.setText("Ativar acesso");
            currentSuggestion = 4;
            return;
        }

        long screenMs = getTodayScreenTimeMillis();
        if (screenMs >= 4L * 60L * 60L * 1000L) {
            tvWellnessBody.setText("Hoje já usaste cerca de " + formatDuration(screenMs) + " de ecrã. Faz uma pausa de 5–10 minutos para recuperar foco.");
            btnWellnessAction.setText("Fazer pausa");
            currentSuggestion = 10;
            return;
        }

        // Sugestão diária pseudo-aleatória
        currentSuggestion = new Random((int) (System.currentTimeMillis() / 86_400_000L)).nextInt(5);
        String[] textos = {
                "Bebe um copo de água.",
                "Faz um pequeno alongamento.",
                "Dá uma mini-caminhada.",
                "Verifica a tua postura.",
                "Pausa de respiração de 2 minutos."
        };
        String[] botoes = {"Beber água", "Alongar", "Caminhar", "Ajustar postura", "Respirar"};
        tvWellnessBody.setText(textos[currentSuggestion]);
        btnWellnessAction.setText(botoes[currentSuggestion]);
    }

    private boolean hasUsageAccess() {
        android.app.AppOpsManager appOps =
                (android.app.AppOpsManager) getSystemService(APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                getPackageName());
        return mode == android.app.AppOpsManager.MODE_ALLOWED;
    }

    private long getTodayScreenTimeMillis() {
        try {
            android.app.usage.UsageStatsManager usm =
                    (android.app.usage.UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            Map<String, android.app.usage.UsageStats> map =
                    usm.queryAndAggregateUsageStats(cal.getTimeInMillis(), System.currentTimeMillis());

            long total = 0L;
            if (map != null) {
                for (Map.Entry<String, android.app.usage.UsageStats> entry : map.entrySet()) {
                    String pkg = entry.getKey();
                    android.app.usage.UsageStats u = entry.getValue();
                    if (u == null || pkg == null) continue;
                    if (pkg.equals(getPackageName()) ||
                            pkg.startsWith("com.android") ||
                            pkg.startsWith("com.google.android") ||
                            pkg.contains("launcher") ||
                            pkg.contains("systemui") ||
                            pkg.contains("inputmethod") ||
                            pkg.contains("settings") ||
                            pkg.contains("packageinstaller") ||
                            pkg.contains("wellbeing")) continue;
                    if (u.getTotalTimeInForeground() > 20_000)
                        total += u.getTotalTimeInForeground();
                }
            }
            return (long) (total * 0.4); // correção empírica
        } catch (SecurityException se) {
            return 0L;
        }
    }

    private String formatDuration(long ms) {
        long minutes = ms / 60000L;
        return String.format(Locale.getDefault(), "%dh %02dmin", minutes / 60, minutes % 60);
    }

    private void openBreathingExercise() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Pausa de respiração")
                .setMessage("Inspira 4s • segura 4s • expira 6s.\nRepete calmamente por 2 minutos.")
                .setPositiveButton("Ok", null)
                .show();
    }

    // ============================================================
    // Secção: Firebase (perfil + sincronização)
    // ============================================================


     // Atualiza o cabeçalho com nome e avatar do utilizador:
     // - Usa dados locais (SharedPreferences).
     // - Se disponível, atualiza com dados do Firebase (nome e imagem).
     // - Guarda avatar localmente para uso offline.


    private void refreshProfileHeader() {
        TextView tvGreeting = findViewById(R.id.tvGreeting);
        ShapeableImageView btnProfile = findViewById(R.id.btnProfile);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();

        // Dados locais (nome e avatar)
        android.content.SharedPreferences sp = getSharedPreferences("oa_" + uid, MODE_PRIVATE);
        String nomeSP = sp.getString("nome", null);
        if (nomeSP != null && !nomeSP.isEmpty()) tvGreeting.setText("Olá, " + nomeSP + "!");

        loadLocalAvatar(btnProfile, uid);

        // Dados Firebase (nome + imagem Base64)
        DatabaseReference profileRef = FirebaseDatabase.getInstance()
                .getReference("users").child(uid).child("profile");

        profileRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                String displayName = snap.child("displayName").getValue(String.class);
                if (displayName != null && !displayName.isEmpty()) {
                    tvGreeting.setText("Olá, " + displayName + "!");
                    sp.edit().putString("nome", displayName).apply();
                }
                String b64 = snap.child("photoB64").getValue(String.class);
                if (b64 != null && !b64.isEmpty()) saveAndSetAvatar(btnProfile, b64, uid);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadLocalAvatar(ShapeableImageView img, String uid) {
        try {
            java.io.File avatarFile = new java.io.File(getFilesDir(), "avatar_" + uid + ".jpg");
            if (avatarFile.exists()) {
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(avatarFile.getAbsolutePath());
                if (bmp != null) {
                    img.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    img.setImageBitmap(bmp);
                }
            }
        } catch (Exception ignored) {}
    }

    private void saveAndSetAvatar(ShapeableImageView img, String b64, String uid) {
        android.graphics.Bitmap bmp = base64ToBitmap(b64);
        if (bmp == null) return;
        img.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        img.setImageBitmap(bmp);
        try (java.io.FileOutputStream out =
                     new java.io.FileOutputStream(new java.io.File(getFilesDir(), "avatar_" + uid + ".jpg"))) {
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out);
        } catch (Exception ignored) {}
    }

    private android.graphics.Bitmap base64ToBitmap(String b64) {
        try {
            byte[] data = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
            return android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception e) {
            return null;
        }
    }
}
