package pt.ubi.pdm.projetofinal;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;


// Classe responsável por agendar tarefas de sincronização com a cloud.
// Utiliza a API WorkManager para garantir execução mesmo após reinícios ou encerramento da app.

public class SyncScheduler {

    private static final String PERIODIC_NAME = "oa_sync_periodic";


    // Agenda uma tarefa periódica (a cada 15 minutos) para sincronizar dados com a cloud.
    // - Requer ligação à internet.
    // - Usa WorkManager com política KEEP para evitar múltiplas tarefas duplicadas.

    public static void schedulePeriodic(Context ctx) {
        Constraints c = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest periodic =
                new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
                        .setConstraints(c)
                        .build();

        WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, periodic);
    }


    // Executa uma tarefa de sincronização única imediatamente.
    // - Requer ligação à internet.
    // - Usa WorkManager com política REPLACE para garantir que substitui qualquer tarefa anterior pendente.

    public static void kickNow(Context ctx) {
        Constraints c = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest once =
                new OneTimeWorkRequest.Builder(SyncWorker.class)
                        .setConstraints(c)
                        .build();

        WorkManager.getInstance(ctx)
                .enqueueUniqueWork("oa_sync_once", ExistingWorkPolicy.REPLACE, once);
    }
}
