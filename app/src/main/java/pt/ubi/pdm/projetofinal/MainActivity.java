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

/**
 * Ecrã principal da aplicação.
 * Mostra:
 *  - Saudação e frase motivacional
 *  - Humor diário e progresso semanal
 *  - Sugestões de bem-estar
 *  - Reflexões guiadas
 * Garante funcionamento offline (SQLite) e sincronização com Firebase.
 */
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

    // ============================================================
    // Ciclo de vida
    // ============================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupBottomBar();
        setupEmergencyButtons();
        setupUI();
        setupDatabase();

        setupDailyMoodLocal();
        renderLast7DaysProgress();
        setupWellnessSuggestion();
        monitorFirebaseConnection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshProfileHeader();
        refreshWellness();
        renderLast7DaysProgress();
    }

    @Override
    protected int currentNavItemId() {
        return R.id.nav_home;
    }

    // ============================================================
    // Configuração inicial
    // ============================================================
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
                        if (ok != null && !ok.equals(lastConnectionStatus)) {
                            Toast.makeText(MainActivity.this,
                                    ok ? "Ligação restabelecida ✅"
                                            : "Sem ligação • a trabalhar offline",
                                    Toast.LENGTH_SHORT).show();
                            lastConnectionStatus = ok;
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    // ============================================================
    // Secção: Botões de emergência
    // ============================================================
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
    private void setupDailyMoodLocal() {
        sqlite.MoodLog today = db.getMoodByDate(todayId);
        if (today != null && today.mood >= 1 && today.mood <= 5)
            moodGroup.check(new int[]{R.id.mood1, R.id.mood2, R.id.mood3, R.id.mood4, R.id.mood5}[today.mood - 1]);

        moodGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            int mood = checkedId == R.id.mood1 ? 1 :
                    checkedId == R.id.mood2 ? 2 :
                            checkedId == R.id.mood3 ? 3 :
                                    checkedId == R.id.mood4 ? 4 : 5;

            long now = System.currentTimeMillis();
            db.upsertMood(todayId, mood, now);

            String payload = "{\"dateId\":\"" + todayId + "\"," +
                    "\"mood\":" + mood + "," +
                    "\"createdAt\":" + now + "}";
            db.enqueue("UPSERT_MOOD", todayId, payload, now);

            Toast.makeText(this, "Humor guardado ✅", Toast.LENGTH_SHORT).show();
            renderLast7DaysProgress();
        });
    }

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

    // ============================================================
    // Secção: Reflexão guiada
    // ============================================================
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

        db.insertReflection(dateId, text, now);
        String payload = "{\"dateId\":\"" + dateId + "\"," +
                "\"text\":" + quoteJson(text) + "," +
                "\"createdAt\":" + now + "}";
        db.enqueue("UPSERT_REFLECTION", dateId, payload, now);

        Toast.makeText(this, "Reflexão guardada ✅", Toast.LENGTH_SHORT).show();
        SyncScheduler.kickNow(this);
    }

    private static String quoteJson(String s) {
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"") + "\"";
    }

    // ============================================================
    // Secção: Sugestões de bem-estar
    // ============================================================
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
            tvWellnessBody.setText("Hoje já usaste cerca de " + formatDuration(screenMs) +
                    " de ecrã. Faz uma pausa de 5–10 minutos para recuperar foco.");
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
