package pt.ubi.pdm.projetofinal;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;


public class RegisterActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private TextInputEditText etEmail, etPass, etPass2;
    private View btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        etEmail = findViewById(R.id.etRegEmail);
        etPass  = findViewById(R.id.etRegPass);
        etPass2 = findViewById(R.id.etRegPass2);
        btnRegister = findViewById(R.id.btnRegister);

        btnRegister.setOnClickListener(v -> createAccount());
        findViewById(R.id.backToLogin).setOnClickListener(v -> finish());
    }

    private String extractNameFallback(String email) {
        if (email == null) return "Utilizador";
        String left = email.split("@")[0];
        // remove símbolos e números
        left = left.replaceAll("[^a-zA-Z]", " ").trim();
        if (left.isEmpty()) return "Utilizador";

        String[] parts = left.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1).toLowerCase());
            sb.append(' ');
        }
        return sb.toString().trim();
    }


    private void createAccount() {
        String email = String.valueOf(etEmail.getText()).trim();
        String p1 = String.valueOf(etPass.getText());
        String p2 = String.valueOf(etPass2.getText());

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { etEmail.setError("Email inválido"); return; }
        if (p1.length() < 6) { etPass.setError("Mín. 6 caracteres"); return; }
        if (!p1.equals(p2)) { etPass2.setError("As palavras-passe não coincidem"); return; }

        btnRegister.setEnabled(false);
        auth.createUserWithEmailAndPassword(email, p1)
                .addOnCompleteListener(t -> {
                    btnRegister.setEnabled(true);
                    if (t.isSuccessful()) {
                        String nomeUser = extractNameFallback(email);

                        FirebaseUser u = auth.getCurrentUser();
                        if (u != null) {
                            UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(nomeUser)
                                    .build();
                            u.updateProfile(req);
                        }

                        getSharedPreferences("oa", MODE_PRIVATE)
                                .edit().putString("nome", nomeUser).apply();

                        Toast.makeText(this, "Conta criada!", Toast.LENGTH_SHORT).show();

                        // 👉 vai direto para a Home e limpa o back stack
                        Intent i = new Intent(RegisterActivity.this, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        // opcional: finishAffinity();  // se preferires
                    } else {
                        Toast.makeText(this, "Erro: " + t.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}
