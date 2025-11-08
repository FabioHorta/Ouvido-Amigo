package pt.ubi.pdm.projetofinal;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    // Lista de comentários a apresentar
    private final List<CommunityActivity.Comment> comments;

    // Construtor: recebe a lista inicial de comentários
    public CommentAdapter(List<CommunityActivity.Comment> comments) {
        this.comments = comments;
    }


    /**
     * Cria a View de cada  comentário.
     * É chamado apenas quando o RecyclerView precisa de uma nova ViewHolder.
     */
    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_2, parent, false);
        return new CommentViewHolder(v);
    }

    /**
     * Liga os dados (autor, texto, etc.) ao ViewHolder.
     * É chamado para cada item visível da lista.
     */

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        CommunityActivity.Comment c = comments.get(position);
        holder.author.setText(c.author == null ? "Anónimo" : c.author);
        holder.text.setText(c.text);
    }

    /**
     * Devolve o número total de comentarios a mostrar.
     * O RecyclerView usa isto para saber quantas linhas desenhar.
     */
    @Override
    public int getItemCount() {
        return comments.size();
    }


    /**
     * Classe interna ViewHolder:
     * Guarda as referências das views (TextViews) de cada comentario,para não estar sempre a fazer findViewById().
     */
    public static class CommentViewHolder extends RecyclerView.ViewHolder {
        TextView author, text;
        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            author = itemView.findViewById(android.R.id.text1);
            text = itemView.findViewById(android.R.id.text2);
        }
    }
}
