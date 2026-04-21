package ru.sibgatulinanton.deepseek;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import ru.sibgatulinanton.deepseek.dto.DeepSeekElements;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeepSeekChatPage {
    private static final Pattern CHAT_ID_PATTERN = Pattern.compile("/a/chat/s/([0-9a-fA-F-]+)");

    private final BrowserDriverManager browserManager;
    private final DeepSeekElements elements;
    private int lastResponseCount = 0;

    public DeepSeekChatPage(BrowserDriverManager browserManager) {
        this.browserManager = browserManager;
        this.elements = new DeepSeekElements();
        PageFactory.initElements(browserManager.getDriver(), elements);
    }

    public boolean isUserLoggedIn() {
        try {
            String currentUrl = getCurrentUrl();
            if (currentUrl != null) {
                String lower = currentUrl.toLowerCase();
                if (lower.contains("sign-in") || lower.contains("login")) {
                    return false;
                }
            }

            if (hasVisibleElement(By.cssSelector("textarea"))) {
                return true;
            }

            if (hasVisibleElement(By.cssSelector("div[contenteditable='true']"))) {
                return true;
            }

            if (hasVisibleElement(By.xpath("//textarea[@name='search' or @autocomplete='off']"))) {
                return true;
            }

            return hasVisibleElement(By.xpath("//div[@role='button' and @aria-disabled='false' and .//*[local-name()='svg']"));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasVisibleElement(By by) {
        List<WebElement> found = browserManager.getDriver().findElements(by);
        for (WebElement item : found) {
            try {
                if (item.isDisplayed()) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private int getCurrentResponseCount() {
        try {
            List<WebElement> responses = browserManager.getDriver().findElements(By.xpath("//div[contains(@class, 'ds-markdown')]"));
            return responses.size();
        } catch (Exception e) {
            return 0;
        }
    }

    public void insertPrompt(String prompt) {
        browserManager.getWait().until(ExpectedConditions.elementToBeClickable(elements.getPromptTextArea()));
        elements.getPromptTextArea().clear();
        elements.getPromptTextArea().sendKeys(prompt);
        System.out.println("[OK] Prompt inserted");
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
        System.out.println("[OK] Prompt inserted via innerHTML");
    }

    private WebElement getFreshTextArea() {
        try {
            return browserManager.getDriver().findElement(By.xpath("//textarea[@placeholder='Сообщение для DeepSeek']"));
        } catch (Exception e) {
            try {
                return browserManager.getDriver().findElement(By.xpath("//textarea[@name='search']"));
            } catch (Exception ex) {
                return browserManager.getDriver().findElement(By.xpath("//textarea[@autocomplete='off']"));
            }
        }
    }

    public boolean setPromptViaJS(String prompt) {
        WebElement textArea = getFreshTextArea();
        browserManager.getWait().until(ExpectedConditions.elementToBeClickable(textArea));
        JavascriptExecutor js = (JavascriptExecutor) browserManager.getDriver();

        String promptNorm = normalizePromptForCompare(prompt);

        for (int attempt = 1; attempt <= 3; attempt++) {
            js.executeScript("arguments[0].value = '';", textArea);
            js.executeScript(
                    "arguments[0].value = arguments[1];" +
                            "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
                            "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));" +
                            "arguments[0].dispatchEvent(new Event('keyup', { bubbles: true }));" +
                            "arguments[0].focus();",
                    textArea,
                    prompt
            );

            try {
                Thread.sleep(120);
            } catch (InterruptedException ignored) {
            }

            textArea.sendKeys(" ");

            try {
                Thread.sleep(120);
            } catch (InterruptedException ignored) {
            }

            String actualValue = (String) js.executeScript("return arguments[0].value;", textArea);
            String actualNorm = normalizePromptForCompare(actualValue);

            boolean ok = !promptNorm.isEmpty() && (actualNorm.equals(promptNorm) || actualNorm.contains(promptNorm));
            if (ok) {
                System.out.println("[OK] Prompt inserted. attempt=" + attempt);
                return true;
            }

            System.out.println("[WARN] Prompt mismatch. attempt=" + attempt + ", expectedNorm=" + promptNorm + ", actualNorm=" + actualNorm);
        }

        return false;
    }

    private String normalizePromptForCompare(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace('\u00A0', ' ')
                .replace("\u200B", "")
                .replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public boolean isContinueButtonVisibleForLastResponse() {
        try {
            WebElement continueButton = browserManager.getDriver().findElement(By.xpath("//button[.//span[text()='Продолжить']]") );
            return continueButton.isDisplayed() && continueButton.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    public void clickContinueButtonForLastResponse() {
        try {
            WebElement continueButton = browserManager.getDriver().findElement(By.xpath("//button[.//span[text()='Продолжить']]") );
            continueButton.click();
            System.out.println("[OK] Continue button clicked");
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean isRepeatButtonVisibleForLastResponse() {
        try {
            WebElement repeatButton = browserManager.getDriver().findElement(
                    By.xpath("//button[@role='button' and @aria-disabled='false'][.//span[normalize-space(text())='Повторить']]")
            );
            return repeatButton.isDisplayed() && repeatButton.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    public void clickRepeatButtonForLastResponse() {
        try {
            WebElement repeatButton = browserManager.getDriver().findElement(
                    By.xpath("//button[@role='button' and @aria-disabled='false'][.//span[normalize-space(text())='Повторить']]")
            );

            JavascriptExecutor js = (JavascriptExecutor) browserManager.getDriver();
            js.executeScript("arguments[0].scrollIntoView(true);", repeatButton);
            js.executeScript("arguments[0].click();", repeatButton);
            System.out.println("[OK] Repeat button clicked");
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

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
        boolean inserted = false;
        for (int attempt = 1; attempt <= 3; attempt++) {
            inserted = setPromptViaJS(prompt);
            if (inserted) {
                break;
            }
            System.out.println("[WARN] Prompt not inserted (" + attempt + "/3), retry...");
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
        }

        if (!inserted) {
            System.out.println("[WARN] Prompt verification failed. Continue send attempt anyway.");
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        browserManager.getWait().until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//div[@style='width: fit-content;']/div[@role='button' and @aria-disabled='false']")
        ));

        WebElement sendButton;
        try {
            sendButton = browserManager.getDriver().findElement(By.xpath("//div[@style='width: fit-content;']/div[@role='button' and @aria-disabled='false']"));
        } catch (Exception e) {
            try {
                sendButton = browserManager.getDriver().findElement(By.xpath("//div[contains(@class, '_52c986b') and @role='button' and @aria-disabled='false']"));
            } catch (Exception ex) {
                sendButton = browserManager.getDriver().findElement(By.xpath("//div[@role='button']//path[contains(@d, 'M8.3125 0.981587')]/ancestor::div[@role='button']"));
            }
        }

        if (sendButton == null) {
            throw new RuntimeException("Send button not found");
        }

        if (!isCorrectSendButton(sendButton)) {
            System.out.println("[WARN] Not exact send button, trying fallback selector");
            sendButton = browserManager.getDriver().findElement(By.xpath("//div[@style='width: fit-content;']/div[@role='button']"));
        }

        JavascriptExecutor js = (JavascriptExecutor) browserManager.getDriver();
        js.executeScript("arguments[0].scrollIntoView(true);", sendButton);
        js.executeScript("arguments[0].click();", sendButton);

        System.out.println("[OK] Send button clicked");
        System.out.println("[OK] Prompt sent");
    }

    public boolean isCorrectSendButton(WebElement button) {
        try {
            String html = button.getAttribute("outerHTML");
            return html.contains("M8.3125 0.981587") && !html.contains("M9.67272 0.522841");
        } catch (Exception e) {
            return false;
        }
    }

    public void waitForResponseStarted() {
        System.out.println("[WAIT] Waiting for response start...");

        int initialCount = getCurrentResponseCount();
        System.out.println("[INFO] Current response count: " + initialCount);

        try {
            browserManager.getWait().until(ExpectedConditions.numberOfElementsToBeMoreThan(
                    By.xpath("//div[contains(@class, 'ds-markdown')]"),
                    initialCount
            ));

            System.out.println("[OK] Response started");
            Thread.sleep(500);

        } catch (Exception e) {
            System.out.println("[WARN] Failed to detect response start: " + e.getMessage());
        }
    }

    public void waitForResponseComplete() {
        System.out.println("[WAIT] Waiting for response completion...");

        int maxWaitSeconds = 300;
        int waitedSeconds = 0;
        boolean copyButtonWasEnabled = false;

        while (waitedSeconds < maxWaitSeconds) {
            try {
                if (isContinueButtonVisibleForLastResponse()) {
                    System.out.println("[INFO] Continue button detected, clicking...");
                    clickContinueButtonForLastResponse();
                    waitedSeconds = 0;
                    copyButtonWasEnabled = false;
                    Thread.sleep(1000);
                    continue;
                }

                if (isRepeatButtonVisibleForLastResponse()) {
                    System.out.println("[INFO] Repeat button detected, clicking...");
                    clickRepeatButtonForLastResponse();
                    waitedSeconds = 0;
                    copyButtonWasEnabled = false;
                    Thread.sleep(1000);
                    continue;
                }

                if (isCopyButtonEnabledForLastResponse()) {
                    if (!copyButtonWasEnabled) {
                        copyButtonWasEnabled = true;
                        Thread.sleep(200);

                        if (isContinueButtonVisibleForLastResponse()) {
                            clickContinueButtonForLastResponse();
                            waitedSeconds = 0;
                            copyButtonWasEnabled = false;
                            continue;
                        }

                        if (isCopyButtonEnabledForLastResponse()) {
                            System.out.println("[OK] Response completed");
                            Thread.sleep(500);
                            return;
                        }
                        copyButtonWasEnabled = false;
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
                System.out.println("[WAIT] " + waitedSeconds + " sec");
            }
        }

        System.out.println("[WARN] Wait timeout, continue anyway");
    }

    public String getResponse() {
        try {
            List<WebElement> responses = browserManager.getDriver().findElements(By.xpath("//div[contains(@class, 'ds-markdown')]"));
            if (!responses.isEmpty()) {
                String response = responses.get(responses.size() - 1).getText();
                System.out.println("[OK] Response received, length=" + response.length());
                return response;
            }
            return null;
        } catch (Exception e) {
            System.err.println("[ERR] Failed to get response: " + e.getMessage());
            return null;
        }
    }

    public String getCurrentUrl() {
        try {
            return browserManager.getDriver().getCurrentUrl();
        } catch (Exception e) {
            return null;
        }
    }

    public String getChatIdFromCurrentUrl() {
        String currentUrl = getCurrentUrl();
        if (currentUrl == null || currentUrl.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = CHAT_ID_PATTERN.matcher(currentUrl);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public String askDeepSeek(String prompt) {
        if (!isUserLoggedIn()) {
            throw new RuntimeException("User is not authorized in DeepSeek");
        }

        lastResponseCount = getCurrentResponseCount();
        System.out.println("[INFO] Sending prompt. currentResponseCount=" + lastResponseCount);

        sendPromptWithEnter(prompt);
        waitForResponseStarted();
        waitForResponseComplete();

        return getResponse();
    }
}
