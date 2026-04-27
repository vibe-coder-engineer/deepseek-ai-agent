package ru.sibgatulinanton.cli;

import java.util.Scanner;

public class ConsoleInput {

    private final Scanner scanner;

    public ConsoleInput(Scanner scanner) {
        this.scanner = scanner;
    }

    public String askLine(String prompt) {
        System.out.print(prompt);
        try {
            return scanner.nextLine();
        } catch (Exception e) {
            return "";
        }
    }

    public void waitEnter() {
        try {
            scanner.nextLine();
        } catch (Exception ignored) {
        }
    }
}
