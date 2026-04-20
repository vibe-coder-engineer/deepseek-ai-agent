package ru.sibgatulinanton.deepseek.dto;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.CacheLookup;

public class DeepSeekElements {

    // ========== ТЕКСТОВОЕ ПОЛЕ ==========

    @FindBy(xpath = "//textarea[@placeholder='Сообщение для DeepSeek']")
    private WebElement promptTextAreaByPlaceholder;

    @FindBy(xpath = "//textarea[@name='search']")
    private WebElement promptTextAreaByName;

    @FindBy(xpath = "//textarea[@autocomplete='off']")
    private WebElement promptTextAreaByAutocomplete;

    @FindBy(css = "textarea.ds-scroll-area")
    private WebElement promptTextAreaByClass;


    // ========== КНОПКА ОТПРАВКИ ==========

    // По классам из вашего HTML
    @FindBy(xpath = "//div[@role='button' and contains(@class, 'ds-icon-button')]")
    private WebElement sendButtonByRole;

    @FindBy(css = "div.ds-icon-button[role='button']")
    private WebElement sendButtonByCss;

    @FindBy(xpath = "//div[contains(@class, 'ds-icon-button') and @role='button']")
    private WebElement sendButtonByClass;

    // Если кнопка активна (aria-disabled="false")
    @FindBy(xpath = "//div[@role='button' and contains(@class, 'ds-icon-button') and @aria-disabled='false']")
    private WebElement sendButtonActive;

    // По иконке внутри
    @FindBy(xpath = "//div[@role='button']//*[local-name()='svg']/ancestor::div[@role='button']")
    private WebElement sendButtonByIcon;

    @FindBy(xpath = "//div[contains(@class, 'ds-icon-button')]//svg/parent::div[@role='button']")
    private WebElement sendButtonBySvg;


    // ========== ОБЛАСТЬ ОТВЕТА ==========

    @FindBy(css = ".ds-markdown")
    private WebElement responseArea;

    @FindBy(xpath = "//div[contains(@class, 'ds-markdown')]")
    private WebElement responseAreaByClass;

    @FindBy(xpath = "//div[contains(@class, 'message')][last()]")
    private WebElement lastMessage;


    // ========== ИНДИКАТОРЫ ==========

    @FindBy(xpath = "//div[contains(@class, 'typing') or contains(@class, 'loading')]")
    private WebElement loadingIndicator;


    // ========== ГЕТТЕРЫ ==========

    public WebElement getPromptTextArea() {
        try {
            return promptTextAreaByPlaceholder;
        } catch (Exception e) {
            try {
                return promptTextAreaByName;
            } catch (Exception ex) {
                return promptTextAreaByAutocomplete;
            }
        }
    }

    public WebElement getSendButton() {
        // Возвращаем активную кнопку (не disabled)
        try {
            return sendButtonActive;
        } catch (Exception e) {
            try {
                return sendButtonByRole;
            } catch (Exception ex) {
                return sendButtonByClass;
            }
        }
    }

    public WebElement getPromptTextAreaByPlaceholder() {
        return promptTextAreaByPlaceholder;
    }

    public WebElement getPromptTextAreaByName() {
        return promptTextAreaByName;
    }

    public WebElement getSendButtonByRole() {
        return sendButtonByRole;
    }

    public WebElement getSendButtonByCss() {
        return sendButtonByCss;
    }

    public WebElement getSendButtonActive() {
        return sendButtonActive;
    }

    public WebElement getResponseArea() {
        return responseArea;
    }

    public WebElement getLastMessage() {
        return lastMessage;
    }

    public WebElement getLoadingIndicator() {
        return loadingIndicator;
    }
}