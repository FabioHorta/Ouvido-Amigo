package pt.ubi.pdm.projetofinal;

import android.app.Application;
import com.google.firebase.database.FirebaseDatabase;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Ativa a persistência offline do Firebase Realtime Database
        try {
            // Permite que os dados do Firebase sejam armazenados localmente e sincronizados quando houver ligação
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception ignored) {
            // Caso a persistência já tenha sido ativada anteriormente (o que causaria exceção), ignora o erro
        }

        // Agenda a sincronização periódica da aplicação
        SyncScheduler.schedulePeriodic(this);
    }
}
