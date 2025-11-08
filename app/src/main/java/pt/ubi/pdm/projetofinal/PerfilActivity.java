package pt.ubi.pdm.projetofinal;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class PerfilActivity extends AppCompatActivity {

    private ImageView imgAvatar;
    private TextInputEditText etName, etDob;
    private TextInputLayout tilDob;

    private DatabaseReference profileRef;
    private String uid;

    private Uri tempCameraUri = null;
    private File localAvatarFile; // /data/data/<app>/files/avatar_<uid>.jpg

    // Galeria
    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) handleNewPhotoFromUri(uri);
            });

    // Câmara
    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (Boolean.TRUE.equals(ok) && tempCameraUri != null) {
                    handleNewPhotoFromUri(tempCameraUri);
                }
            });

    // Permissões
    private final ActivityResultLauncher<String[]> reqPerms =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
                if (Boolean.TRUE.equals(r.getOrDefault(Manifest.permission.CAMERA, false))) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Permissão de câmara negada.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil);

        imgAvatar = findViewById(R.id.imgAvatar);
        etName    = findViewById(R.id.etName);
        etDob     = findViewById(R.id.etDob);
        tilDob    = findViewById(R.id.tilDob);

        findViewById(R.id.fabChangePhoto).setOnClickListener(v -> showPhotoSheet());
        findViewById(R.id.btnPickGallery).setOnClickListener(v -> pickImage.launch("image/*"));
        findViewById(R.id.btnTakePhoto).setOnClickListener(v -> requestCamera());
        findViewById(R.id.btnRemovePhoto).setOnClickListener(v -> removeAvatar());
        findViewById(R.id.btnSave).setOnClickListener(v -> saveProfile());
        findViewById(R.id.btnChangePassword).setOnClickListener(v -> showChangePasswordDialog());
        findViewById(R.id.btnLogout).setOnClickListener(v -> logout());

        tilDob.setEndIconOnClickListener(v -> openDatePicker());
        etDob.setOnClickListener(v -> openDatePicker());

        // Firebase
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        uid = user.getUid();

        profileRef = FirebaseDatabase.getInstance()
                .getReference("users").child(uid).child("profile");
        profileRef.keepSynced(true); // 🔸 offline sync

        // Avatar local por utilizador
        localAvatarFile = new File(getFilesDir(), "avatar_" + uid + ".jpg");

        // 🔸 carregar dados/foto ao abrir
        loadProfile();
    }

    /* ------------------ LOAD/SAVE PERFIL ------------------ */
    private void loadProfile() {
        profileRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                String name     = snap.child("displayName").getValue(String.class);
                String dobIso   = snap.child("dob").getValue(String.class);
                String photoB64 = snap.child("photoB64").getValue(String.class);

                if (!TextUtils.isEmpty(name)) etName.setText(name);
                if (!TextUtils.isEmpty(dobIso)) etDob.setText(isoToUi(dobIso));

                // Foto local > Base64 > default
                if (localAvatarFile.exists()) {
                    Bitmap bmp = BitmapFactory.decodeFile(localAvatarFile.getAbsolutePath());
                    if (bmp != null) {
                        imgAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        imgAvatar.setImageBitmap(bmp);
                    }
                } else if (!TextUtils.isEmpty(photoB64)) {
                    Bitmap bmp = base64ToBitmap(photoB64);
                    if (bmp != null) {
                        imgAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        imgAvatar.setImageBitmap(bmp);
                        saveBitmapToFile(bmp, localAvatarFile, 90);
                    }
                } else {
                    imgAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imgAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void saveProfile() {
        String name = val(etName);
        String dobUi = val(etDob);

        if (name.length() < 2 || name.length() > 30) {
            Toast.makeText(this, "Nome deve ter 2–30 caracteres.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String,Object> data = new HashMap<>();
        data.put("displayName", name);
        if (!TextUtils.isEmpty(dobUi)) data.put("dob", uiToIso(dobUi));

        profileRef.updateChildren(data)
                .addOnSuccessListener(v -> {
                    // 🔸 prefs por utilizador
                    getSharedPreferences("oa_" + uid, MODE_PRIVATE)
                            .edit().putString("nome", name).apply();
                    Toast.makeText(this, "Perfil atualizado ✅", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    /* ------------------ DATE PICKER ------------------ */
    private void openDatePicker() {
        MaterialDatePicker<Long> dp = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Seleciona a data de nascimento")
                .build();
        dp.addOnPositiveButtonClickListener(selection -> {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(selection);
            etDob.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(c.getTime()));
        });
        dp.show(getSupportFragmentManager(), "dob");
    }

    private String uiToIso(String ddMMyyyy) {
        try {
            Date d = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(ddMMyyyy);
            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Objects.requireNonNull(d));
        } catch (Exception e) { return ""; }
    }
    private String isoToUi(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(iso);
            return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Objects.requireNonNull(d));
        } catch (Exception e) { return ""; }
    }

    /* ------------------ FOTO: GALERIA/CÂMARA/RTDB ------------------ */
    private void showPhotoSheet() {
        String[] options = {"Galeria", "Câmara", "Remover foto"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Foto de perfil")
                .setItems(options, (d, w) -> {
                    if (w == 0) pickImage.launch("image/*");
                    else if (w == 1) requestCamera();
                    else removeAvatar();
                }).show();
    }

    private void requestCamera() {
        List<String> need = new ArrayList<>();
        need.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT <= 32) need.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        reqPerms.launch(need.toArray(new String[0]));
    }

    private void startCamera() {
        try {
            File dir = new File(getExternalFilesDir(null), "tmp");
            if (!dir.exists()) dir.mkdirs();
            File file = File.createTempFile("avatar_", ".jpg", dir);
            tempCameraUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            takePicture.launch(tempCameraUri);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir a câmara.", Toast.LENGTH_LONG).show();
        }
    }

    private void handleNewPhotoFromUri(@NonNull Uri uri) {
        Bitmap bmp = decodeDownsample(uri, 2048);
        if (bmp == null) { Toast.makeText(this, "Imagem inválida.", Toast.LENGTH_SHORT).show(); return; }

        // Mostrar e guardar local
        imgAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imgAvatar.setImageBitmap(bmp);
        saveBitmapToFile(bmp, localAvatarFile, 90);

        // Miniatura para RTDB
        Bitmap tiny = scaleMax(bmp, 512);
        String b64 = bitmapToBase64(tiny, 85);
        Map<String,Object> data = new HashMap<>();
        data.put("photoB64", b64);
        data.put("photoUpdatedAt", ServerValue.TIMESTAMP);
        profileRef.updateChildren(data);
    }

    private void removeAvatar() {
        if (localAvatarFile.exists()) localAvatarFile.delete();
        imgAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imgAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
        Map<String,Object> upd = new HashMap<>();
        upd.put("photoB64", null);
        profileRef.updateChildren(upd);
        Toast.makeText(this, "Foto removida.", Toast.LENGTH_SHORT).show();
    }

    /* ------------------ PASSWORD + LOGOUT ------------------ */
    private void showChangePasswordDialog() {
    // Campo "Nova palavra-passe"
        TextInputLayout tilNew = new TextInputLayout(this);
        tilNew.setHint("Nova palavra-passe");
        tilNew.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

        TextInputEditText etNew = new TextInputEditText(this);
        tilNew.addView(etNew);

    // Campo "Confirmar palavra-passe"
        TextInputLayout tilConf = new TextInputLayout(this);
        tilConf.setHint("Confirmar palavra-passe");
        tilConf.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

        TextInputEditText etConf = new TextInputEditText(this);
        tilConf.addView(etConf);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, 0);
        container.addView(tilNew);
        container.addView(tilConf);

        AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                .setTitle("Alterar palavra-passe")
                .setView(container)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null)
                .create();

        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n1 = val(etNew), n2 = val(etConf);
            if (!validPassword(n1)) { tilNew.setError("Min. 6 caracteres"); return; }
            if (!n1.equals(n2)) { tilConf.setError("As palavras-passe não coincidem"); return; }

            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u == null) return;

            u.updatePassword(n1).addOnSuccessListener(vv -> {
                Toast.makeText(this, "Palavra-passe atualizada ✅", Toast.LENGTH_SHORT).show();
                dlg.dismiss();
            }).addOnFailureListener(e -> {
                askReauthThenUpdate(n1);
            });
        }));
        dlg.show();
    }

    private void askReauthThenUpdate(String newPass) {
        TextInputLayout tilCurr = new TextInputLayout(this);
        tilCurr.setHint("Palavra-passe atual");
        tilCurr.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

        TextInputEditText etCurr = new TextInputEditText(this);
        tilCurr.addView(etCurr);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, 0);
        container.addView(tilCurr);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Confirmar identidade")
                .setView(container)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Confirmar", (d, w) -> {
                    FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
                    if (u == null || u.getEmail() == null) return;
                    String current = val(etCurr);
                    u.reauthenticate(EmailAuthProvider.getCredential(u.getEmail(), current))
                            .addOnSuccessListener(v ->
                                    u.updatePassword(newPass)
                                            .addOnSuccessListener(vv ->
                                                    Toast.makeText(this, "Palavra-passe atualizada ✅", Toast.LENGTH_SHORT).show())
                                            .addOnFailureListener(e ->
                                                    Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show()))
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Falha na reautenticação.", Toast.LENGTH_LONG).show());
                }).show();
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finishAffinity();
    }

    /* ------------------ HELPERS ------------------ */
    private String val(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
    private boolean validPassword(String p) { return p != null && p.length() >= 6; }

    private Bitmap decodeDownsample(Uri uri, int maxDimPx) {
        try {
            ContentResolver cr = getContentResolver();
            InputStream in1 = cr.openInputStream(uri);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in1, null, opts);
            if (in1 != null) in1.close();

            int scale = 1;
            int max = Math.max(opts.outWidth, opts.outHeight);
            while (max / scale > maxDimPx) scale *= 2;

            BitmapFactory.Options opts2 = new BitmapFactory.Options();
            opts2.inSampleSize = scale;
            InputStream in2 = cr.openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(in2, null, opts2);
            if (in2 != null) in2.close();
            return bmp;
        } catch (Exception e) { return null; }
    }

    private Bitmap scaleMax(Bitmap src, int maxDim) {
        int w = src.getWidth(), h = src.getHeight();
        float r = (float) maxDim / Math.max(w, h);
        if (r >= 1f) return src;
        int nw = Math.round(w * r), nh = Math.round(h * r);
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }

    private void saveBitmapToFile(Bitmap bmp, File file, int qualityJpeg) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, qualityJpeg, out);
        } catch (Exception ignored) {}
    }

    private String bitmapToBase64(Bitmap bmp, int qualityJpeg) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, qualityJpeg, bos);
        return android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP);
    }

    private Bitmap base64ToBitmap(String b64) {
        try {
            byte[] data = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception e) { return null; }
    }
}
