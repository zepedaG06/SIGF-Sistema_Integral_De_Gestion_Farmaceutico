package org.sigf.ui;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.sigf.config.Database;
import org.sigf.security.Security;
import org.sigf.services.PharmacyProfileService;

public class LoginController {
    private record LoginResult(Security.User user, boolean needsProfile) {}

    @FXML private TextField username;
    @FXML private PasswordField password;
    @FXML private Label message;
    @FXML private Button login;
    private Stage stage;
    private Database db;

    public void init(Stage stage, Database db) {
        this.stage = stage;
        this.db = db;
    }

    @FXML
    public void signIn() {
        String n = username.getText(), p = password.getText();
        login.setDisable(true);
        message.setText("Verificando credenciales…");
        Task<LoginResult> task =
                new Task<>() {
                    protected LoginResult call() throws Exception {
                        Security.User user = new Security(db).login(n, p);
                        return new LoginResult(
                                user, new PharmacyProfileService(db).load().isEmpty());
                    }
                };
        task.setOnSucceeded(
                e -> {
                    password.clear();
                    LoginResult result = task.getValue();
                    Runnable open = () -> new Workspace(stage, db, result.user()).show();
                    if (result.needsProfile()) {
                        if (!result.user().role().equals("ADMIN")) {
                            message.setText(
                                    "Un administrador debe completar primero la configuración de la farmacia.");
                            login.setDisable(false);
                        } else {
                            ProfileDialog.openInitial(
                                    stage,
                                    db,
                                    result.user(),
                                    open,
                                    () -> {
                                        login.setDisable(false);
                                        message.setText(
                                                "No se pudo cargar la configuración inicial.");
                                    });
                        }
                    } else {
                        open.run();
                    }
                });
        task.setOnFailed(
                e -> {
                    password.clear();
                    message.setText(
                            "No se pudo iniciar sesión: " + task.getException().getMessage());
                    login.setDisable(false);
                });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }
}
