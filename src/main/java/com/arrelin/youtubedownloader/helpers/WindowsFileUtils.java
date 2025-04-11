package com.arrelin.youtubedownloader.helpers;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;

import java.io.File;

public class WindowsFileUtils {

    public interface Kernel32 extends Library {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        boolean CopyFileExW(WString existingFileName, WString newFileName,
            Pointer lpProgressRoutine, Pointer reserved,
            Pointer cancel, int copyFlags);

        int GetLastError();
    }

    public static File copyLockedFile(File source) throws Exception {
        File temp = File.createTempFile("cookies", ".db");

        System.out.println("🔍 Проверка пути:");
        System.out.println("Path to copy: " + source.getAbsolutePath());
        System.out.println("Exists? " + source.exists());

        boolean success = Kernel32.INSTANCE.CopyFileExW(
            new WString(source.getAbsolutePath()),
            new WString(temp.getAbsolutePath()),
            null,
            null,
            null,
            0
        );

        if (!success) {
            int errorCode = Kernel32.INSTANCE.GetLastError();
            throw new RuntimeException("❌ Не удалось скопировать файл через CopyFileEx (ошибка Windows: " + errorCode + ")");
        }

        return temp;
    }
}
