package ru.sibgatulinanton.os.cmd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class CompleteCmd {

    public static String executeCommand(String command) {
        StringBuilder output = new StringBuilder();

        try {
            // Устанавливаем UTF-8 в CMD перед выполнением команды
            String fullCommand = "chcp 65001 > nul && " + command;
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", fullCommand);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Теперь читаем в UTF-8
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            );

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            process.waitFor();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return output.toString();
    }

}
