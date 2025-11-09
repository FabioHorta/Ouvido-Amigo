package pt.ubi.pdm.projetofinal;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

// Activity que implementa uma pausa guiada com contagem decrescente.
// - Mantém o ecrã ligado e entra em modo imersivo.
// - Tenta ativar "Screen Pinning" para evitar distrações.
// - Impede o botão de voltar.
// - Permite saída de emergência com 3 toques no ecrã.

public class PauseActivity extends AppCompatActivity {
    public static final String EXTRA_DURATION_MS = "duration_ms";

    private CountDownTimer timer;
    private long durationMs = 10 * 60 * 1000L; // Duração padrão: 10 minutos
    private TextView tvCountdown, tvHint;
    private int tapCount = 0; // Contador de toques para saída de emergência

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pause);

        // Mantém o ecrã ligado e ativa modo imersivo (oculta barra de navegação e status)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();

        // Inicializa componentes da UI
        tvCountdown = findViewById(R.id.tvCountdown);
        tvHint = findViewById(R.id.tvHint);

        // Lê a duração da pausa passada por Intent
        long extra = getIntent().getLongExtra(EXTRA_DURATION_MS, durationMs);
        if (extra > 0) durationMs = extra;

        // Tenta ativar "Screen Pinning" para evitar que o utilizador saia da app
        try {
            startLockTask();
            tvHint.setText("Pausa em progresso • App fixada. Prima Voltar e Recentes para sair após terminar.");
        } catch (Exception ignored) {
            // Caso não seja possível ativar, apenas mostra aviso
            tvHint.setText("Pausa em progresso • Para evitar distrações, não saias desta página.");
        }

        startCountdown(); // Inicia a contagem decrescente
        setupEmergencyExit(); // Configura saída de emergência

        // Impede o botão físico de voltar
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Ignora o botão "Voltar"
            }
        });
    }

    // Ativa o modo imersivo para ocultar a UI do sistema.
    // Reativa automaticamente após mudanças de visibilidade.
    private void enterImmersiveMode() {
        final View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        decor.setOnSystemUiVisibilityChangeListener(v -> decor.postDelayed(this::enterImmersiveMode, 300));
    }

    // Inicia a contagem decrescente com atualização a cada segundo.
    // Ao terminar, mostra mensagem e fecha a Activity.
    private void startCountdown() {
        updateCountdownText(durationMs);
        timer = new CountDownTimer(durationMs, 1000L) {
            @Override
            public void onTick(long ms) {
                updateCountdownText(ms);
            }

            @Override
            public void onFinish() {
                updateCountdownText(0);
                try {
                    stopLockTask();
                } catch (Exception ignored) {
                }
                Toast.makeText(PauseActivity.this, "Pausa concluída ✨", Toast.LENGTH_SHORT).show();
                finish();
            }
        }.start();
    }

    // Atualiza o texto do cronómetro no formato MM:SS.
    private void updateCountdownText(long ms) {
        long totalSec = ms / 1000L;
        long m = totalSec / 60L;
        long s = totalSec % 60L;
        tvCountdown.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", m, s));
    }

    // Permite sair da pausa com 3 toques rápidos no ecrã.
    // Reinicia o contador de toques após 3 segundos.
    private void setupEmergencyExit() {
        View root = findViewById(R.id.pauseRoot);
        root.setOnClickListener(v -> {
            tapCount++;
            if (tapCount == 3) {
                Toast.makeText(this, "Saída de emergência.", Toast.LENGTH_SHORT).show();
                try {
                    stopLockTask();
                } catch (Exception ignored) {
                }
                finish();
            } else {
                root.postDelayed(() -> tapCount = 0, 3000);
            }
        });
    }

    // Cancela o temporizador e desativa o "screen pinning" ao destruir a Activity.
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
        try {
            stopLockTask();
        } catch (Exception ignored) {
        }
    }
}