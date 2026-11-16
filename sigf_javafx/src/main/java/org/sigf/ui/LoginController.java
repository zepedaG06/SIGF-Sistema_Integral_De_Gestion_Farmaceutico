package org.sigf.ui;
import org.sigf.app.*; import org.sigf.config.*; import org.sigf.security.*; import org.sigf.services.*; import org.sigf.ui.*; import org.sigf.domain.*;

import javafx.fxml.FXML; import javafx.scene.control.*; import javafx.stage.Stage; import javafx.concurrent.Task;
public class LoginController {
 private record LoginResult(Security.User user,boolean needsProfile){}
 @FXML private TextField username; @FXML private PasswordField password; @FXML private Label message; @FXML private Button login;
 private Stage stage; private Database db;
 public void init(Stage stage,Database db){this.stage=stage;this.db=db;}
 @FXML public void signIn(){String n=username.getText(),p=password.getText();login.setDisable(true);message.setText("Verificando credenciales…");Task<LoginResult> task=new Task<>(){protected LoginResult call()throws Exception{Security.User user=new Security(db).login(n,p);return new LoginResult(user,user.role().equals("ADMIN")&&new PharmacyProfileService(db).load().isEmpty());}};task.setOnSucceeded(e->{password.clear();LoginResult result=task.getValue();Runnable open=()->new Workspace(stage,db,result.user()).show();if(result.needsProfile())ProfileDialog.open(stage,db,result.user(),open);else open.run();});task.setOnFailed(e->{password.clear();message.setText("No se pudo iniciar sesión: "+task.getException().getMessage());login.setDisable(false);});Thread t=new Thread(task);t.setDaemon(true);t.start();}
}