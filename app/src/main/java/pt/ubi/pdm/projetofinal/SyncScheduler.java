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

public class SyncScheduler {

    private static final String PERIODIC_NAME = "oa_sync_periodic";

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
