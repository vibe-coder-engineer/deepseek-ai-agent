package ru.sibgatulinanton.deepseek;


import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import ru.sibgatulinanton.deepseek.dto.DeepSeekElements;

public class DeepSeekChatPage {

    private final BrowserDriverManager browserManager;
    private final DeepSeekElements elements;

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


    public void sendPromptWithEnter(String prompt) {
       // insertPrompt(prompt);
        setPromptViaInnerHTML(prompt);
        try {
            Thread.sleep(3000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        browserManager.getWait().until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//div[@style='width: fit-content;']/div[@role='button' and @aria-disabled='false']")
        ));

        // Находим кнопку через уникальный селектор
        WebElement sendButton = null;

        try {
            // Пробуем найти через обертку (самый надежный способ)
            sendButton = browserManager.getDriver().findElement(
                    By.xpath("//div[@style='width: fit-content;']/div[@role='button' and @aria-disabled='false']")
            );
        } catch (Exception e) {
            try {
                // Fallback: ищем по уникальному классу _52c986b
                sendButton = browserManager.getDriver().findElement(
                        By.xpath("//div[contains(@class, '_52c986b') and @role='button' and @aria-disabled='false']")
                );
            } catch (Exception ex) {
                // Fallback: ищем по стрелке в path
                sendButton = browserManager.getDriver().findElement(
                        By.xpath("//div[@role='button']//path[contains(@d, 'M8.3125 0.981587')]/ancestor::div[@role='button']")
                );
            }
        }

        if (sendButton == null) {
            throw new RuntimeException("Не удалось найти кнопку отправки");
        }

        // Проверяем, что это правильная кнопка
        if (!isCorrectSendButton(sendButton)) {
            System.out.println("⚠️ Найдена не та кнопка, ищем дальше...");
            // Ищем кнопку с оберткой
            sendButton = browserManager.getDriver().findElement(
                    By.xpath("//div[@style='width: fit-content;']/div[@role='button']")
            );
        }

        // Кликаем через JavaScript
        JavascriptExecutor js = (JavascriptExecutor) browserManager.getDriver();
        js.executeScript("arguments[0].scrollIntoView(true);", sendButton);
        js.executeScript("arguments[0].click();", sendButton);

        System.out.println("✅ Кнопка отправки нажата");
        System.out.println("✅ Промпт отправлен (Enter)");
    }

    public boolean isCorrectSendButton(WebElement button) {
        try {
            String html = button.getAttribute("outerHTML");
            // Проверяем, что внутри есть стрелка, а не папка
            return html.contains("M8.3125 0.981587") && !html.contains("M9.67272 0.522841");
        } catch (Exception e) {
            return false;
        }
    }

    public void sendPromptWithButton(String prompt) {
        insertPrompt(prompt);
        browserManager.getWait().until(ExpectedConditions.elementToBeClickable(elements.getSendButton())).click();
        System.out.println("✅ Промпт отправлен (кнопка)");
    }

    public String getResponse() {
        try {
            // Находим все сообщения с ответами
            java.util.List<WebElement> responses = browserManager.getDriver().findElements(
                    By.xpath("//div[contains(@class, 'ds-markdown')]")
            );

            if (!responses.isEmpty()) {
                String response = responses.get(responses.size() - 1).getText();
                System.out.println("✅ Получен ответ (" + response.length() + " символов)");
                System.out.println(response);
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
        sendPromptWithEnter(prompt);

        // Ждем начала ответа
        waitForResponseStarted();

        // Ждем полного завершения генерации
        waitForResponseComplete();

        // Получаем ответ
        return getResponse();
    }



    public void waitForResponseComplete() {
        System.out.println("⏳ Ожидание завершения генерации ответа...");

        try {
            // Ждем, когда кнопка "Копировать" станет активной (без атрибута disabled)
            browserManager.getWait().until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//button[@role='button' and not(@disabled)]//span[contains(text(), 'Копировать')]")
            ));

            System.out.println("✅ Кнопка 'Копировать' стала активной - ответ полностью сгенерирован");
        } catch (Exception e) {
            System.out.println("⚠️ Не удалось дождаться стрелки, пробуем альтернативный способ...");

            // Альтернативный способ: ждем, пока исчезнет индикатор печати/генерации
            try {
                Thread.sleep(2000);

                // Ищем индикатор генерации
                By typingIndicator = By.xpath("//div[contains(@class, 'typing') or contains(@class, 'loading') or contains(@class, 'ds-typing')]");
                browserManager.getWait().until(ExpectedConditions.invisibilityOfElementLocated(typingIndicator));

                System.out.println("✅ Индикатор генерации исчез");
            } catch (Exception ex) {
                // Если индикатора нет, просто ждем фиксированное время
                System.out.println("⏳ Дополнительная задержка 5 секунд...");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException exc) {
                    throw new RuntimeException(exc);
                }
            }
        }

        // Дополнительная задержка для полной стабилизации
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Ожидание появления первого символа ответа
    public void waitForResponseStarted() {
        System.out.println("⏳ Ожидание начала ответа...");

        try {
            // Ждем появления любого текста в области ответа
            browserManager.getWait().until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//div[contains(@class, 'ds-markdown')]")
            ));

            System.out.println("✅ Ответ начал появляться");

        } catch (Exception e) {
            System.out.println("⚠️ Не удалось обнаружить начало ответа");
        }
    }
}