package ru.sibgatulinanton.os.cmd;

import com.profesorfalken.jpowershell.PowerShell;
import com.profesorfalken.jpowershell.PowerShellNotAvailableException;
import com.profesorfalken.jpowershell.PowerShellResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

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

    public static String executeCommandPowerShell(String command) {
        StringBuilder output = new StringBuilder();

        try {
            // Добавляем форматирование для чистого вывода
            String fixedCommand = command + " | Format-Table -AutoSize | Out-String -Width 4096";

            byte[] commandBytes = fixedCommand.getBytes(StandardCharsets.UTF_16LE);
            String encodedCommand = Base64.getEncoder().encodeToString(commandBytes);

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-EncodedCommand", encodedCommand
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return output.toString();
    }

    public static String executePowerShell(String command) {
        try (PowerShell powerShell = PowerShell.openSession()) {
            //Execute a command in PowerShell session
            PowerShellResponse response = powerShell.executeCommand(command);

            //Print results
            return response.getCommandOutput();
        } catch (PowerShellNotAvailableException ex) {
            //Handle error when PowerShell is not available in the system
            //Maybe try in another way?
            throw new RuntimeException(ex);
        }
    }





    public static void main(String[] args) {
        String command = "Set-Content -Path 'C:\\Users\\89879\\Desktop\\deepseek-ai-agent\\auth-service\\pom.xml' -Value '<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n" +
                "         http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "\n" +
                "    <groupId>com.example</groupId>\n" +
                "    <artifactId>auth-service</artifactId>\n" +
                "    <version>1.0.0</version>\n" +
                "    <packaging>jar</packaging>\n" +
                "\n" +
                "    <parent>\n" +
                "        <groupId>org.springframework.boot</groupId>\n" +
                "        <artifactId>spring-boot-starter-parent</artifactId>\n" +
                "        <version>3.1.5</version>\n" +
                "        <relativePath/>\n" +
                "    </parent>\n" +
                "\n" +
                "    <properties>\n" +
                "        <java.version>17</java.version>\n" +
                "        <jjwt.version>0.12.3</jjwt.version>\n" +
                "    </properties>\n" +
                "\n" +
                "    <dependencies>\n" +
                "        <dependency>\n" +
                "            <groupId>org.springframework.boot</groupId>\n" +
                "            <artifactId>spring-boot-starter-web</artifactId>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>org.springframework.boot</groupId>\n" +
                "            <artifactId>spring-boot-starter-security</artifactId>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>org.springframework.boot</groupId>\n" +
                "            <artifactId>spring-boot-starter-data-jpa</artifactId>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>org.springframework.boot</groupId>\n" +
                "            <artifactId>spring-boot-starter-validation</artifactId>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>com.h2database</groupId>\n" +
                "            <artifactId>h2</artifactId>\n" +
                "            <scope>runtime</scope>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>io.webtoken</groupId>\n" +
                "            <artifactId>jjwt-api</artifactId>\n" +
                "            <version>${jjwt.version}</version>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>io.webtoken</groupId>\n" +
                "            <artifactId>jjwt-impl</artifactId>\n" +
                "            <version>${jjwt.version}</version>\n" +
                "            <scope>runtime</scope>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>io.webtoken</groupId>\n" +
                "            <artifactId>jjwt-jackson</artifactId>\n" +
                "            <version>${jjwt.version}</version>\n" +
                "            <scope>runtime</scope>\n" +
                "        </dependency>\n" +
                "    </dependencies>\n" +
                "\n" +
                "    <build>\n" +
                "        <plugins>\n" +
                "            <plugin>\n" +
                "                <groupId>org.springframework.boot</groupId>\n" +
                "                <artifactId>spring-boot-maven-plugin</artifactId>\n" +
                "            </plugin>\n" +
                "        </plugins>\n" +
                "    </build>'\n";

        // Используем исправленный метод
        String result = executePowerShell(command);

        // Или с Base64 кодированием
        // String result = executeCommandPowerShellBase64(command);

        System.out.println("Result: " + result);
    }

}
