package pt.ubi.pdm.projetofinal;


import android.content.Intent;
import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public abstract class BaseBottomNavActivity extends AppCompatActivity {
    private BottomNavigationView bottomNavRef;
    private boolean isSyncingSelection = false;

    //Método abstrato que cada Activity filha deve implementar para indicar qual o item atualmente selecionado na barra de navegação.
    protected abstract @IdRes int currentNavItemId();

    protected void attachBottomNav(BottomNavigationView bottomNav) {
        if (bottomNav == null) return; // Se for nulo, não faz nada
        bottomNavRef = bottomNav;

        // Ignora quando o utilizador clica novamente no item já selecionado
        bottomNav.setOnItemReselectedListener(item -> {});

        // Listener para tratar seleção de novos itens na barra de navegação
        bottomNav.setOnItemSelectedListener(item -> {
            // Evita loop se a seleção estiver a ser feita programaticamente
            if (isSyncingSelection) return true;

            int current = currentNavItemId(); // ID do item atualmente ativo
            int id = item.getItemId();        // ID do item selecionado
            if (id == current) return true;   // Se for o mesmo, não faz nada

            // Cria o Intent para navegar para a Activity correspondente
            Intent i = null;
            if (id == R.id.nav_home)            i = new Intent(this, MainActivity.class);
            else if (id == R.id.nav_diary)      i = new Intent(this, DiaryActivity.class);
            else if (id == R.id.nav_exercises)  i = new Intent(this, ExercisesActivity.class);
            else if (id == R.id.nav_community)  i = new Intent(this, CommunityActivity.class);

            if (i != null) {
                // Evita criar múltiplas instâncias da mesma Activity
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(i);              // Inicia a nova Activity
                overridePendingTransition(0, 0); // Sem animação de transição
                finish();                      // Fecha a Activity atual
                return true;
            }
            return false; // Se não houver correspondência, não faz nada
        });

        // Garante que o item visualmente selecionado corresponde à Activity atual
        syncBottomSelection();
    }

    //Atualiza a seleção visual da barra de navegação inferior para refletir corretamente a Activity atual.
    private void syncBottomSelection() {
        if (bottomNavRef == null) return;
        isSyncingSelection = true; // Ativa flag para evitar loops
        bottomNavRef.setSelectedItemId(currentNavItemId()); // Seleciona item correto
        isSyncingSelection = false; // Desativa flag
    }


    //Lembra o utilizador que para utilizar aquela funcionalidade terá de aceitar permissões caso não seja cumprido reagenda o alerta daqui a 5 horas
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (Reminders.hasPostNotifPermission(this)) {
            Reminders.scheduleInactivityAfterHours(this, 5); // Reagenda para 5 horas
        }
    }

    //Garante que existe um canal para as notificações existe, e reagenda  alerta
    @Override
    protected void onResume() {
        super.onResume();
        Reminders.createChannel(this); // Garante que o canal de notificações existe
        if (Reminders.hasPostNotifPermission(this)) {
            Reminders.scheduleInactivityAfterHours(this, 5); // Reagenda alerta
        }
    }
}