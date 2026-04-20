package ru.sibgatulinanton.deepseek;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import ru.sibgatulinanton.deepseek.dto.DeepSeekElements;

import java.util.List;

public class DeepSeekChatPage {

    private final BrowserDriverManager browserManager;
    private final DeepSeekElements elements;
    private int lastResponseCount = 0; // Счетчик ответов для отслеживания новых

    public DeepSeekChatPage(BrowserDriverManager browserManager) {
        this.browserManager = browserManager;
        this.elements = new DeepSeekElements();
        PageFactory.initElements(browserManager.getDriver(), elements);
    }

    public boolean isUserLoggedIn() {
        try {
            return elements.getPromptTextArea().isDisplayed() ||
                    elements.getPromptTextAreaByPlaceholder().isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    // Получение текущего количества ответов
    private int getCurrentResponseCount() {
        try {
            List<WebElement> responses = browserManager.getDriver().findElements(
                    By.xpath("//div[contains(@class, 'ds-markdown')]")
            );
            return responses.size();
        } catch (Exception e) {
            return 0;
        }
    }

    public void insertPrompt(String prompt) {
        browserManager.getWait().until(ExpectedConditions.elementToBeClickable(elements.getPromptTextArea()));
        elements.getPromptTextArea().clear();
        elements.getPromptTextArea().sendKeys(prompt);
        System.out.println("✅ Промпт вставлен: " + prompt);
    }

    public void setPromptViaInnerHTML(String prompt) {
        browserManager.getWait().until(ExpectedConditions.elementToBeClickable(elements.getPromptTextArea()));

        JavascriptExecutor js = (JavascriptExecutor) browserManager.getDriver();
        js.executeScript(
                "arguments[0].innerHTML = arguments[1];" +
                        "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));",
                elements.getPromptTextArea(),
                prompt
        );

        System.out.println("✅ Промпт установлен через innerHTML");
    }

    // Проверка кнопки "Продолжить" ТОЛЬКО для последнего ответа
    public boolean isContinueButtonVisibleForLastResponse() {
        try {
            WebElement continueButton = browserManager.getDriver().findElement(
                    By.xpath("(//div[contains(@class, 'ds-markdown')])[last()]/ancestor::div[contains(@class, 'message')]//button[@role='button' and @aria-disabled='false']//span[text()='Продолжить']/ancestor::button[@role='button']")
            );
            return continueButton.isDisplayed() && continueButton.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    // Клик по кнопке "Продолжить" для последнего ответа
    public void clickContinueButtonForLastResponse() {
        try {
            WebElement continueButton = browserManager.getDriver().findElement(
                    By.xpath("(//div[contains(@class, 'ds-markdown')])[last()]/ancestor::div[contains(@class, 'message')]//button[@role='button' and @aria-disabled='false']//span[text()='Продолжить']/ancestor::button[@role='button']")
            );

            JavascriptExecutor js = (JavascriptExecutor) browserManager.getDriver();
            js.executeScript("arguments[0].scrollIntoView(true);", continueButton);
            js.executeScript("arguments[0].click();", continueButton);

            System.out.println("✅ Кнопка 'Продолжить' нажата, ждем продолжения генерации...");

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } catch (Exception e) {
            System.out.println("⚠️ Не удалось нажать кнопку 'Продолжить': " + e.getMessage());
        }
    }

    // Проверка кнопки "Копировать" ТОЛЬКО для последнего ответа
    public boolean isCopyButtonEnabledForLastResponse() {
        try {
            WebElement copyButton = browserManager.getDriver().findElement(
                    By.xpath("(//div[contains(@class, 'ds-markdown')])[last()]/ancestor::div[contains(@class, 'message')]//button[@role='button' and not(@disabled)]//span[contains(text(), 'Копировать')]/ancestor::button[@role='button' and not(@disabled)]")
            );
            return copyButton.isEnabled() && copyButton.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    public void sendPromptWithEnter(String prompt) {
        setPromptViaInnerHTML(prompt);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        browserManager.getWait().until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//div[@style='width: fit-content;']/div[@role='button' and @aria-disabled='false']")
        ));

        WebElement sendButton = null;

        try {
            sendButton = browserManager.getDriver().findElement(
                    By.xpath("//div[@style='width: fit-content;']/div[@role='button' and @aria-disabled='false']")
            );
        } catch (Exception e) {
            try {
                sendButton = browserManager.getDriver().findElement(
                        By.xpath("//div[contains(@class, '_52c986b') and @role='button' and @aria-disabled='false']")
                );
            } catch (Exception ex) {
                sendButton = browserManager.getDriver().findElement(
                        By.xpath("//div[@role='button']//path[contains(@d, 'M8.3125 0.981587')]/ancestor::div[@role='button']")
                );
            }
        }

        if (sendButton == null) {
            throw new RuntimeException("Не удалось найти кнопку отправки");
        }

        if (!isCorrectSendButton(sendButton)) {
            System.out.println("⚠️ Найдена не та кнопка, ищем дальше...");
            sendButton = browserManager.getDriver().findElement(
                    By.xpath("//div[@style='width: fit-content;']/div[@role='button']")
            );
        }

        JavascriptExecutor js = (JavascriptExecutor) browserManager.getDriver();
        js.executeScript("arguments[0].scrollIntoView(true);", sendButton);
        js.executeScript("arguments[0].click();", sendButton);

        System.out.println("✅ Кнопка отправки нажата");
        System.out.println("✅ Промпт отправлен");
    }

    public boolean isCorrectSendButton(WebElement button) {
        try {
            String html = button.getAttribute("outerHTML");
            return html.contains("M8.3125 0.981587") && !html.contains("M9.67272 0.522841");
        } catch (Exception e) {
            return false;
        }
    }

    // Ожидание появления НОВОГО ответа
    public void waitForResponseStarted() {
        System.out.println("⏳ Ожидание начала нового ответа...");

        int initialCount = getCurrentResponseCount();
        System.out.println("📊 Текущее количество ответов: " + initialCount);

        try {
            // Ждем, когда появится новый ответ (количество увеличится)
            browserManager.getWait().until(ExpectedConditions.numberOfElementsToBeMoreThan(
                    By.xpath("//div[contains(@class, 'ds-markdown')]"),
                    initialCount
            ));

            System.out.println("✅ Новый ответ начал появляться");

            // Небольшая задержка для стабилизации
            Thread.sleep(500);

        } catch (Exception e) {
            System.out.println("⚠️ Не удалось обнаружить начало ответа: " + e.getMessage());
        }
    }

    // Ожидание полного завершения генерации НОВОГО ответа
    public void waitForResponseComplete() {
        System.out.println("⏳ Ожидание завершения генерации нового ответа...");

        int maxWaitSeconds = 300;
        int waitedSeconds = 0;
        boolean copyButtonWasEnabled = false;

        while (waitedSeconds < maxWaitSeconds) {
            try {
                // Приоритет 1: проверка кнопки "Продолжить" в последнем ответе
                if (isContinueButtonVisibleForLastResponse()) {
                    System.out.println("🔘 Обнаружена кнопка 'Продолжить', нажимаем...");
                    clickContinueButtonForLastResponse();
                    waitedSeconds = 0;
                    copyButtonWasEnabled = false;
                    Thread.sleep(1000);
                    continue;
                }

                // Приоритет 2: проверка кнопки "Копировать" в последнем ответе
                if (isCopyButtonEnabledForLastResponse()) {
                    if (!copyButtonWasEnabled) {
                        System.out.println("📋 Кнопка 'Копировать' активна, проверяем через 100мс...");
                        copyButtonWasEnabled = true;

                        Thread.sleep(100);

                        // После паузы снова проверяем кнопку "Продолжить"
                        if (isContinueButtonVisibleForLastResponse()) {
                            System.out.println("🔘 После проверки появилась кнопка 'Продолжить', нажимаем...");
                            clickContinueButtonForLastResponse();
                            waitedSeconds = 0;
                            copyButtonWasEnabled = false;
                            continue;
                        }

                        // Финальная проверка кнопки "Копировать"
                        if (isCopyButtonEnabledForLastResponse()) {
                            System.out.println("✅ Генерация нового ответа завершена!");
                            Thread.sleep(500); // Финальная стабилизация
                            return;
                        } else {
                            System.out.println("⚠️ Кнопка 'Копировать' перестала быть активной, продолжаем...");
                            copyButtonWasEnabled = false;
                        }
                    } else {
                        return;
                    }
                } else {
                    copyButtonWasEnabled = false;
                }

            } catch (Exception e) {
                copyButtonWasEnabled = false;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            waitedSeconds++;

            if (waitedSeconds % 10 == 0) {
                System.out.println("⏳ Ожидание... " + waitedSeconds + " сек");
            }
        }

        System.out.println("⚠️ Таймаут ожидания, пробуем получить ответ в любом случае");
    }

    // Получение ТОЛЬКО ПОСЛЕДНЕГО (нового) ответа
    public String getResponse() {
        try {
            List<WebElement> responses = browserManager.getDriver().findElements(
                    By.xpath("//div[contains(@class, 'ds-markdown')]")
            );

            if (!responses.isEmpty()) {
                String response = responses.get(responses.size() - 1).getText();
                System.out.println("✅ Получен новый ответ (" + response.length() + " символов)");
                return response;
            }

            return null;

        } catch (Exception e) {
            System.err.println("❌ Ошибка при получении ответа: " + e.getMessage());
            return null;
        }
    }

    public String askDeepSeek(String prompt) {
        if (!isUserLoggedIn()) {
            throw new RuntimeException("Пользователь не авторизован в DeepSeek!");
        }

        // Запоминаем количество ответов до отправки
        lastResponseCount = getCurrentResponseCount();
        System.out.println("📊 Отправка промпта. Текущее количество ответов: " + lastResponseCount);

        sendPromptWithEnter(prompt);

        // Ждем начала нового ответа
        waitForResponseStarted();

        // Ждем полного завершения генерации
        waitForResponseComplete();

        // Получаем новый ответ
        return getResponse();
    }
}