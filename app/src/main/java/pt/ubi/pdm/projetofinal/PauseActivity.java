package pt.ubi.pdm.projetofinal;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class PauseActivity extends AppCompatActivity {

    public static final String EXTRA_DURATION_MS = "duration_ms";
    private CountDownTimer timer;
    private long durationMs = 10 * 60 * 1000L; // default 10 min
    private TextView tvCountdown, tvHint;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pause);

        // Mantém ecrã ligado e esconde UI do sistema (imersivo)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();

        tvCountdown = findViewById(R.id.tvCountdown);
        tvHint      = findViewById(R.id.tvHint);

        long extra = getIntent().getLongExtra(EXTRA_DURATION_MS, durationMs);
        if (extra > 0) durationMs = extra;

        // Tentar "Screen Pinning" (fixar app)
        try {
            startLockTask(); // só funciona se o utilizador tiver activado "Fixação de ecrã" nas Definições
            tvHint.setText("Pausa em progresso • App fixada. Prima Voltar e Recentes para sair após terminar.");
        } catch (Exception ignored) {
            // Sem pinning, seguimos mesmo assim.
            tvHint.setText("Pausa em progresso • Para evitar distrações, não saias desta página.");
        }

        startCountdown();
        // Tap triplo em 3s para sair antes do tempo (safety)
        setupEmergencyExit();
    }

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

    private void startCountdown() {
        updateCountdownText(durationMs);
        timer = new CountDownTimer(durationMs, 1000L) {
            @Override public void onTick(long ms) { updateCountdownText(ms); }
            @Override public void onFinish() {
                updateCountdownText(0);
                try { stopLockTask(); } catch (Exception ignored) {}
                Toast.makeText(PauseActivity.this, "Pausa concluída ✨", Toast.LENGTH_SHORT).show();
                finish();
            }
        }.start();
    }

    private void updateCountdownText(long ms) {
        long totalSec = ms / 1000L;
        long m = totalSec / 60L;
        long s = totalSec % 60L;
        tvCountdown.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", m, s));
    }

    private int tapCount = 0;
    private void setupEmergencyExit() {
        View root = findViewById(R.id.pauseRoot);
        root.setOnClickListener(v -> {
            tapCount++;
            if (tapCount == 3) {
                Toast.makeText(this, "Saída de emergência.", Toast.LENGTH_SHORT).show();
                try { stopLockTask(); } catch (Exception ignored) {}
                finish();
            } else {
                root.postDelayed(() -> tapCount = 0, 3000);
            }
        });
    }

    @Override public void onBackPressed() {
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
        try { stopLockTask(); } catch (Exception ignored) {}
    }
}
