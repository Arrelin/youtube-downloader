package com.arrelin.youtubedownloader.helpers;

import com.sun.jna.*;
import com.sun.jna.Structure;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DPAPI {

    // === WINDOWS API ===
    public interface Crypt32 extends Library {
        Crypt32 INSTANCE = Native.load("Crypt32", Crypt32.class);

        boolean CryptUnprotectData(DATA_BLOB pDataIn, Pointer ppszDataDescr, DATA_BLOB pOptionalEntropy,
            Pointer pvReserved, Pointer pPromptStruct, int dwFlags, DATA_BLOB pDataOut);
    }

    public interface Kernel32 extends Library {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);
        Pointer LocalFree(Pointer hMem);
    }

    @Structure.FieldOrder({"cbData", "pbData"})
    public static class DATA_BLOB extends Structure {
        public int cbData;
        public Pointer pbData;

        public DATA_BLOB() {}

        public DATA_BLOB(byte[] data) {
            cbData = data.length;
            pbData = new Memory(data.length);
            pbData.write(0, data, 0, data.length);
        }

        public byte[] getData() {
            return pbData.getByteArray(0, cbData);
        }
    }

    public static String decrypt(byte[] encryptedData) {
        DATA_BLOB in = new DATA_BLOB(encryptedData);
        DATA_BLOB out = new DATA_BLOB();

        boolean result = Crypt32.INSTANCE.CryptUnprotectData(in, null, null, null, null, 0, out);
        if (!result) {
            throw new RuntimeException("Не удалось расшифровать cookie");
        }

        byte[] decryptedBytes = out.getData();
        Kernel32.INSTANCE.LocalFree(out.pbData);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    // === ОСНОВНАЯ ЛОГИКА ===
    public static void extractYouTubeCookies() throws Exception {
        String userHome = System.getProperty("user.home");
        File chromeDir = new File(userHome + "\\AppData\\Local\\Google\\Chrome\\User Data");
        File cookiesFile = null;

        for (String profile : List.of("Default", "Profile 1", "Profile 2", "Profile 3")) {
            File candidate = new File(chromeDir, profile + "\\Network\\Cookies");
            if (candidate.exists()) {
                cookiesFile = candidate;
                break;
            }
        }

        if (cookiesFile == null || !cookiesFile.exists()) {
            throw new FileNotFoundException("❌ Не удалось найти файл Cookies ни в одном профиле Chrome.");
        }

        File unlocked = WindowsFileUtils.copyLockedFile(cookiesFile);
        String dbUrl = "jdbc:sqlite:" + unlocked.getAbsolutePath().replace("\\", "/");

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT host_key, name, encrypted_value FROM cookies WHERE host_key LIKE ?");
            stmt.setString(1, "%youtube.com");

            ResultSet rs = stmt.executeQuery();
            Map<String, String> cookies = new HashMap<>();

            while (rs.next()) {
                String name = rs.getString("name");
                byte[] encrypted = rs.getBytes("encrypted_value");

                String value = decrypt(encrypted);
                cookies.put(name, value);
            }

            File out = new File("cookies.txt");
            try (PrintWriter pw = new PrintWriter(out)) {
                for (Map.Entry<String, String> entry : cookies.entrySet()) {
                    pw.println(".youtube.com\tTRUE\t/\tFALSE\t0\t" + entry.getKey() + "\t" + entry.getValue());
                }
            }

            System.out.println("✅ Cookies сохранены в " + out.getAbsolutePath());
        }
    }
}
