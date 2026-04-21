package ru.sibgatulinanton;

import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import ru.sibgatulinanton.deepseek.BrowserDriverManager;
import ru.sibgatulinanton.deepseek.DeepSeekChatPage;
import ru.sibgatulinanton.lang.Language;
import ru.sibgatulinanton.os.OSType;
import ru.sibgatulinanton.os.OSUtils;
import ru.sibgatulinanton.os.cmd.CompleteCmd;
import ru.sibgatulinanton.prompts.PromptLoader;

import java.time.Duration;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) {

        String currentDir = System.getProperty("user.dir");
        OSType osType = OSUtils.getOperatingSystemType();

        PromptLoader promptLoader = new PromptLoader();

        BrowserDriverManager manager = new BrowserDriverManager();
        manager.openDeepSeek();

        DeepSeekChatPage deepSeekPage = new DeepSeekChatPage(manager);

        if (deepSeekPage.isUserLoggedIn()) {
            System.out.println("✅ Пользователь авторизован в DeepSeek!");


        } else {
            System.out.println("⚠️ Пользователь не авторизован");

        }

        System.out.println("\n📌 ВАЖНО: При первом запуске авторизуйтесь вручную!");
        System.out.println("📌 В следующий раз вы уже будете авторизованы автоматически!\n");


        manager.driverWait(30);

        String prompt = promptLoader.getPrompt(Language.RU,
                        "first_prompt")
                .replaceAll("\\{TASK}", "Напиши змека на html css js")
                .replaceAll("\\{OS}", osType.name())
                .replaceAll("\\{WORKSPACE}", currentDir)
                .replaceAll("\\{CMD}", OSType.WINDOWS.equals(osType) ? "PowerShell" : "bash");

        boolean isEnd = false;
        boolean promptIsExist = false;
        while (!isEnd) {
            promptIsExist = false;
            String response = deepSeekPage.askDeepSeek(prompt)
                    .replace("json", "")
                    .replace("Копировать", "")
                    .replace("Скачать", "")
                    .trim();

            JSONObject jsonObject = new JSONObject(response);

            JSONArray operations = jsonObject.getJSONArray("operations");
            for (int i = 0; i < operations.length(); i++) {
                JSONObject operation = operations.getJSONObject(i);
                String type = operation.getString("type");
                String data = operation.getString("data");
                String content = operation.has("content") && !operation.isNull("content") ? StringEscapeUtils.escapeEcmaScript(operation.getString("content")) : null;

                if ("END".equals(type)) {
                    isEnd = true;
                    System.out.println(content);
                } else if ("CMD".equals(type)) {
                    if (content != null) {
                        data = data.replace("%s", content);
                    }

                    System.out.println(data);
                    String cmd =
                            OSType.WINDOWS.equals(osType) ?
                                    CompleteCmd.executePowerShell(data + "\n") :
                                    CompleteCmd.executeCommand(data);
                    System.out.println(cmd);

                } else if ("CMD_WAIT".equals(type)) {
                    if (content != null) {
                        data = data.replace("%s", content);
                    }

                    System.out.println(data);
                    String cmd =
                            OSType.WINDOWS.equals(osType) ?
                                    CompleteCmd.executePowerShell(data + "\n") :
                                    CompleteCmd.executeCommand(data);
                    System.out.println(cmd);

                    prompt = cmd.trim().isEmpty() ? "продолжай" : cmd;
                    promptIsExist = true;
                } else if ("TEXT".equals(type)) {
                    System.out.println(data);
                }
            }
            if (!promptIsExist) {
                prompt = "продолжай";
            }
        }
        manager.driverWait(100);

        manager.quit();
    }
}
