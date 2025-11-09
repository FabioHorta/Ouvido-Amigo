package pt.ubi.pdm.projetofinal;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;


// Classe responsável por gerir o ecrã do diário e reflexões do utilizador.
// Permite escrever, visualizar, exportar e sincronizar entradas do diário e reflexões com Firebase.
public class DiaryActivity extends BaseBottomNavActivity {

    private DatabaseReference reflectionsRef;
    private FirebaseUser user;
    private ChildEventListener reflectionsListener;
    private sqlite db;
    private final Set<String> daysWithEntry = new HashSet<>();
    private ActivityResultLauncher<Intent> editDiaryLauncher;
    private DatabaseReference diaryRef;
    private ChildEventListener diaryListener;

    // Inicializa a interface, base de dados local e referências Firebase.
    // Liga os botões da UI às ações: escrever, abrir calendário, exportar PDF, etc.

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diary);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        attachBottomNav(bottomNav);
        user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }

        diaryRef = FirebaseDatabase.getInstance()
                .getReference("users").child(user.getUid()).child("diary");
        diaryRef.keepSynced(true);

        reflectionsRef = FirebaseDatabase.getInstance()
                .getReference("users").child(user.getUid()).child("reflections");
        reflectionsRef.keepSynced(true);

        db = new sqlite(this);
        refreshLocalDays();
        attachReflectionsListener();
        editDiaryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                res -> {
                    if (res.getResultCode() == RESULT_OK) {
                        refreshLocalDays();
                    }
                });

        findViewById(R.id.btnWrite).setOnClickListener(v ->
                editDiaryLauncher.launch(new Intent(this, WriteDiaryActivity.class))
        );
        findViewById(R.id.btnCalendarDiary).setOnClickListener(v -> openCalendarForDiary());
        findViewById(R.id.btnCalendarReflections).setOnClickListener(v -> openCalendarForReflections());
        findViewById(R.id.btnExportPdf).setOnClickListener(v -> showExportDialog());
    }

    // Liga e desliga o listener do diário no Firebase para sincronização em tempo real.
    @Override protected void onStart() {
        super.onStart();
        attachDiaryListener();
    }

    @Override protected void onStop() {
        super.onStop();
        if (diaryListener != null && diaryRef != null) {
            diaryRef.removeEventListener(diaryListener);
            diaryListener = null;
        }
    }

    // ============================================================
    // Secção: Diário
    // ============================================================

    // Abre um calendário com os dias que têm entradas no diário local.
    // Permite visualizar ou editar entradas existentes.

    private void openCalendarForDiary() {
        // Atualiza dias válidos (com texto) antes de abrir o calendário
        refreshLocalDays();

        // DatePicker com validação de dias permitidos (se houver)
        MaterialDatePicker<Long> dp;
        if (daysWithEntry.isEmpty()) {
            dp = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Escolhe o dia (diário)")
                    .build();
        } else {
            CalendarConstraints.Builder cc = new CalendarConstraints.Builder()
                    .setValidator(new AllowedDaysValidator(daysWithEntry));
            dp = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Escolhe o dia (diário)")
                    .setCalendarConstraints(cc.build())
                    .build();
        }

        // Ao escolher uma data, mostra o texto do diário desse dia
        dp.addOnPositiveButtonClickListener(ms -> {
            String dateId = dateIdFromMillis(ms);
            sqlite.DiaryEntry e = db.getDiaryByDate(dateId);

            if (e == null || e.text == null || e.text.trim().isEmpty()) {
                new MaterialAlertDialogBuilder(DiaryActivity.this)
                        .setTitle(formatDateUi(dateId))
                        .setMessage("Não escreveste no diário nesse dia.")
                        .setPositiveButton("Escrever", (d, w) -> {
                            Intent i = new Intent(DiaryActivity.this, WriteDiaryActivity.class)
                                    .putExtra(WriteDiaryActivity.EXTRA_DATE_ID, dateId);
                            editDiaryLauncher.launch(i);
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                return;
            }

            new MaterialAlertDialogBuilder(DiaryActivity.this)
                    .setTitle("Diário • " + formatDateUi(dateId))
                    .setMessage(e.text)
                    .setPositiveButton("Editar", (d, w) -> {
                        Intent i = new Intent(DiaryActivity.this, WriteDiaryActivity.class)
                                .putExtra(WriteDiaryActivity.EXTRA_DATE_ID, dateId);
                        editDiaryLauncher.launch(i);
                    })
                    .setNegativeButton("Fechar", null)
                    .show();
        });

        dp.show(getSupportFragmentManager(), "dp_diary");

        if (!daysWithEntry.isEmpty()) {
            Toast.makeText(this, "Dias com conteúdo estão ativos; os restantes ficam cinzentos.", Toast.LENGTH_SHORT).show();
        }
    }

    // Atualiza a lista de dias com entradas no diário local (últimos 365 dias).
    private void refreshLocalDays() {
        daysWithEntry.clear();
        List<sqlite.DiaryEntry> lastYear = db.getDiaryLastNDays(365);
        for (sqlite.DiaryEntry e : lastYear) {
            if (e.text != null && !e.text.trim().isEmpty()) {
                daysWithEntry.add(e.dateId); // "yyyy-MM-dd"
            }
        }
    }

    // ============================================================
    // Secção: Reflexões
    // ============================================================

    // Abre um calendário com os dias que têm reflexões (locais).
    // Mostra as reflexões do dia selecionado.

    private void openCalendarForReflections() {
        // Dias com reflexões
        HashSet<String> daysWithReflections = new HashSet<>(db.getReflectionDaysLastNDays(365));

        MaterialDatePicker<Long> dp;
        if (daysWithReflections.isEmpty()) {
            dp = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Escolhe o dia (reflexões)")
                    .build();
        } else {
            CalendarConstraints.Builder cc =
                    new CalendarConstraints.Builder()
                            .setValidator(new AllowedDaysValidator(daysWithReflections));
            dp = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Escolhe o dia (reflexões)")
                    .setCalendarConstraints(cc.build())
                    .build();
        }

        // Mostra a lista de reflexões locais do dia
        dp.addOnPositiveButtonClickListener(ms -> {
            String dateId = dateIdFromMillis(ms);
            List<sqlite.Reflection> list = db.getReflectionsByDate(dateId);

            if (list.isEmpty()) {
                new MaterialAlertDialogBuilder(DiaryActivity.this)
                        .setTitle(formatDateUi(dateId))
                        .setMessage("Não tens reflexões nesse dia.")
                        .setPositiveButton("Ok", null).show();
                return;
            }

            StringBuilder sb = new StringBuilder();
            java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
            for (sqlite.Reflection r : list) {
                if (r.text == null) continue;
                String t = r.text.trim();
                if (t.isEmpty() || !seen.add(t)) continue; // ignora repetidos
                if (sb.length() > 0) sb.append("\n\n");
                sb.append("• ").append(t);
            }

            new MaterialAlertDialogBuilder(DiaryActivity.this)
                    .setTitle("Reflexões • " + formatDateUi(dateId))
                    .setMessage(sb.toString())
                    .setPositiveButton("Ok", null)
                    .show();
        });

        dp.show(getSupportFragmentManager(), "dp_reflections");

        if (!daysWithReflections.isEmpty()) {
            Toast.makeText(this, "Dias com reflexões estão ativos;os restantes ficam cinzentos.", Toast.LENGTH_SHORT).show();
        }
    }

    // ============================================================
    // Secção: Exportar PDF
    // ============================================================

    // Mostra opções para exportar o diário e reflexões para PDF (tudo ou intervalo de datas).
    private void showExportDialog() {
        String[] options = {"Todas as entradas", "Entre datas"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Exportar diário para PDF")
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        exportPdf(/*all=*/true, 0L, Long.MAX_VALUE);
                    } else {
                        pickDateRangeAndExport();
                    }
                })
                .show();
    }

    // Permite ao utilizador escolher um intervalo de datas para exportar o diário
    private void pickDateRangeAndExport() {
        final Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, day) -> {
            Calendar from = Calendar.getInstance();
            from.set(y, m, day, 0, 0, 0); from.set(Calendar.MILLISECOND, 0);

            new DatePickerDialog(this, (view2, y2, m2, day2) -> {
                Calendar to = Calendar.getInstance();
                to.set(y2, m2, day2, 23, 59, 59); to.set(Calendar.MILLISECOND, 999);
                exportPdf(false, from.getTimeInMillis(), to.getTimeInMillis());
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();

        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }


    // Junta dados locais e do Firebase (diário e reflexões).
    // Gera um ficheiro PDF com as entradas e abre/partilha o documento.

    private void exportPdf(boolean all, long fromMs, long toMs) {
        // 1) Reflexões LOCAIS → mapeia por dia
        Map<String, String> reflTextLocal = new HashMap<>();
        List<String> reflDays = db.getReflectionDaysLastNDays(365);
        for (String day : reflDays) {
            List<sqlite.Reflection> list = db.getReflectionsByDate(day);
            if (!list.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (sqlite.Reflection r : list) {
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append("• ").append(r.text);
                }
                if (sb.length() > 0) reflTextLocal.put(day, sb.toString());
            }
        }

        // 2) Pede reflexões REMOTAS (Firebase), junta com as LOCAIS e só depois gera o PDF
        reflectionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot reflSnap) {
                // Começa com o que existe localmente; Firebase acrescenta/atualiza
                Map<String, String> reflText = new HashMap<>(reflTextLocal);

                for (DataSnapshot day : reflSnap.getChildren()) {
                    String dayKey = day.getKey();
                    if (dayKey == null) continue;
                    StringBuilder sb = new StringBuilder();
                    for (DataSnapshot e : day.getChildren()) {
                        String t = e.child("text").getValue(String.class);
                        if (t != null && !t.trim().isEmpty()) {
                            if (sb.length() > 0) sb.append("\n\n");
                            sb.append("• ").append(t.trim());
                        }
                    }
                    if (sb.length() > 0) {
                        reflText.put(dayKey, sb.toString());
                    }
                }

                // 3) Diário LOCAL
                Map<String, String> diaryText = new HashMap<>();
                List<sqlite.DiaryEntry> allDiary = db.getDiaryLastNDays(365);
                for (sqlite.DiaryEntry e : allDiary) {
                    if (e.text != null && !e.text.trim().isEmpty()) {
                        diaryText.put(e.dateId, e.text.trim());
                    }
                }

                // 4) União de todas as datas presentes em diário/reflexões
                TreeSet<String> allDates = new TreeSet<>();
                allDates.addAll(diaryText.keySet());
                allDates.addAll(reflText.keySet());

                // 5) Filtra pelo intervalo, se necessário
                List<String> dates = new ArrayList<>();
                for (String d : allDates) {
                    boolean hasDiary = diaryText.containsKey(d);
                    boolean hasRefl  = reflText.containsKey(d);
                    if (!hasDiary && !hasRefl) continue;
                    if (!all) {
                        long ms = dateIdToMsSafe(d);
                        if (ms < fromMs || ms > toMs) continue;
                    }
                    dates.add(d);
                }

                if (dates.isEmpty()) {
                    Toast.makeText(DiaryActivity.this, "Sem entradas no intervalo.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 6) Gera e abre/partilha o PDF
                try {
                    File dir = new File(getExternalFilesDir("Exports"), "");
                    if (!dir.exists()) dir.mkdirs();
                    String fname = "diario_" +
                            new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date()) +
                            ".pdf";
                    File out = new File(dir, fname);

                    createPdf(out, new TreeSet<>(dates), diaryText, reflText);

                    Uri uri = FileProvider.getUriForFile(DiaryActivity.this,
                            getPackageName() + ".fileprovider", out);

                    Intent view = new Intent(Intent.ACTION_VIEW);
                    view.setDataAndType(uri, "application/pdf");
                    view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(view, "Abrir/partilhar PDF"));

                } catch (Exception e) {
                    Toast.makeText(DiaryActivity.this, "Erro a exportar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }


    //Liga um listener ao nó do diário no Firebase Realtime Database.
    // - Garante que o listener só é criado uma vez.
    // - Escuta adições e alterações de entradas no diário (últimos 365 dias).
    // - Para cada entrada nova ou modificada:
        // - Obtém a data (chave) e os dados do texto e data de criação.
        // - Verifica se o texto não está vazio.
        // - Compara com a entrada local (SQLite) para evitar duplicações
        // - Se for diferente ou nova, insere/atualiza a entrada local.
        // - Atualiza a interface com os dados locais.
    private void attachDiaryListener() {
        if (diaryRef == null || diaryListener != null) return;

        diaryListener = new ChildEventListener() {
            private void applyDay(DataSnapshot d) {
                String dateId = d.getKey(); // yyyy-MM-dd
                if (dateId == null) return;

                String text = d.child("text").getValue(String.class);
                Long createdAt = d.child("createdAt").getValue(Long.class);
                if (text == null || text.trim().isEmpty()) return;

                long when = (createdAt != null ? createdAt : System.currentTimeMillis());

                java.util.List<sqlite.DiaryEntry> existing = db.getDiaryByDate(dateId) != null
                        ? java.util.Collections.singletonList(db.getDiaryByDate(dateId))
                        : java.util.Collections.emptyList();
                boolean same = false;
                for (sqlite.DiaryEntry e : existing) {
                    if (e != null && e.text != null && e.text.trim().equals(text.trim())) { same = true; break; }
                }
                if (!same) {
                    db.upsertDiary(dateId, text.trim(), when);
                }
                refreshLocalDays();
            }

            @Override public void onChildAdded(@NonNull DataSnapshot snapshot, String prev) { applyDay(snapshot); }
            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, String prev) { applyDay(snapshot); }
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, String prev) { }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        };

        diaryRef.orderByKey().limitToLast(365).addChildEventListener(diaryListener);
    }


    //Liga um listener ao nó de reflexões no Firebase Realtime Database.
    // - Garante que o listener só é criado uma vez.
    // - Escuta alterações nos dados de reflexões (últimos 365 dias).
    // - Para cada dia com reflexões:
        // - Percorre todas as entradas (reflexões) desse dia.
        // - Verifica se o texto é válido (não nulo nem vazio).
        // - Compara com as reflexões já existentes na base de dados local (SQLite).
        // - Se for nova, insere a reflexão localmente com a data de criação.


    private void attachReflectionsListener() {
        if (reflectionsRef == null || reflectionsListener != null) return;

        reflectionsListener = new ChildEventListener() {
            private void upsertDay(DataSnapshot daySnap) {
                String dayKey = daySnap.getKey(); // yyyy-MM-dd
                if (dayKey == null)
                    return;
                List<sqlite.Reflection> existing = db.getReflectionsByDate(dayKey);

                for (DataSnapshot e : daySnap.getChildren()) {
                    String text = e.child("text").getValue(String.class);
                    Long createdAt = e.child("createdAt").getValue(Long.class);
                    if (text == null) continue;
                    String t = text.trim();
                    if (t.isEmpty()) continue;
                    long when = (createdAt != null ? createdAt : System.currentTimeMillis());

                    boolean already = false;
                    if (existing != null) {
                        for (sqlite.Reflection r : existing) {
                            if (r.text != null && r.text.trim().equals(t)) {
                                already = true;
                                break;
                            }
                        }
                    }
                    if (!already) {
                        db.insertReflection(dayKey, t, when);
                    }
                }
            }
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                upsertDay(snapshot);
            }
            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {
                upsertDay(snapshot);
            }
            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
            }
            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName)
                {}
            @Override
            public void onCancelled(@NonNull DatabaseError error)
                {}
        };


        reflectionsRef.orderByKey().limitToLast(365).addChildEventListener(reflectionsListener);
    }

    // ============================================================
    // Secção: PDF Helpers
    // ============================================================

    // Cria o documento PDF com as entradas do diário e reflexões formatadas.
    private void createPdf(File file, SortedSet<String> dates,
                           Map<String,String> diaryText, Map<String,String> reflText) throws Exception {

        PdfDocument doc = new PdfDocument();

        Paint title = new Paint();
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        title.setTextSize(18f);

        Paint h2 = new Paint();
        h2.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        h2.setTextSize(14f);

        Paint body = new Paint();
        body.setTextSize(12f);

        final int pageW = 595;   // ~A4 a 72dpi
        final int pageH = 842;
        final int margin = 40;
        final int lineH = 18;
        final int maxWidth = pageW - margin * 2;

        int pageNum = 1;

        // Página de capa
        {
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, pageNum++).create());
            Canvas canvas = page.getCanvas();
            int y = margin + 40;
            canvas.drawText("Exportação do Diário", margin, y, title);
            y += lineH * 2;
            canvas.drawText("Gerado em: " +
                            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date()),
                    margin, y, body);
            doc.finishPage(page);
        }

        // Páginas com conteúdo
        PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, pageNum++).create());
        Canvas canvas = page.getCanvas();
        int y = margin;

        for (String d : dates) {
            // Quebra de página defensiva
            if (y > pageH - margin - lineH * 6) {
                doc.finishPage(page);
                page = doc.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, pageNum++).create());
                canvas = page.getCanvas();
                y = margin;
            }

            // Título com data
            canvas.drawText(formatDateUi(d), margin, y, title);
            y += lineH + 6;

            // Secção: Texto do diário
            if (y > pageH - margin - lineH * 4) {
                doc.finishPage(page);
                page = doc.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, pageNum++).create());
                canvas = page.getCanvas();
                y = margin;
            }
            canvas.drawText("Texto", margin, y, h2);
            y += lineH;
            y = drawParagraph(canvas, body, diaryText.get(d), margin, y, maxWidth, lineH);
            y += lineH * 4;

            // Secção: Reflexões
            if (y > pageH - margin - lineH * 3) {
                doc.finishPage(page);
                page = doc.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, pageNum++).create());
                canvas = page.getCanvas();
                y = margin;
            }
            canvas.drawText("Reflexões", margin, y, h2);
            y += lineH;
            y = drawParagraph(canvas, body, reflText.get(d), margin, y, maxWidth, lineH);
            y += lineH * 4;
        }

        doc.finishPage(page);

        // Guarda em disco
        try (FileOutputStream fos = new FileOutputStream(file)) {
            doc.writeTo(fos);
        }
        doc.close();
    }

    // ============================================================
    // Secção: Helpers
    // ============================================================

    // Helpers para desenhar texto formatado no PDF, com quebras de linha e margens.
    private int drawParagraph(Canvas canvas, Paint paint, String text, int x, int y, int maxWidth, int lineH) {
        if (text == null || text.trim().isEmpty()) return y;
        String[] paragraphs = text.split("\\n");
        for (String p : paragraphs) {
            y = drawWrapped(canvas, paint, p, x, y, maxWidth, lineH);
            y += 6;
        }
        return y;
    }
    private int drawWrapped(Canvas canvas, Paint paint, String text, int x, int y, int maxWidth, int lineH) {
        if (text == null) return y;
        String[] words = text.trim().isEmpty() ? new String[0] : text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String cand = (line.length() == 0) ? w : line + " " + w;
            if (paint.measureText(cand) > maxWidth) {
                canvas.drawText(line.toString(), x, y + lineH, paint);
                y += lineH;
                line = new StringBuilder(w);
            } else {
                line = new StringBuilder(cand);
            }
        }
        if (line.length() > 0) {
            canvas.drawText(line.toString(), x, y + lineH, paint);
            y += lineH;
        }
        return y;
    }

    // ============================================================
    // Secção: Misc
    // ============================================================

    @Override
    protected int currentNavItemId() {
        return R.id.nav_diary;
    }

    // Valida as datas para o calendário (MaterialDatePicker).
    // Permite selecionar apenas os dias com conteúdo.

    public static class AllowedDaysValidator implements CalendarConstraints.DateValidator, Parcelable {
        private final HashSet<String> allowedDateIds; // "yyyy-MM-dd"
        public AllowedDaysValidator(Set<String> allowed) { this.allowedDateIds = new HashSet<>(allowed); }
        protected AllowedDaysValidator(Parcel in) {
            List<String> list = new ArrayList<>();
            in.readStringList(list);
            this.allowedDateIds = new HashSet<>(list);
        }
        @Override public boolean isValid(long date) {
            String id = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(date));
            return allowedDateIds.contains(id);
        }
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeStringList(new ArrayList<>(allowedDateIds));
        }
        public static final Creator<AllowedDaysValidator> CREATOR = new Creator<AllowedDaysValidator>() {
            @Override public AllowedDaysValidator createFromParcel(Parcel in) { return new AllowedDaysValidator(in); }
            @Override public AllowedDaysValidator[] newArray(int size) { return new AllowedDaysValidator[size]; }
        };
    }

    // ============================================================
    // Secção: Funções Auxiliares
    // ============================================================
    private String dateIdFromMillis(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(ms));
    }
    private String formatDateUi(String dateId) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateId);
            return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Objects.requireNonNull(d));
        } catch (Exception e) { return dateId; }
    }
    private long dateIdToMsSafe(String dateId) {
        if (dateId == null) return 0L;
        try { return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateId).getTime(); }
        catch (Exception e) { return 0L; }
    }
}
