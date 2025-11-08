package pt.ubi.pdm.projetofinal;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ecrã de Exercícios:
 * - Lista de exercícios (local) com categorias e progresso
 * - Secção de áudios de relaxamento (res/raw)
 * - Preferências simples (SharedPreferences) para concluídos e lembretes
 */
public class ExercisesActivity extends BaseBottomNavActivity {

    // --- UI ---
    private TextView tvPercent, tvProgressoSub;
    private RecyclerView rvExercicios, rvAudios;
    private ChipGroup chipGroup;
    private MaterialSwitch switchLembretes;

    // --- Dados ---
    private final List<Exercicio> allExercicios = new ArrayList<>();
    private final List<Exercicio> shownExercicios = new ArrayList<>();
    private final List<AudioRelax> audios = new ArrayList<>();

    private ExercicioAdapter exAdapter;
    private AudioAdapter audioAdapter;

    private static MediaPlayer mp;
    private static int currentlyPlaying = -1; // índice do áudio a tocar, -1 se nenhum


    private static final String PREFS = "oa_exercises";
    private static final String PREF_DONE = "done_set";           // ids concluídos
    private static final String PREF_REMINDERS = "reminders_on";  // lembretes

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercises);

        // Bottom bar
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        attachBottomNav(bottomNav);

        // Ligações UI
        tvPercent = findViewById(R.id.tvPercent);
        tvProgressoSub = findViewById(R.id.tvProgressoSub);
        chipGroup = findViewById(R.id.chips);
        switchLembretes = findViewById(R.id.switchLembretes);
        rvExercicios = findViewById(R.id.rvExercicios);
        rvAudios = findViewById(R.id.rvAudios);

        // Listas / Adapters
        setupData();            // carrega exercícios (e marca concluídos a partir de prefs)
        setupRecyclerViews();   // configura recyclers e adapters
        setupChipsFiltering();  // filtra por categorias via chips
        setupAudios();          // carrega lista de áudios
        setupReminders();       // liga o switch aos prefs

        // Atualiza progressos (percentagem e fração) de acordo com o filtro atual
        applyFilterAndUpdateProgress();
    }

    /* ----------------------- Dados base ----------------------- */

    private void setupData() {
        // Repôr concluídos do storage (atenção: o set devolvido não deve ser modificado)
        Set<String> doneIds = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet(PREF_DONE, new HashSet<>());

        // Exercícios (podes renomear/ajustar tempos/categorias)
        // Categoria: Físico
        allExercicios.add(new Exercicio("Alongamentos matinais", "5–8 min · mobilidade", "Físico", doneIds.contains("ex_1"), "ex_1"));
        allExercicios.add(new Exercicio("Caminhada leve", "10–15 min · exterior", "Físico", doneIds.contains("ex_2"), "ex_2"));

        // Categoria: Mental
        allExercicios.add(new Exercicio("3 gratidões", "5 min · reflexão escrita", "Mental", doneIds.contains("ex_3"), "ex_3"));
        allExercicios.add(new Exercicio("Respiração 4-7-8", "3–5 min · foco", "Mental", doneIds.contains("ex_4"), "ex_4"));

        // Categoria: Relaxamento
        allExercicios.add(new Exercicio("Varredura corporal", "8–10 min · relax", "Relaxamento", doneIds.contains("ex_5"), "ex_5"));
        allExercicios.add(new Exercicio("Pausa consciente", "3–5 min · mindfulness", "Relaxamento", doneIds.contains("ex_6"), "ex_6"));

        // Categoria: Sono
        allExercicios.add(new Exercicio("Higiene do sono", "2–3 min · check-list", "Sono", doneIds.contains("ex_7"), "ex_7"));
        allExercicios.add(new Exercicio("Desligar ecrãs", "Definir hora", "Sono", doneIds.contains("ex_8"), "ex_8"));

        // Categoria: Saúde
        allExercicios.add(new Exercicio("Hidratação", "Bebe 6–8 copos", "Saúde", doneIds.contains("ex_9"), "ex_9"));
        allExercicios.add(new Exercicio("Alongar pescoço/ombros", "2–3 min", "Saúde", doneIds.contains("ex_10"), "ex_10"));

        // Inicialmente mostra todos
        shownExercicios.addAll(allExercicios);
    }

    private void setupAudios() {
        // Áudios embebidos (ficheiros em res/raw). Troca/ajusta à vontade.
        audios.add(new AudioRelax("Respiração calma", R.raw.breath_calm));
        audios.add(new AudioRelax("Body scan curto", R.raw.body_scan_short));
        audios.add(new AudioRelax("Som de chuva", R.raw.rain));
        audioAdapter.notifyDataSetChanged();
    }

    /* ----------------------- RecyclerViews ------------------------- */

    private void setupRecyclerViews() {
        // Grelha 2 colunas para exercícios
        GridLayoutManager glm = new GridLayoutManager(this, 2);
        rvExercicios.setLayoutManager(glm);
        exAdapter = new ExercicioAdapter(shownExercicios, new ExercicioAdapter.OnCheckChanged() {
            @Override public void onToggle(@NonNull Exercicio ex, boolean checked) {
                // Atualiza estado, persiste e recalcula progresso
                ex.done = checked;
                saveDoneSet();
                updateProgress();
            }
        });
        rvExercicios.setAdapter(exAdapter);

        // Lista horizontal para áudios
        rvAudios.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        audioAdapter = new AudioAdapter(audios, new AudioAdapter.OnAudioAction() {
            @Override public void onPlayClick(int position) {
                handlePlay(position);
            }
        });
        rvAudios.setAdapter(audioAdapter);
    }

    /* --------------------------- Chips (filtro) ----------------------------- */

    private void setupChipsFiltering() {
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilterAndUpdateProgress());
    }

    // Aplica filtro pelas categorias ativas e recalcula progresso
    private void applyFilterAndUpdateProgress() {
        Set<String> active = new HashSet<>();
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            View v = chipGroup.getChildAt(i);
            if (v instanceof Chip && ((Chip) v).isChecked()) {
                String label = ((Chip) v).getText().toString();
                switch (label) {
                    case "Exercício":
                        active.add("Físico"); active.add("Mental"); active.add("Relaxamento"); active.add("Sono"); active.add("Saúde");
                        break;
                    case "Áudio":
                        // reservado: se quiseres no futuro ocultar/mostrar a secção de áudios
                        break;
                    case "Sono":   active.add("Sono");   break;
                    case "Saúde":  active.add("Saúde");  break;
                }
            }
        }

        shownExercicios.clear();
        if (active.isEmpty()) {
            shownExercicios.addAll(allExercicios);
        } else {
            for (Exercicio e : allExercicios) {
                if (active.contains(e.categoria)) shownExercicios.add(e);
            }
        }
        exAdapter.notifyDataSetChanged();
        updateProgress();
    }

    /* ----------------------- Progresso/Lembretes ------------------- */

    // Atualiza percentagem e fração dos exercícios visíveis (respeita o filtro)
    private void updateProgress() {
        int total = shownExercicios.size();
        int done = 0;
        for (Exercicio e : shownExercicios) if (e.done) done++;

        int percent = total == 0 ? 0 : Math.round(100f * done / total);
        tvProgressoSub.setText(String.format(Locale.getDefault(), "%d/%d exercícios concluídos", done, total));
        tvPercent.setText(percent + "%");
    }

    // Guarda preferências do lembrete (apenas a flag; o agendamento real pode ser feito noutro ponto)
    private void setupReminders() {
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_REMINDERS, true);
        switchLembretes.setChecked(enabled);
        switchLembretes.setOnCheckedChangeListener((button, isChecked) ->
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_REMINDERS, isChecked).apply()
        );
    }

    // Persiste conjunto de exercícios concluídos (ids estáveis ex_1, ex_2, …)
    private void saveDoneSet() {
        HashSet<String> set = new HashSet<>();
        for (Exercicio e : allExercicios) if (e.done) set.add(e.id);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putStringSet(PREF_DONE, set).apply();
    }

    /* --------------------------- Áudio ----------------------------- */

    // Toca/pára a faixa no índice 'position' e mantém o UI coerente
    private void handlePlay(int position) {
        AudioRelax a = audios.get(position);

        // Sem ficheiro? aviso
        if (a.resId == 0) {
            Toast.makeText(this, "Áudio não encontrado (adiciona o ficheiro em res/raw).", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Se já está a tocar o mesmo, pára
            if (currentlyPlaying == position && mp != null) {
                mp.stop();
                mp.release();
                mp = null;
                int prev = currentlyPlaying;
                currentlyPlaying = -1;
                audioAdapter.notifyItemChanged(prev);
                return;
            }

            // Trocar de faixa: parar a anterior (se existir)
            int prev = currentlyPlaying;
            if (mp != null) { mp.stop(); mp.release(); mp = null; }

            // Criar e iniciar novo MediaPlayer
            mp = MediaPlayer.create(this, a.resId);
            if (mp == null) {
                Toast.makeText(this, "Não foi possível reproduzir este áudio.", Toast.LENGTH_SHORT).show();
                // repor estado visual do anterior, se necessário
                if (prev >= 0) audioAdapter.notifyItemChanged(prev);
                return;
            }

            currentlyPlaying = position;
            mp.setOnCompletionListener(m -> {
                int justPlayed = currentlyPlaying;
                currentlyPlaying = -1;
                audioAdapter.notifyItemChanged(justPlayed);
            });

            mp.start();

            // Atualizar anterior e atual (para o texto do botão refletir “Parar/Tocar”)
            if (prev >= 0) audioAdapter.notifyItemChanged(prev);
            audioAdapter.notifyItemChanged(position);

        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível reproduzir este áudio.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mp != null) {
            mp.release();
            mp = null;
            int prev = currentlyPlaying;
            currentlyPlaying = -1;
            if (prev >= 0) audioAdapter.notifyItemChanged(prev);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Defesa adicional (caso onStop não tenha corrido)
        if (mp != null) { mp.release(); mp = null; }
    }

    @Override
    protected int currentNavItemId() { return R.id.nav_exercises; }

    /* ======================= MODELOS/ADAPTERS ======================= */

    // Modelo de exercício
    private static class Exercicio {
        final String titulo, info, categoria, id;
        boolean done;
        Exercicio(String t, String i, String c, boolean d, String id) {
            this.titulo = t; this.info = i; this.categoria = c; this.done = d; this.id = id;
        }
    }

    // Modelo de áudio (título e referência ao ficheiro em res/raw)
    private static class AudioRelax {
        final String titulo;
        final int resId; // res/raw
        AudioRelax(String t, int r) { this.titulo = t; this.resId = r; }
    }

    // Adapter dos exercícios (item_exercicio.xml)
    private static class ExercicioAdapter extends RecyclerView.Adapter<ExercicioAdapter.VH> {
        interface OnCheckChanged { void onToggle(@NonNull Exercicio ex, boolean checked); }
        private final List<Exercicio> data;
        private final OnCheckChanged cb;
        ExercicioAdapter(List<Exercicio> data, OnCheckChanged cb) { this.data = data; this.cb = cb; }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvTitulo, tvInfo;
            CheckBox cbConcluido;
            VH(@NonNull View v) {
                super(v);
                tvTitulo     = v.findViewById(R.id.tvExercicioTitulo);
                tvInfo       = v.findViewById(R.id.tvExercicioInfo);
                cbConcluido  = v.findViewById(R.id.cbConcluido);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_exercicio, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Exercicio ex = data.get(pos);
            h.tvTitulo.setText(ex.titulo);
            h.tvInfo.setText(ex.info);

            h.cbConcluido.setOnCheckedChangeListener(null);
            h.cbConcluido.setChecked(ex.done);

            // Tocar no cartão alterna o checkbox (UX rápida)
            ((MaterialCardView) h.itemView).setOnClickListener(v -> h.cbConcluido.toggle());

            // Quando o utilizador muda o estado, notifica a Activity via callback
            h.cbConcluido.setOnCheckedChangeListener((buttonView, isChecked) -> cb.onToggle(ex, isChecked));
        }

        @Override public int getItemCount() { return data.size(); }
    }

    // Adapter dos áudios (item_audio.xml)
    private static class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.VH> {
        interface OnAudioAction { void onPlayClick(int position); }
        private final List<AudioRelax> data;
        private final OnAudioAction cb;
        AudioAdapter(List<AudioRelax> d, OnAudioAction cb) { this.data = d; this.cb = cb; }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitulo;
            com.google.android.material.button.MaterialButton btnPlay;
            VH(@NonNull View v) {
                super(v);
                tvTitulo = v.findViewById(R.id.tvAudioTitulo);
                btnPlay  = v.findViewById(R.id.btnTocar);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_audio, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            AudioRelax a = data.get(pos);
            h.tvTitulo.setText(a.titulo);

            // O texto do botão reflete o estado global (índice a tocar)
            boolean isThisPlaying = (pos == currentlyPlaying && mp != null && mp.isPlaying());
            h.btnPlay.setText(isThisPlaying ? "Parar" : "Tocar");

            // Usa getBindingAdapterPosition para garantir posição correta após animações/updates
            h.btnPlay.setOnClickListener(v -> {
                int adapterPos = h.getBindingAdapterPosition();
                if (adapterPos != RecyclerView.NO_POSITION) cb.onPlayClick(adapterPos);
            });
        }

        @Override public int getItemCount() { return data.size(); }
    }
}
