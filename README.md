# 🧠 Ouvido Amigo

Aplicação Android desenvolvida em **Java** com foco no **bem-estar emocional** e **autoexpressão**, combinando ferramentas de escrita terapêutica, exercícios de relaxamento e uma comunidade anónima de apoio.

---

## 📱 Funcionalidades Principais

### 👤 Autenticação
- Login tradicional com e-mail e palavra-passe.  
- Login rápido com **Google / Firebase Auth**.  
- Registo e recuperação de conta integrados.

### 🏠 Ecrã Principal
- Seleção do **estado de humor diário**.  
- Sugestões personalizadas de bem-estar.  
- Acesso direto ao diário, exercícios e comunidade.

### 📔 Diário Pessoal
- Escrever e guardar reflexões diárias.  
- Consultar entradas antigas.  
- Exportação das entradas em **PDF**.  
- Sincronização automática com **Firebase Realtime Database**.

### 💪 Exercícios e Hábitos
- Lista de exercícios físicos e de respiração.  
- Áudios relaxantes integrados.  
- Lembretes automáticos com **notificações Android 13+**.  
- Suporte offline com **WorkManager** e sincronização em background.

### 🌍 Comunidade
- Publicações anónimas ou identificadas.  
- Comentários em tempo real com **Firebase RTDB**.  
- Interface fluida com *BottomSheetDialogs* e *Swipe Refresh*.  
- Cache offline e atualização dinâmica.

### 👥 Perfil do Utilizador
- Edição de nome, data de nascimento e fotografia.  
- Alteração de palavra-passe.  
- Gestão de sessão e logout seguro.

---

## 🧩 Estrutura do Projeto

```
app/
 ├─ java/
 │   └─ pt.ubi.pdm.projetofinal/
 │       ├─ App.java
 │       ├─ LoginActivity.java
 │       ├─ RegisterActivity.java
 │       ├─ MainActivity.java
 │       ├─ DiaryActivity.java
 │       ├─ WriteDiaryActivity.java
 │       ├─ ExercisesActivity.java
 │       ├─ CommunityActivity.java
 │       ├─ PerfilActivity.java
 │       ├─ ReminderReceiver.java
 │       ├─ SyncScheduler.java
 │       ├─ SyncWorker.java
 │       ├─ sqlite.java
 │       ├─ CommentAdapter.java
 │       └─ BaseBottomNavActivity.java
 │
 └─ res/layout/
     ├─ activity_main.xml
     ├─ activity_login.xml
     ├─ activity_register.xml
     ├─ activity_diary.xml
     ├─ activity_exercises.xml
     ├─ activity_perfil.xml
     ├─ dialog_new_post.xml
     ├─ dialog_comments.xml
     ├─ item_post.xml
     ├─ item_audio.xml
     └─ item_exercicio.xml
```

---

## ⚙️ Tecnologias e Bibliotecas

- **Linguagem:** Java 11  
- **Framework:** Android SDK / AndroidX  
- **UI:** Material Design 3 + ConstraintLayout  
- **Autenticação e Cloud:** Firebase Auth + Realtime Database  
- **Armazenamento local:** SQLite + SharedPreferences  
- **Sincronização:** WorkManager  
- **Notificações:** NotificationManagerCompat  
- **Compatibilidade:** Android 8.0 (API 26) ou superior  

---

## 🔔 Permissões Necessárias

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## 🛠️ Instalação e Execução

1. Clonar o repositório:
   ```bash
   git clone https://github.com/FabioHorta/Ouvido-Amigo.git
   ```
2. Abrir no **Android Studio** (versão Electric Eel ou superior).  
3. Fazer **Sync Project with Gradle Files**.  
4. Executar num emulador ou dispositivo físico (Android 8+).  
5. Aceitar a permissão de notificações quando pedida.

---

## 💡 Melhorias Futuras

- Estatísticas gráficas de humor e exercícios.  
- Sistema de mensagens privadas anónimas.  
- Tradução multilíngue (EN/PT).  
- Backup automático no Google Drive.  

---

## 👨‍💻 Autor

**Fábio Horta**  
Universidade da Beira Interior — Projeto Final de PDM  
📧 [fabio.horta@ubi.pt]  

---

## 🪪 Licença

Este projeto é distribuído sob a licença **MIT**.  
Consulta o ficheiro [LICENSE](LICENSE) para mais detalhes.
