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

            return hasVisibleElement(
                    By.xpath("//div[@role='button' and @aria-disabled='false' and .//*[local-name()='svg']]")
            );
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasVisibleElement(By by) {
        List<WebElement> elements = browserManager.getDriver().findElements(by);
        for (WebElement element : elements) {
            try {
                if (element.isDisplayed()) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    // РџРѕР»СѓС‡РµРЅРёРµ С‚РµРєСѓС‰РµРіРѕ РєРѕР»РёС‡РµСЃС‚РІР° РѕС‚РІРµС‚РѕРІ
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
        System.out.println("вњ… РџСЂРѕРјРїС‚ РІСЃС‚Р°РІР»РµРЅ: " + prompt);
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

        System.out.println("вњ… РџСЂРѕРјРїС‚ СѓСЃС‚Р°РЅРѕРІР»РµРЅ С‡РµСЂРµР· innerHTML");
    }


    private WebElement getFreshTextArea() {
        try {
            // РџСЂРѕР±СѓРµРј РЅР°Р№С‚Рё Р·Р°РЅРѕРІРѕ, Р° РЅРµ РёСЃРїРѕР»СЊР·РѕРІР°С‚СЊ РєСЌС€РёСЂРѕРІР°РЅРЅС‹Р№
            return browserManager.getDriver().findElement(
                    By.xpath("//textarea[@placeholder='РЎРѕРѕР±С‰РµРЅРёРµ РґР»СЏ DeepSeek']")
            );
        } catch (Exception e) {
            try {
                return browserManager.getDriver().findElement(
                        By.xpath("//textarea[@name='search']")
                );
            } catch (Exception ex) {
                return browserManager.getDriver().findElement(
                        By.xpath("//textarea[@autocomplete='off']")
                );
            }
        }
    }

    public boolean setPromptViaJS(String prompt) {
        WebElement textArea = getFreshTextArea();
        browserManager.getWait().until(ExpectedConditions.elementToBeClickable(textArea));

        JavascriptExecutor js = (JavascriptExecutor) browserManager.getDriver();
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
            Thread.sleep(150);
        } catch (InterruptedException ignored) {
        }

        textArea.sendKeys(" ");

        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {
        }

        String actualValue = (String) js.executeScript("return arguments[0].value;", textArea);
        boolean matchesPrompt = actualValue != null
                && (actualValue.equals(prompt) || actualValue.equals(prompt + " "));
        if (matchesPrompt) {
            System.out.println("✅ Промпт успешно установлен: " + prompt.substring(0, Math.min(50, prompt.length())) + "...");
            return true;
        }

        System.out.println("⚠️ Ошибка! Ожидалось: " + prompt + ", Получено: " + actualValue);
        js.executeScript(
                "arguments[0].value = arguments[1];" +
                        "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
                        "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));" +
                        "arguments[0].dispatchEvent(new Event('keyup', { bubbles: true }));",
                textArea,
                prompt + " "
        );

        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {
        }

        String retryValue = (String) js.executeScript("return arguments[0].value;", textArea);
        boolean retryMatches = retryValue != null
                && (retryValue.equals(prompt) || retryValue.equals(prompt + " "));
        if (!retryMatches) {
            System.out.println("❌ Повторная вставка не удалась. Получено: " + retryValue);
        }
        return retryMatches;
    }

    public boolean isContinueButtonVisibleForLastResponse() {
        try {
            // РЎРЅР°С‡Р°Р»Р° РёС‰РµРј РєРЅРѕРїРєСѓ СЃ С‚РµРєСЃС‚РѕРј "РџСЂРѕРґРѕР»Р¶РёС‚СЊ"
            WebElement continueButton = browserManager.getDriver().findElement(
                    By.xpath("//button[.//span[text()='РџСЂРѕРґРѕР»Р¶РёС‚СЊ']]")
            );

            return continueButton.isDisplayed() && continueButton.isEnabled();
        } catch (Exception e) {
            // Р•СЃР»Рё РЅРµ РЅР°С€Р»Рё, РёС‰РµРј РёРєРѕРЅРєСѓ-РєРЅРѕРїРєСѓ
           /* try {
                WebElement iconButton = browserManager.getDriver().findElement(
                        By.xpath("//div[contains(@class, 'ds-icon-button') and @role='button']")
                );
                return iconButton.isDisplayed() && iconButton.isEnabled();
            } catch (Exception e2) {
                return false;
            }

            */
           return false;
        }
    }

    public void clickContinueButtonForLastResponse() {
        try {
            // РЎРЅР°С‡Р°Р»Р° РїСЂРѕР±СѓРµРј РЅР°Р№С‚Рё РєРЅРѕРїРєСѓ СЃ С‚РµРєСЃС‚РѕРј "РџСЂРѕРґРѕР»Р¶РёС‚СЊ"
            WebElement continueButton = browserManager.getDriver().findElement(
                    By.xpath("//button[.//span[text()='РџСЂРѕРґРѕР»Р¶РёС‚СЊ']]")
            );
            continueButton.click();
            System.out.println("вњ… РљРЅРѕРїРєР° 'РџСЂРѕРґРѕР»Р¶РёС‚СЊ' (СЃ С‚РµРєСЃС‚РѕРј) РЅР°Р¶Р°С‚Р°");

        } catch (Exception e) {
            // Р•СЃР»Рё РЅРµС‚ РєРЅРѕРїРєРё СЃ С‚РµРєСЃС‚РѕРј, РёС‰РµРј РёРєРѕРЅРєСѓ
          /*  try {
                WebElement iconButton = browserManager.getDriver().findElement(
                        By.xpath("//div[contains(@class, 'ds-icon-button') and @role='button']")
                );
                iconButton.click();
                System.out.println("вњ… РљРЅРѕРїРєР° 'РџСЂРѕРґРѕР»Р¶РёС‚СЊ' (РёРєРѕРЅРєР°) РЅР°Р¶Р°С‚Р°");

            } catch (Exception e2) {
                System.out.println("вљ пёЏ РќРµ СѓРґР°Р»РѕСЃСЊ РЅР°Р¶Р°С‚СЊ РєРЅРѕРїРєСѓ 'РџСЂРѕРґРѕР»Р¶РёС‚СЊ': " + e.getMessage());
                return;
            }

           */
            e.printStackTrace();
        }

        // Р–РґРµРј РїСЂРѕРґРѕР»Р¶РµРЅРёСЏ РіРµРЅРµСЂР°С†РёРё
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // РџСЂРѕРІРµСЂРєР° РєРЅРѕРїРєРё "РљРѕРїРёСЂРѕРІР°С‚СЊ" РўРћР›Р¬РљРћ РґР»СЏ РїРѕСЃР»РµРґРЅРµРіРѕ РѕС‚РІРµС‚Р°
    public boolean isRepeatButtonVisibleForLastResponse() {
        try {
            WebElement repeatButton = browserManager.getDriver().findElement(
                    By.xpath("//button[@role='button' and @aria-disabled='false'][.//span[normalize-space(text())='РџРѕРІС‚РѕСЂРёС‚СЊ']]")
            );
            return repeatButton.isDisplayed() && repeatButton.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    public void clickRepeatButtonForLastResponse() {
        try {
            WebElement repeatButton = browserManager.getDriver().findElement(
                    By.xpath("//button[@role='button' and @aria-disabled='false'][.//span[normalize-space(text())='РџРѕРІС‚РѕСЂРёС‚СЊ']]")
            );

            JavascriptExecutor js = (JavascriptExecutor) browserManager.getDriver();
            js.executeScript("arguments[0].scrollIntoView(true);", repeatButton);
            js.executeScript("arguments[0].click();", repeatButton);
            System.out.println("вњ… РљРЅРѕРїРєР° 'РџРѕРІС‚РѕСЂРёС‚СЊ' РЅР°Р¶Р°С‚Р°");
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
                    By.xpath("(//div[contains(@class, 'ds-markdown')])[last()]/ancestor::div[contains(@class, 'message')]//button[@role='button' and not(@disabled)]//span[contains(text(), 'РљРѕРїРёСЂРѕРІР°С‚СЊ')]/ancestor::button[@role='button' and not(@disabled)]")
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
            System.out.println("⚠️ Промпт не вставился (попытка " + attempt + "/3), повторяем...");
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
        }

        if (!inserted) {
            throw new RuntimeException("Не удалось вставить промпт в поле ввода");
        }

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
            throw new RuntimeException("РќРµ СѓРґР°Р»РѕСЃСЊ РЅР°Р№С‚Рё РєРЅРѕРїРєСѓ РѕС‚РїСЂР°РІРєРё");
        }

        if (!isCorrectSendButton(sendButton)) {
            System.out.println("вљ пёЏ РќР°Р№РґРµРЅР° РЅРµ С‚Р° РєРЅРѕРїРєР°, РёС‰РµРј РґР°Р»СЊС€Рµ...");
            sendButton = browserManager.getDriver().findElement(
                    By.xpath("//div[@style='width: fit-content;']/div[@role='button']")
            );
        }

        JavascriptExecutor js = (JavascriptExecutor) browserManager.getDriver();
        js.executeScript("arguments[0].scrollIntoView(true);", sendButton);
        js.executeScript("arguments[0].click();", sendButton);

        System.out.println("вњ… РљРЅРѕРїРєР° РѕС‚РїСЂР°РІРєРё РЅР°Р¶Р°С‚Р°");
        System.out.println("вњ… РџСЂРѕРјРїС‚ РѕС‚РїСЂР°РІР»РµРЅ");
    }

    public boolean isCorrectSendButton(WebElement button) {
        try {
            String html = button.getAttribute("outerHTML");
            return html.contains("M8.3125 0.981587") && !html.contains("M9.67272 0.522841");
        } catch (Exception e) {
            return false;
        }
    }

    // РћР¶РёРґР°РЅРёРµ РїРѕСЏРІР»РµРЅРёСЏ РќРћР’РћР“Рћ РѕС‚РІРµС‚Р°
    public void waitForResponseStarted() {
        System.out.println("вЏі РћР¶РёРґР°РЅРёРµ РЅР°С‡Р°Р»Р° РЅРѕРІРѕРіРѕ РѕС‚РІРµС‚Р°...");

        int initialCount = getCurrentResponseCount();
        System.out.println("рџ“Љ РўРµРєСѓС‰РµРµ РєРѕР»РёС‡РµСЃС‚РІРѕ РѕС‚РІРµС‚РѕРІ: " + initialCount);

        try {
            // Р–РґРµРј, РєРѕРіРґР° РїРѕСЏРІРёС‚СЃСЏ РЅРѕРІС‹Р№ РѕС‚РІРµС‚ (РєРѕР»РёС‡РµСЃС‚РІРѕ СѓРІРµР»РёС‡РёС‚СЃСЏ)
            browserManager.getWait().until(ExpectedConditions.numberOfElementsToBeMoreThan(
                    By.xpath("//div[contains(@class, 'ds-markdown')]"),
                    initialCount
            ));

            System.out.println("вњ… РќРѕРІС‹Р№ РѕС‚РІРµС‚ РЅР°С‡Р°Р» РїРѕСЏРІР»СЏС‚СЊСЃСЏ");

            // РќРµР±РѕР»СЊС€Р°СЏ Р·Р°РґРµСЂР¶РєР° РґР»СЏ СЃС‚Р°Р±РёР»РёР·Р°С†РёРё
            Thread.sleep(500);

        } catch (Exception e) {
            System.out.println("вљ пёЏ РќРµ СѓРґР°Р»РѕСЃСЊ РѕР±РЅР°СЂСѓР¶РёС‚СЊ РЅР°С‡Р°Р»Рѕ РѕС‚РІРµС‚Р°: " + e.getMessage());
        }
    }

    // РћР¶РёРґР°РЅРёРµ РїРѕР»РЅРѕРіРѕ Р·Р°РІРµСЂС€РµРЅРёСЏ РіРµРЅРµСЂР°С†РёРё РќРћР’РћР“Рћ РѕС‚РІРµС‚Р°
    public void waitForResponseComplete() {
        System.out.println("вЏі РћР¶РёРґР°РЅРёРµ Р·Р°РІРµСЂС€РµРЅРёСЏ РіРµРЅРµСЂР°С†РёРё РЅРѕРІРѕРіРѕ РѕС‚РІРµС‚Р°...");

        int maxWaitSeconds = 300;
        int waitedSeconds = 0;
        boolean copyButtonWasEnabled = false;

        while (waitedSeconds < maxWaitSeconds) {
            try {
                // РџСЂРёРѕСЂРёС‚РµС‚ 1: РїСЂРѕРІРµСЂРєР° РєРЅРѕРїРєРё "РџСЂРѕРґРѕР»Р¶РёС‚СЊ" РІ РїРѕСЃР»РµРґРЅРµРј РѕС‚РІРµС‚Рµ
                if (isContinueButtonVisibleForLastResponse()) {
                    System.out.println("рџ” РћР±РЅР°СЂСѓР¶РµРЅР° РєРЅРѕРїРєР° 'РџСЂРѕРґРѕР»Р¶РёС‚СЊ', РЅР°Р¶РёРјР°РµРј...");
                    clickContinueButtonForLastResponse();
                    waitedSeconds = 0;
                    copyButtonWasEnabled = false;
                    Thread.sleep(1000);
                    continue;
                }

                // РџСЂРёРѕСЂРёС‚РµС‚ 2: РїСЂРѕРІРµСЂРєР° РєРЅРѕРїРєРё "РљРѕРїРёСЂРѕРІР°С‚СЊ" РІ РїРѕСЃР»РµРґРЅРµРј РѕС‚РІРµС‚Рµ
                if (isRepeatButtonVisibleForLastResponse()) {
                    System.out.println("рџ”Ѓ РћР±РЅР°СЂСѓР¶РµРЅР° РєРЅРѕРїРєР° 'РџРѕРІС‚РѕСЂРёС‚СЊ', РЅР°Р¶РёРјР°РµРј...");
                    clickRepeatButtonForLastResponse();
                    waitedSeconds = 0;
                    copyButtonWasEnabled = false;
                    Thread.sleep(1000);
                    continue;
                }

                if (isCopyButtonEnabledForLastResponse()) {
                    if (!copyButtonWasEnabled) {
                        System.out.println("рџ“‹ РљРЅРѕРїРєР° 'РљРѕРїРёСЂРѕРІР°С‚СЊ' Р°РєС‚РёРІРЅР°, РїСЂРѕРІРµСЂСЏРµРј С‡РµСЂРµР· 100РјСЃ...");
                        copyButtonWasEnabled = true;

                        Thread.sleep(200);

                        // РџРѕСЃР»Рµ РїР°СѓР·С‹ СЃРЅРѕРІР° РїСЂРѕРІРµСЂСЏРµРј РєРЅРѕРїРєСѓ "РџСЂРѕРґРѕР»Р¶РёС‚СЊ"
                        if (isContinueButtonVisibleForLastResponse()) {
                            System.out.println("рџ” РџРѕСЃР»Рµ РїСЂРѕРІРµСЂРєРё РїРѕСЏРІРёР»Р°СЃСЊ РєРЅРѕРїРєР° 'РџСЂРѕРґРѕР»Р¶РёС‚СЊ', РЅР°Р¶РёРјР°РµРј...");
                            clickContinueButtonForLastResponse();
                            waitedSeconds = 0;
                            copyButtonWasEnabled = false;
                            continue;
                        }

                        // Р¤РёРЅР°Р»СЊРЅР°СЏ РїСЂРѕРІРµСЂРєР° РєРЅРѕРїРєРё "РљРѕРїРёСЂРѕРІР°С‚СЊ"
                        if (isCopyButtonEnabledForLastResponse()) {
                            System.out.println("вњ… Р“РµРЅРµСЂР°С†РёСЏ РЅРѕРІРѕРіРѕ РѕС‚РІРµС‚Р° Р·Р°РІРµСЂС€РµРЅР°!");
                            Thread.sleep(500); // Р¤РёРЅР°Р»СЊРЅР°СЏ СЃС‚Р°Р±РёР»РёР·Р°С†РёСЏ
                            return;
                        } else {
                            System.out.println("вљ пёЏ РљРЅРѕРїРєР° 'РљРѕРїРёСЂРѕРІР°С‚СЊ' РїРµСЂРµСЃС‚Р°Р»Р° Р±С‹С‚СЊ Р°РєС‚РёРІРЅРѕР№, РїСЂРѕРґРѕР»Р¶Р°РµРј...");
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
                System.out.println("вЏі РћР¶РёРґР°РЅРёРµ... " + waitedSeconds + " СЃРµРє");
            }
        }

        System.out.println("вљ пёЏ РўР°Р№РјР°СѓС‚ РѕР¶РёРґР°РЅРёСЏ, РїСЂРѕР±СѓРµРј РїРѕР»СѓС‡РёС‚СЊ РѕС‚РІРµС‚ РІ Р»СЋР±РѕРј СЃР»СѓС‡Р°Рµ");
    }

    // РџРѕР»СѓС‡РµРЅРёРµ РўРћР›Р¬РљРћ РџРћРЎР›Р•Р”РќР•Р“Рћ (РЅРѕРІРѕРіРѕ) РѕС‚РІРµС‚Р°
    public String getResponse() {
        try {
            List<WebElement> responses = browserManager.getDriver().findElements(
                    By.xpath("//div[contains(@class, 'ds-markdown')]")
            );

            if (!responses.isEmpty()) {
                String response = responses.get(responses.size() - 1).getText();
                System.out.println("вњ… РџРѕР»СѓС‡РµРЅ РЅРѕРІС‹Р№ РѕС‚РІРµС‚ (" + response.length() + " СЃРёРјРІРѕР»РѕРІ)");
                return response;
            }

            return null;

        } catch (Exception e) {
            System.err.println("вќЊ РћС€РёР±РєР° РїСЂРё РїРѕР»СѓС‡РµРЅРёРё РѕС‚РІРµС‚Р°: " + e.getMessage());
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
            throw new RuntimeException("РџРѕР»СЊР·РѕРІР°С‚РµР»СЊ РЅРµ Р°РІС‚РѕСЂРёР·РѕРІР°РЅ РІ DeepSeek!");
        }

        // Р—Р°РїРѕРјРёРЅР°РµРј РєРѕР»РёС‡РµСЃС‚РІРѕ РѕС‚РІРµС‚РѕРІ РґРѕ РѕС‚РїСЂР°РІРєРё
        lastResponseCount = getCurrentResponseCount();
        System.out.println("рџ“Љ РћС‚РїСЂР°РІРєР° РїСЂРѕРјРїС‚Р°. РўРµРєСѓС‰РµРµ РєРѕР»РёС‡РµСЃС‚РІРѕ РѕС‚РІРµС‚РѕРІ: " + lastResponseCount);

        sendPromptWithEnter(prompt);

        // Р–РґРµРј РЅР°С‡Р°Р»Р° РЅРѕРІРѕРіРѕ РѕС‚РІРµС‚Р°
        waitForResponseStarted();

        // Р–РґРµРј РїРѕР»РЅРѕРіРѕ Р·Р°РІРµСЂС€РµРЅРёСЏ РіРµРЅРµСЂР°С†РёРё
        waitForResponseComplete();

        // РџРѕР»СѓС‡Р°РµРј РЅРѕРІС‹Р№ РѕС‚РІРµС‚
        return getResponse();
    }
}
