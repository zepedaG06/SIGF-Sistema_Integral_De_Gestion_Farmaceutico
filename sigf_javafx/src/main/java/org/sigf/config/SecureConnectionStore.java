package org.sigf.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

/** La contraseña se protege con DPAPI, Keychain o Secret Service del usuario. */
public final class SecureConnectionStore {
    private final Preferences pref;
    private String activeAccount = ACCOUNT;
    private String protectedOverride;

    public SecureConnectionStore() {
        this(Preferences.userRoot().node("org/sigf/javafx/connection"));
    }

    public SecureConnectionStore(Preferences pref) {
        this.pref = pref;
    }

    private static final String SERVICE = "org.sigf.javafx.database", ACCOUNT = "sigf-postgres";

    public record ConnectionInfo(String url, String user, String password) {}

    private String[] metadata() {
        String bundle = pref.get("connection", null);
        if (bundle != null) {
            String[] fields = bundle.split("\\|", -1);
            if (fields.length != 4) {
                throw new IllegalStateException("Configuración guardada inválida");
            }
            for (int i = 0; i < fields.length; i++) {
                fields[i] =
                        new String(Base64.getDecoder().decode(fields[i]), StandardCharsets.UTF_8);
            }
            activeAccount = fields[2];
            protectedOverride = fields[3];
            return fields;
        }
        activeAccount = ACCOUNT;
        protectedOverride = null;
        return new String[] {
            pref.get("url", ""), pref.get("user", ""), ACCOUNT, pref.get("protected", "")
        };
    }

    private static String bundle(String[] fields) {
        return Arrays.stream(fields)
                .map(v -> Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8)))
                .collect(java.util.stream.Collectors.joining("|"));
    }

    public synchronized Optional<ConnectionInfo> load() throws Exception {
        String[] fields = metadata();
        if (fields[0].isEmpty() || fields[1].isEmpty()) {
            return Optional.empty();
        }
        String secret = readSecret();
        return secret == null || secret.isEmpty()
                ? Optional.empty()
                : Optional.of(new ConnectionInfo(fields[0], fields[1], secret));
    }

    public synchronized void save(String url, String user, String password) throws Exception {
        if (!url.matches("jdbc:postgresql://[A-Za-z0-9.-]+:[0-9]{1,5}/sigf_javafx_db")
                || user.isBlank()
                || password.isEmpty()) {
            throw new IllegalArgumentException("Conexión inválida");
        }
        String[] old = metadata();
        String previous = old[0].isEmpty() ? null : bundle(old);
        if (previous != null) {
            pref.put("connection", previous);
            pref.flush();
        }
        activeAccount = ACCOUNT + "-" + UUID.randomUUID();
        protectedOverride = null;
        try {
            writeSecret(password);
            String encrypted = os().contains("win") ? pref.get("protected", "") : "";
            pref.put("connection", bundle(new String[] {url, user, activeAccount, encrypted}));
            pref.flush();
        } catch (Exception failure) {
            try {
                clearSecret();
                if (previous == null) {
                    pref.remove("connection");
                } else {
                    pref.put("connection", previous);
                }
                pref.flush();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        pref.remove("url");
        pref.remove("user");
        pref.remove("protected");
        pref.flush();
        if (!old[0].isEmpty() && !os().contains("win")) {
            String current = activeAccount;
            activeAccount = old[2];
            try {
                clearSecret();
            } catch (Exception ignored) {
                /* Retain the old protected item if the OS refuses cleanup. */
            } finally {
                activeAccount = current;
            }
        }
    }

    public synchronized void clear() throws Exception {
        String[] old = metadata();
        if (!old[0].isEmpty()) {
            clearSecret();
        }
        pref.remove("connection");
        pref.remove("url");
        pref.remove("user");
        pref.remove("protected");
        pref.flush();
    }

    private static String os() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }

    private String readSecret() throws Exception {
        if (os().contains("win")) {
            String encrypted =
                    protectedOverride != null ? protectedOverride : pref.get("protected", null);
            if (encrypted == null) {
                return null;
            }
            String result =
                    process(
                            List.of(
                                    "powershell.exe",
                                    "-NoProfile",
                                    "-NonInteractive",
                                    "-Command",
                                    "$ErrorActionPreference='Stop'; Add-Type -AssemblyName System.Security; $d=[Convert]::FromBase64String([Console]::In.ReadLine()); $p=[Security.Cryptography.ProtectedData]::Unprotect($d,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser); [Console]::Out.Write([Convert]::ToBase64String($p))"),
                            encrypted);
            return new String(Base64.getDecoder().decode(result.trim()), StandardCharsets.UTF_8);
        }
        if (os().contains("mac")) {
            return withoutFinalNewline(
                    process(
                            List.of(
                                    "security",
                                    "find-generic-password",
                                    "-s",
                                    SERVICE,
                                    "-a",
                                    activeAccount,
                                    "-w"),
                            null));
        }
        if (os().contains("linux")) {
            return withoutFinalNewline(
                    process(
                            List.of(
                                    "secret-tool",
                                    "lookup",
                                    "service",
                                    SERVICE,
                                    "account",
                                    activeAccount),
                            null));
        }
        throw new IllegalStateException("Almacén seguro no disponible en este sistema");
    }

    private void writeSecret(String password) throws Exception {
        if (password.indexOf('\n') >= 0 || password.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("La contraseña no puede contener saltos de línea");
        }
        if (os().contains("win")) {
            String input =
                    Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
            String result =
                    process(
                            List.of(
                                    "powershell.exe",
                                    "-NoProfile",
                                    "-NonInteractive",
                                    "-Command",
                                    "$ErrorActionPreference='Stop'; Add-Type -AssemblyName System.Security; $p=[Convert]::FromBase64String([Console]::In.ReadLine()); $d=[Security.Cryptography.ProtectedData]::Protect($p,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser); [Console]::Out.Write([Convert]::ToBase64String($d))"),
                            input);
            pref.put("protected", result.trim());
            return;
        }
        if (os().contains("mac")) {
            String escaped = password.replace("\\", "\\\\").replace("\"", "\\\"");
            process(
                    List.of("security", "-i"),
                    "add-generic-password -U -s "
                            + SERVICE
                            + " -a "
                            + activeAccount
                            + " -w \""
                            + escaped
                            + "\"");
            if (!password.equals(readSecret())) {
                throw new IllegalStateException("No se pudo guardar la contraseña en Keychain");
            }
            return;
        }
        if (os().contains("linux")) {
            process(
                    List.of(
                            "secret-tool",
                            "store",
                            "--label=SIGF PostgreSQL",
                            "service",
                            SERVICE,
                            "account",
                            activeAccount),
                    password);
            return;
        }
        throw new IllegalStateException("Almacén seguro no disponible en este sistema");
    }

    private void clearSecret() throws Exception {
        if (os().contains("win")) {
            return;
        }
        if (os().contains("mac")) {
            process(
                    List.of(
                            "security",
                            "delete-generic-password",
                            "-s",
                            SERVICE,
                            "-a",
                            activeAccount),
                    null);
            return;
        }
        if (os().contains("linux")) {
            process(
                    List.of("secret-tool", "clear", "service", SERVICE, "account", activeAccount),
                    null);
            return;
        }
        throw new IllegalStateException("Almacén seguro no disponible");
    }

    private static String withoutFinalNewline(String value) {
        if (value.endsWith("\r\n")) {
            return value.substring(0, value.length() - 2);
        }
        if (value.endsWith("\n")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String process(List<String> args, String input) throws Exception {
        Process p = new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try (var out = p.getOutputStream()) {
            if (input != null) {
                out.write((input + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        if (!p.waitFor(15, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("El almacén seguro no respondió");
        }
        byte[] output = p.getInputStream().readAllBytes();
        if (p.exitValue() != 0) {
            throw new IllegalStateException("No se pudo acceder al almacén seguro del sistema");
        }
        return new String(output, StandardCharsets.UTF_8);
    }
}
