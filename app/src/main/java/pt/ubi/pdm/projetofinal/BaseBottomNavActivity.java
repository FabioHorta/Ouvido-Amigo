package pt.ubi.pdm.projetofinal;

import android.content.Intent;
import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

//define o comportamento comum (gestão do BottomNavigationView); mas não sabe qual é o item do menu que cada Activity representa. Por isso é uma abstract class
public abstract class BaseBottomNavActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavRef;
    private boolean isSyncingSelection = false;

    protected abstract @IdRes int currentNavItemId();
    protected void attachBottomNav(BottomNavigationView bottomNav) {
        if (bottomNav == null) return;
        bottomNavRef = bottomNav;

        bottomNav.setOnItemReselectedListener(item -> {});

        bottomNav.setOnItemSelectedListener(item -> {
            if (isSyncingSelection) return true;

            int current = currentNavItemId();
            int id = item.getItemId();
            if (id == current) return true;

            Intent i = null;
            if (id == R.id.nav_home)          i = new Intent(this, MainActivity.class);
            else if (id == R.id.nav_diary)    i = new Intent(this, DiaryActivity.class);
            else if (id == R.id.nav_exercises) i = new Intent(this, ExercisesActivity.class);
            else if (id == R.id.nav_community) i = new Intent(this, CommunityActivity.class);

            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(i);
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });

        syncBottomSelection();
    }

    private void syncBottomSelection() {
        if (bottomNavRef == null) return;
        isSyncingSelection = true;
        bottomNavRef.setSelectedItemId(currentNavItemId());
        isSyncingSelection = false;
    }


    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (Reminders.hasPostNotifPermission(this)) {
            Reminders.scheduleInactivityAfterHours(this, 5);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Reminders.createChannel(this);
        // Se faltar permissão em Android 13+, pede na Activity concreta (Main/Exercises), senão:
        if (Reminders.hasPostNotifPermission(this)) {
            Reminders.scheduleInactivityAfterHours(this, 5); // 5 horas
        }
    }

}
