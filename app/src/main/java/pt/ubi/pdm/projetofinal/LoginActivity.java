package pt.ubi.pdm.projetofinal;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

// Classe responsável pelo ecrã de login da aplicação.
// Funcionalidades:
// - Login com email e palavra-passe usando Firebase Authentication.
// - Login com conta Google (Google Sign-In + Firebase).
// - Redireciona automaticamente para a MainActivity se o utilizador já estiver registado.
// - Guarda o nome do utilizador nas SharedPreferences para uso posterior na aplicação.
// - Valida os dados de entrada (email e password) antes de autenticar.

public class LoginActivity extends AppCompatActivity {

    private static final int RC_GOOGLE = 9001;

    private FirebaseAuth auth;
    private GoogleSignInClient googleClient;

    private TextInputEditText etEmail, etPass;


    // Inicia a interface e os componentes da UI.
    // Configura os botões de login por email, login com Google e navegação para o registo.
    // Inicia o cliente de autenticação do Google.

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        // UI
        etEmail = findViewById(R.id.etEmail);
        etPass  = findViewById(R.id.etPass);

        findViewById(R.id.btnLogin).setOnClickListener(v -> doEmailLogin());
        findViewById(R.id.btnGoogle).setOnClickListener(v -> startGoogleLogin());
        View btnToRegister = findViewById(R.id.btnToRegister);
        btnToRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));

        // Google Sign-In client
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);
    }


    // Verifica se o utilizador já está autenticado.
    // Se sim, guarda o nome nas SharedPreferences e redireciona para a MainActivity

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u != null) {
            // já tem login – guarda/atualiza o nome e vai para a Home
            String nomeUser = u.getDisplayName();
            if (nomeUser == null || nomeUser.trim().isEmpty()) {
                nomeUser = extractNameFallback(u.getEmail());
            }
            getSharedPreferences("oa", MODE_PRIVATE)
                    .edit().putString("nome", nomeUser).apply();

            Intent i = new Intent(LoginActivity.this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        }
    }

    // ============================================================
    // Secção: Email e Password
    // ============================================================

    // Valida o email e a palavra-passe inseridos.
    // Se válidos, tenta autenticar com Firebase.
    // Em caso de sucesso, guarda o nome e redireciona para a MainActivity.

    private void doEmailLogin() {
        String email = String.valueOf(etEmail.getText()).trim();
        String pass  = String.valueOf(etPass.getText());

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Email inválido");
            return;
        }
        if (pass.length() < 6) {
            etPass.setError("Mín. 6 caracteres");
            return;
        }

        findViewById(R.id.btnLogin).setEnabled(false);

        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(result -> {
                    findViewById(R.id.btnLogin).setEnabled(true);

                    FirebaseUser u = auth.getCurrentUser();
                    String nomeUser;
                    if (u != null) {
                        nomeUser = u.getDisplayName();
                        if (nomeUser == null || nomeUser.trim().isEmpty()) {
                            nomeUser = extractNameFallback(u.getEmail());
                        }
                    } else {
                        nomeUser = extractNameFallback(email);
                    }

                    // guardar nome para a Home
                    getSharedPreferences("oa", MODE_PRIVATE)
                            .edit().                                         putString("nome", nomeUser).apply();

                    goHome();
                })
                .addOnFailureListener(e -> {
                    findViewById(R.id.btnLogin).setEnabled(true);
                    Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // ============================================================
    // Secção: Google SigIn e Firebase
    // ============================================================

    // Inicia o processo de login com conta Google.
    // Após autenticação, obtém o nome do utilizador e guarda-o nas SharedPreferences.
    // Redireciona para a MainActivity.

    private void startGoogleLogin() {
        Intent signInIntent = googleClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_GOOGLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_GOOGLE) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                String idToken = account.getIdToken();
                AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);

                auth.signInWithCredential(credential)
                        .addOnSuccessListener(result -> {
                            FirebaseUser u = auth.getCurrentUser();

                            String nomeUser = null;
                            if (u != null) {
                                nomeUser = u.getDisplayName();
                                if (nomeUser == null || nomeUser.trim().isEmpty()) {
                                    nomeUser = account.getGivenName();
                                }
                                if (nomeUser == null || nomeUser.trim().isEmpty()) {
                                    nomeUser = extractNameFallback(account.getEmail());
                                }
                            } else {
                                nomeUser = extractNameFallback(account.getEmail());
                            }

                            getSharedPreferences("oa", MODE_PRIVATE)
                                    .edit().putString("nome", nomeUser).apply();

                            goHome();
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Google/Firebase: " + e.getMessage(), Toast.LENGTH_LONG).show());

            } catch (ApiException e) {
                Toast.makeText(this, "Google Sign-In falhou: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    // ============================================================
    // Secção: Utils
    // ============================================================
    // Abre a MainActivity e termina a LoginActivity.
    private void goHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }


    // Gera um nome de utilizador com base no email, removendo símbolos e números.
    // Utilizado como fallback caso o nome não esteja disponível no Firebase.

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

}
