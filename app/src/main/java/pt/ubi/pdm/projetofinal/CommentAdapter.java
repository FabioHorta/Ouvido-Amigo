package pt.ubi.pdm.projetofinal;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

//Adapter responsável por apresentar a lista de comentários da comunidade numa RecyclerView.
public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    // Lista de comentários a ser apresentada
    private final List<CommunityActivity.Comment> comments;

    //Este construtor serve para inicializar o adaptador com a lista de comentários que será usada para preencher a RecyclerView.
    // Sem esta lista, o Adapter não teria dados para mostrar.
    public CommentAdapter(@NonNull List<CommunityActivity.Comment> comments) {
        this.comments = comments;
    }


    //Cria a visualização (View) de uma linha da lista de comentários.
    //Usa um layout padrão do Android com duas linhas de texto (text1 e text2).
    //Devolve um ViewHolder que guarda referências a essas duas TextViews.
    //É chamado automaticamente pelo RecyclerView sempre que precisa de mostrar um novo item na lista.
    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_2, parent, false);
        return new CommentViewHolder(v);
    }

    //Preenche os dados de uma posição da lista com base na posição atual.
    //Vai buscar o comentário correspondente à posição na lista.
    //Define o nome do autor (ou "Anónimo" se for nulo).
    //Define o texto do comentário (ou uma string vazia se for nulo).

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        // Obtém o comentário na posição atual
        CommunityActivity.Comment c = comments.get(position);

        // Define o autor (ou "Anónimo" se for nulo)
        holder.author.setText(c.author == null ? "Anónimo" : c.author);

        // Define o texto do comentário (ou vazio se for nulo)
        holder.text.setText(c.text == null ? "" : c.text);
    }


    //Cria um contador para registar quantos comentários existe
    @Override
    public int getItemCount() {
        return comments != null ? comments.size() : 0;
    }



    public static class CommentViewHolder extends RecyclerView.ViewHolder {
        final TextView author; // TextView para o nome do autor
        final TextView text;   // TextView para o conteúdo do comentário

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            // Associa os elementos do layout às variáveis
            author = itemView.findViewById(android.R.id.text1);
            text   = itemView.findViewById(android.R.id.text2);
        }
    }
}
