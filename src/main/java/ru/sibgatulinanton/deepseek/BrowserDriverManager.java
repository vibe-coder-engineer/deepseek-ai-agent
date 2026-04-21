package ru.sibgatulinanton.deepseek;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import ru.sibgatulinanton.lang.Language;
import ru.sibgatulinanton.os.OSUtils;
import ru.sibgatulinanton.prompts.PromptLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
        System.setProperty("webdriver.edge.driver", "resources/drivers/msedgedriver.exe");

        EdgeOptions options = new EdgeOptions();

        String profilePath = resolvePersistentProfilePath().toString();
        options.addArguments("user-data-dir=" + profilePath);
        options.addArguments("--profile-directory=Default");

        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-gpu");

        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");

        driver = new EdgeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_DRIVER_SECONDS));
    }

    private Path resolvePersistentProfilePath() {
        Path targetProfilePath = Paths.get(System.getProperty("user.home"), ".vibecoder", "edge_profile_deepseek");
        Path legacyProfilePath = Paths.get(System.getProperty("user.dir"), "edge_profile_deepseek");

        try {
            Files.createDirectories(targetProfilePath.getParent());

            if (Files.exists(legacyProfilePath) && !Files.exists(targetProfilePath)) {
                Files.move(legacyProfilePath, targetProfilePath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[INFO] Edge profile moved: " + legacyProfilePath + " -> " + targetProfilePath);
            }

            Files.createDirectories(targetProfilePath);
            return targetProfilePath;
        } catch (IOException e) {
            System.out.println("[WARN] Failed to use persistent profile path. Fallback to project path. Reason: " + e.getMessage());
            return legacyProfilePath;
        }
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
