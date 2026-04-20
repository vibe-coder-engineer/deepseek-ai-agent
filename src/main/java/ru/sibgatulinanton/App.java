package ru.sibgatulinanton;

import org.json.JSONArray;
import org.json.JSONObject;
import ru.sibgatulinanton.deepseek.BrowserDriverManager;
import ru.sibgatulinanton.deepseek.DeepSeekChatPage;
import ru.sibgatulinanton.lang.Language;
import ru.sibgatulinanton.os.OSUtils;
import ru.sibgatulinanton.prompts.PromptLoader;

import java.time.Duration;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) {
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
                .replaceAll("\\{TASK}", "Напиши сервис на java spring boot с авторизацией и регистрацией")
                .replaceAll("\\{OS}", OSUtils.getOperatingSystemType()
                        .name());

        String response = deepSeekPage.askDeepSeek(prompt);

        JSONObject jsonObject = new JSONObject(response);

        JSONArray operations = jsonObject.getJSONArray("operations");
        for (int i = 0; i < operations.length(); i++) {
            JSONObject operation = operations.getJSONObject(i);
            String type = operation.getString("type");
            String data = operation.getString("data");
            String content = operation.has("content") ? operation.getString("content") : null;

            if ("END".equals(type)) {
                System.out.println(content);
            } else if ("CMD".equals(type)) {

            } else if ("CMD_WAIT".equals(type)) {

            } else if ("TEXT".equals(type)) {
                System.out.println(data);
            }
        }

        manager.driverWait(100);

        manager.quit();
    }
}
