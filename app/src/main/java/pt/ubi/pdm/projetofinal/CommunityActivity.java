package pt.ubi.pdm.projetofinal;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Date;
import java.util.Map;

/**
 * Ecrã da Comunidade:
 * - Lista de publicações (Firebase RTDB, com cache offline).
 * - Criar nova publicação (texto + anónimo opcional).
 */

public class CommunityActivity extends BaseBottomNavActivity {

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvPosts;

    private DatabaseReference postsRef;
    private FirebaseUser user;

    // Lista em memória dos posts (mostrados no RecyclerView)
    private final List<Post> posts = new ArrayList<>();
    private PostAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community);


        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        attachBottomNav(bottomNav);


        user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }

        // --- RTDB: /community/posts (mantém sincronizado para offline) ---

        postsRef = FirebaseDatabase.getInstance()
                .getReference("community")
                .child("posts");
        postsRef.keepSynced(true);

        // --- UI principal: pull-to-refresh + lista de posts ---
        swipeRefresh = findViewById(R.id.swipeRefresh);
        rvPosts = findViewById(R.id.rvPosts);
        rvPosts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PostAdapter(posts, this::openCommentsSheet);
        rvPosts.setAdapter(adapter);

        // Gesto de refresh manual
        swipeRefresh.setOnRefreshListener(() -> {
            loadPostsOnce();
            stopRefreshIfStuck();
        });
        // Criar uma nova publicação

        findViewById(R.id.fabAddPost).setOnClickListener(v -> openNewPostSheet());


        swipeRefresh.setRefreshing(true);
        loadPostsOnce();
        stopRefreshIfStuck();


        // Listener de conectividade Firebase: se perder ligação, parar o spinner e avisar
        FirebaseDatabase.getInstance().getReference(".info/connected")
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot s) {
                        Boolean ok = s.getValue(Boolean.class);
                        if (ok != null && !ok && swipeRefresh != null && swipeRefresh.isRefreshing()) {
                            swipeRefresh.setRefreshing(false);
                            toast("Sem ligação • a mostrar dados em cache (se existirem)");
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    @Override
    protected int currentNavItemId() {
        return R.id.nav_community;
    }

    /* ===================== CARREGAR POSTS ===================== */

    /**
     * Lê até 100 posts ordenados por createdAt e inverte para mostrar os mais recentes primeiro.
     */
    private void loadPostsOnce() {
        postsRef.orderByChild("createdAt").limitToLast(100)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        posts.clear();
                        for (DataSnapshot d : snap.getChildren()) {
                            Post p = d.getValue(Post.class);
                            if (p != null) { p.id = d.getKey(); posts.add(p); }
                        }
                        Collections.reverse(posts);
                        adapter.notifyDataSetChanged();
                        swipeRefresh.setRefreshing(false);
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {
                        toast("Erro: " + e.getMessage());
                        swipeRefresh.setRefreshing(false);
                    }
                });
    }

    /**
     * Caso o refresh fique “preso” (sem internet/tempo de resposta),
     * desliga o spinner após 4s e informa o utilizador.
     */
    private void stopRefreshIfStuck() {
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
                swipeRefresh.setRefreshing(false);
                toast("Sem internet — não foi possível atualizar agora");
            }
        }, 4000);
    }

    /* ===================== NOVA PUBLICAÇÃO ===================== */
    /**
     * Abre um BottomSheet com o formulário para novo post.
     * Ao publicar, grava no RTDB com createdAt=ServerValue.TIMESTAMP.
     */
    private void openNewPostSheet() {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_new_post, null, false);
        dlg.setContentView(v);

        EditText etText = v.findViewById(R.id.etText);
        MaterialSwitch swAnon = v.findViewById(R.id.switchAnon);
        Button btnSend = v.findViewById(R.id.btnSend);

        btnSend.setOnClickListener(view -> {
            String text = etText.getText() == null ? "" : etText.getText().toString().trim();
            if (TextUtils.isEmpty(text)) { etText.setError("Escreve algo"); return; }

            String author = swAnon.isChecked()
                    ? "Anónimo"
                    : (user.getDisplayName() != null && !user.getDisplayName().isEmpty()
                    ? user.getDisplayName() : "Utilizador");

            Map<String, Object> post = new HashMap<>();
            post.put("text", text);
            post.put("author", author);
            post.put("anonymous", swAnon.isChecked());
            post.put("uid", user.getUid());
            post.put("createdAt", ServerValue.TIMESTAMP);

            postsRef.push().setValue(post)
                    .addOnSuccessListener(x -> {
                        toast("Publicado!");
                        dlg.dismiss();
                        swipeRefresh.setRefreshing(true);
                        loadPostsOnce();
                        stopRefreshIfStuck();
                    })
                    .addOnFailureListener(e -> toast("Falhou publicar"));
        });

        dlg.show();
    }

    /* ===================== COMENTÁRIOS ===================== */
    /**
     * Abre um BottomSheet para a lista de comentários de um post.
     */
    private void openCommentsSheet(Post post) {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_comments, null, false);
        dlg.setContentView(v);

        TextView tvPostText = v.findViewById(R.id.tvPostText);
        RecyclerView rvComments = v.findViewById(R.id.rvComments);
        EditText etComment = v.findViewById(R.id.etComment);
        Button btnSend = v.findViewById(R.id.btnSendComment);

        tvPostText.setText(formatHeader(post));
        rvComments.setLayoutManager(new LinearLayoutManager(this));

        List<Comment> comments = new ArrayList<>();
        CommentAdapter cadapter = new CommentAdapter(comments);
        rvComments.setAdapter(cadapter);

        DatabaseReference commentsRef = postsRef.child(post.id).child("comments");

        commentsRef.orderByChild("createdAt")
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        comments.clear();
                        for (DataSnapshot d : snap.getChildren()) {
                            Comment c = d.getValue(Comment.class);
                            if (c != null) { c.id = d.getKey(); comments.add(c); }
                        }
                        cadapter.notifyDataSetChanged();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) { }
                });

        btnSend.setOnClickListener(view -> {
            String text = etComment.getText() == null ? "" : etComment.getText().toString().trim();
            if (text.isEmpty()) { etComment.setError("Escreve algo"); return; }

            Map<String, Object> c = new HashMap<>();
            c.put("text", text);
            c.put("author", user.getDisplayName() != null && !user.getDisplayName().isEmpty()
                    ? user.getDisplayName() : "Anónimo");
            c.put("createdAt", ServerValue.TIMESTAMP);

            commentsRef.push().setValue(c)
                    .addOnSuccessListener(x -> etComment.setText(""));
        });

        dlg.show();
    }

    /**
     * Formata o cabeçalho com autor e data.
     */
    private String formatHeader(Post p) {
        String who = p.author == null ? "Anónimo" : p.author;
        String when = "";
        try {
            if (p.createdAt instanceof Long) {
                long t = (Long) p.createdAt;
                when = " • " + new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                        .format(new Date(t));
            }
        } catch (Exception ignored) {}
        return who + ": " + p.text + when;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    /* ===================== MODELOS ===================== */
    public static class Post {
        public String id;
        public String text;
        public String author;
        public Boolean anonymous;
        public Object createdAt; // ServerValue.TIMESTAMP -> Long
        public String uid;
        public Post() {}
    }

    public static class Comment {
        public String id;
        public String text;
        public String author;
        public Object createdAt;
        public Comment() {}
    }

    /* ===================== ADAPTERS ===================== */

    private static class PostAdapter extends RecyclerView.Adapter<PostAdapter.VH> {
        interface OnPostClick { void onClick(Post p); }
        private final List<Post> data;
        private final OnPostClick onClick;
        PostAdapter(List<Post> d, OnPostClick c) { data = d; onClick = c; }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvAuthor, tvText, tvComments;
            VH(@NonNull View v) {
                super(v);
                tvAuthor = v.findViewById(R.id.tvAuthor);
                tvText   = v.findViewById(R.id.tvText);
                tvComments = v.findViewById(R.id.tvComments);
            }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull android.view.ViewGroup p, int vt) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_post, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int i) {
            Post p = data.get(i);
            h.tvAuthor.setText(p.author == null ? "Anónimo" : p.author);
            h.tvText.setText(p.text);
            h.tvComments.setOnClickListener(v -> onClick.onClick(p));
        }

        @Override public int getItemCount() { return data.size(); }
    }

}
