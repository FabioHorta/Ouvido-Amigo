package pt.ubi.pdm.projetofinal;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, "reminders")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Está na hora!")
                .setContentText("Faz um exercício rápido 💪")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        // Verifica a permissão antes de enviar a notificação
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        {
            NotificationManagerCompat.from(ctx).notify(3001, b.build());
        }
    }
}
