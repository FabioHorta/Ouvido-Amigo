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


public class DiaryActivity extends BaseBottomNavActivity {

    // Referência às reflexões do utilizador no Firebase
    private DatabaseReference reflectionsRef;
    private FirebaseUser user;

    private sqlite db;
    private final Set<String> daysWithEntry = new HashSet<>();

    private ActivityResultLauncher<Intent> editDiaryLauncher;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diary);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        attachBottomNav(bottomNav);

        // Autenticação
        user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }

        // Nó de reflexões do utilizador
        reflectionsRef = FirebaseDatabase.getInstance()
                .getReference("users").child(user.getUid()).child("reflections");

        // Base de dados local (SQLite)
        db = new sqlite(this);
        refreshLocalDays(); // pré-carrega dias do diário com conteúdo

        // Resultado do editor de diário → se gravou, atualiza dias locais
        editDiaryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                res -> {
                    if (res.getResultCode() == RESULT_OK) {
                        refreshLocalDays();
                    }
                });

        // Ações dos botões
        findViewById(R.id.btnWrite).setOnClickListener(v ->
                editDiaryLauncher.launch(new Intent(this, WriteDiaryActivity.class))
        );
        findViewById(R.id.btnCalendarDiary).setOnClickListener(v -> openCalendarForDiary());
        findViewById(R.id.btnCalendarReflections).setOnClickListener(v -> openCalendarForReflections());
        findViewById(R.id.btnExportPdf).setOnClickListener(v -> showExportDialog());
    }

    /* -------------------- Diário: calendário LOCAL (SQLite) -------------------- */

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
            Toast.makeText(this, "Dias com conteúdo estão ativos; restantes ficam cinzentos.", Toast.LENGTH_SHORT).show();
        }
    }

    // Recalcula o conjunto de dias do último ano com texto no diário local
    private void refreshLocalDays() {
        daysWithEntry.clear();
        List<sqlite.DiaryEntry> lastYear = db.getDiaryLastNDays(365);
        for (sqlite.DiaryEntry e : lastYear) {
            if (e.text != null && !e.text.trim().isEmpty()) {
                daysWithEntry.add(e.dateId); // "yyyy-MM-dd"
            }
        }
    }

    /* -------------------- Reflexões: calendário (Firebase + local) -------------------- */

    private void openCalendarForReflections() {
        // Dias com reflexões (partimos dos dados locais; o utilizador pode estar offline)
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

        // Mostra a lista de reflexões locais do dia; (Firebase complementa no export PDF)
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
            for (sqlite.Reflection r : list) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append("• ").append(r.text);
            }

            new MaterialAlertDialogBuilder(DiaryActivity.this)
                    .setTitle("Reflexões • " + formatDateUi(dateId))
                    .setMessage(sb.toString())
                    .setPositiveButton("Ok", null)
                    .show();
        });

        dp.show(getSupportFragmentManager(), "dp_reflections");

        if (!daysWithReflections.isEmpty()) {
            Toast.makeText(this, "Dias com reflexões estão ativos; restantes ficam cinzentos.", Toast.LENGTH_SHORT).show();
        }
    }

    /* -------------------- Exportar PDF (diário local + reflexões) -------------------- */

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

    // Pede intervalo de datas (início/fim) com dois DatePickers e lança export
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

    /**
     * Exporta PDF com:
     * - Diário
     * - Reflexões
     */
    private void exportPdf(boolean all, long fromMs, long toMs) {
        // 1) Reflexões LOCAIS → mapear por dia (ex.: últimos ~10 anos)
        Map<String, String> reflTextLocal = new HashMap<>();
        List<String> reflDays = db.getReflectionDaysLastNDays(3650);
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

        // 2) Pede reflexões REMOTAS (Firebase), junta com as LOCAIS e só depois gera PDF
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
                        // Remoto complementa/sobrepõe o local para este dia
                        reflText.put(dayKey, sb.toString());
                    }
                }

                // 3) Diário LOCAL → preencher Mapa (antes estava vazio)
                Map<String, String> diaryText = new HashMap<>();
                List<sqlite.DiaryEntry> allDiary = db.getDiaryLastNDays(3650);
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
            @Override public void onCancelled(@NonNull DatabaseError error) { /* noop */ }
        });
    }

    /* -------------------- PDF helpers -------------------- */

    // Cria o PDF: capa + páginas com cada dia (título, texto do diário, reflexões)
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

    // Desenha parágrafos (quebra linhas + espaçamento)
    private int drawParagraph(Canvas canvas, Paint paint, String text, int x, int y, int maxWidth, int lineH) {
        if (text == null || text.trim().isEmpty()) return y;
        String[] paragraphs = text.split("\\n");
        for (String p : paragraphs) {
            y = drawWrapped(canvas, paint, p, x, y, maxWidth, lineH);
            y += 6;
        }
        return y;
    }

    // Quebra de linha simples por palavras, respeitando largura máxima
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

    /* -------------------- Misc -------------------- */

    @Override protected int currentNavItemId() { return R.id.nav_diary; }

    /**
     * Validador de datas permitidas no DatePicker (dias com conteúdo).
     */


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

    // Helpers de datas
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
