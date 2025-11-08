package pt.ubi.pdm.projetofinal;

import android.app.Application;
import com.google.firebase.database.FirebaseDatabase;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Ativa cache/persistência offline do Firebase Realtime Database.
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception ignored) {
            // Se já tiver sido inicializado noutro local, ignoramos para não forçar a app a parar.
        }
        // Agenda a sincronização periódica da app (envio/receção de dados).
        SyncScheduler.schedulePeriodic(this);
    }
}
