package ru.sibgatulinanton.deepseek;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import ru.sibgatulinanton.lang.Language;
import ru.sibgatulinanton.os.OSUtils;
import ru.sibgatulinanton.prompts.PromptLoader;

import java.nio.file.Paths;
import java.time.Duration;

public class BrowserDriverManager {

    public static final String CHAT_DEEPSEEK_LINK = "https://chat.deepseek.com";
    public static final int WAIT_DRIVER_SECONDS = 30;

    private WebDriver driver;
    private WebDriverWait wait;

    public BrowserDriverManager() {
        init();
    }

    private void init() {
        // Указываем путь к драйверу
        System.setProperty("webdriver.edge.driver", "resources/drivers/msedgedriver.exe");

        EdgeOptions options = new EdgeOptions();

        // ===== КЛЮЧЕВОЕ РЕШЕНИЕ: сохраняем профиль между запусками =====
        // Создаем папку для профиля, если её нет
        String userDir = System.getProperty("user.dir");
        String profilePath = Paths.get(userDir, "edge_profile_deepseek").toString();

        // Используем постоянный профиль
        options.addArguments("user-data-dir=" + profilePath);

        // Опционально: конкретный профиль внутри папки
        options.addArguments("--profile-directory=Default");

        // Дополнительные опции для стабильности
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-gpu");

        // Отключаем лишние уведомления
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");


        driver = new EdgeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_DRIVER_SECONDS));

    }

    public void openDeepSeek() {
        openUrl(CHAT_DEEPSEEK_LINK);
    }

    public void openUrl(String url) {
        if (driver == null) {
            throw new IllegalStateException("Driver not initialized");
        }

        driver.get(url);
    }

    public void quit() {
        if (driver == null) {
            throw new IllegalStateException("Driver not initialized");
        }
        driver.quit();
    }

    public WebDriver getDriver() {
        return driver;
    }

    public WebDriverWait getWait() {
        return wait;
    }

    public void driverWait(int seconds) {
        getWait().withTimeout(Duration.ofSeconds(seconds));
    }

    public static void main(String[] args) {
        PromptLoader promptLoader = new PromptLoader();


        String prompt = promptLoader.getPrompt(Language.RU,
                        "first_prompt")
                .replaceAll("\\{TASK}", "Напиши сервис на java который будет проверять лицензии по приватному ключу")
                .replaceAll("\\{OS}", OSUtils.getOperatingSystemType()
                        .name());
        System.out.println(prompt);
    }

}