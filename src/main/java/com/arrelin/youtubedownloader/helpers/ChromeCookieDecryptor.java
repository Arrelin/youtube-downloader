package com.arrelin.youtubedownloader.helpers;

import org.json.JSONObject;
import com.sun.jna.*;
import com.sun.jna.win32.W32APIOptions;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.sql.*;
import java.util.*;
import java.util.Base64;

public class ChromeCookieDecryptor {

    private static final int GCM_TAG_LENGTH = 16;

    // ==== DPAPI ====
    public interface Crypt32 extends Library {
        Crypt32 INSTANCE = Native.load("Crypt32", Crypt32.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean CryptUnprotectData(DATA_BLOB pDataIn, Pointer ppszDataDescr, DATA_BLOB pOptionalEntropy,
            Pointer pvReserved, Pointer pPromptStruct, int dwFlags, DATA_BLOB pDataOut);
    }

    public interface Kernel32 extends Library {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class, W32APIOptions.DEFAULT_OPTIONS);

        Pointer LocalFree(Pointer hMem);
    }

    @Structure.FieldOrder({"cbData", "pbData"})
    public static class DATA_BLOB extends Structure {
        public int cbData;
        public Pointer pbData;

        public DATA_BLOB() {
        }

        public DATA_BLOB(byte[] data) {
            cbData = data.length;
            pbData = new Memory(data.length);
            pbData.write(0, data, 0, data.length);
        }

        public byte[] getData() {
            return pbData.getByteArray(0, cbData);
        }
    }

    private static byte[] dpapiDecrypt(byte[] encrypted) {
        DATA_BLOB in = new DATA_BLOB(encrypted);
        DATA_BLOB out = new DATA_BLOB();

        if (!Crypt32.INSTANCE.CryptUnprotectData(in, null, null, null, null, 0, out)) {
            throw new RuntimeException("Не удалось расшифровать через DPAPI");
        }

        byte[] decrypted = out.getData();
        Kernel32.INSTANCE.LocalFree(out.pbData);
        return decrypted;
    }

    public static void extractYouTubeCookies() throws Exception {
        String userHome = System.getProperty("user.home");
        Path localStatePath = Paths.get(userHome, "AppData", "Local", "Google", "Chrome", "User Data", "Local State");

        // 1. Получаем master_key
        JSONObject json = new JSONObject(Files.readString(localStatePath));
        String encryptedKeyB64 = json.getJSONObject("os_crypt").getString("encrypted_key");
        byte[] encryptedKey = Base64.getDecoder().decode(encryptedKeyB64);
        byte[] trimmedKey = Arrays.copyOfRange(encryptedKey, 5, encryptedKey.length); // удалить "DPAPI" префикс
        byte[] masterKey = dpapiDecrypt(trimmedKey);

        // 2. Открываем базу cookies
        Path dbPath = findChromeCookiesDB(userHome);
        if (dbPath == null) throw new FileNotFoundException("Не найдена база cookies Chrome");

        Path copy = WindowsFileUtils.copyLockedFile(dbPath.toFile()).toPath();
        String dbUrl = "jdbc:sqlite:" + copy.toAbsolutePath().toString().replace("\\", "/");

        Map<String, String> cookies = new HashMap<>();

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            PreparedStatement stmt = conn.prepareStatement("SELECT host_key, name, encrypted_value FROM cookies WHERE host_key LIKE ?");
            stmt.setString(1, "%youtube.com");
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String host = rs.getString("host_key");
                String name = rs.getString("name");
                byte[] encrypted = rs.getBytes("encrypted_value");

                String value = decryptCookie(encrypted, masterKey);
                cookies.put(name, value);
            }
        }

        // 3. Сохраняем в cookies.txt
        File out = new File("cookies.txt");
        try (PrintWriter pw = new PrintWriter(out)) {
            for (Map.Entry<String, String> entry : cookies.entrySet()) {
                pw.println(".youtube.com\tTRUE\t/\tFALSE\t0\t" + entry.getKey() + "\t" + entry.getValue());
            }
        }

        System.out.println("✅ Cookies сохранены: " + out.getAbsolutePath());
    }

    private static Path findChromeCookiesDB(String userHome) {
        List<String> profiles = List.of("Default", "Profile 1", "Profile 2", "Profile 3");
        for (String profile : profiles) {
            Path path = Paths.get(userHome, "AppData", "Local", "Google", "Chrome", "User Data", profile, "Network", "Cookies");
            if (Files.exists(path)) return path;
        }
        return null;
    }

    private static String decryptCookie(byte[] encrypted, byte[] masterKey) throws GeneralSecurityException {
        // Новые куки начинаются с v10/v11
        if (encrypted == null || encrypted.length < 3) return "";

        String prefix = new String(encrypted, 0, 3, StandardCharsets.US_ASCII);
        if (!prefix.equals("v10") && !prefix.equals("v11")) {
            // fallback: возможно, старый формат — DPAPI
            return new String(dpapiDecrypt(encrypted), StandardCharsets.UTF_8);
        }

        byte[] nonce = Arrays.copyOfRange(encrypted, 3, 15);
        byte[] cipherBytes = Arrays.copyOfRange(encrypted, 15, encrypted.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec key = new SecretKeySpec(masterKey, "AES");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        byte[] plainText = cipher.doFinal(cipherBytes);

        return new String(plainText, StandardCharsets.UTF_8);
    }
}
