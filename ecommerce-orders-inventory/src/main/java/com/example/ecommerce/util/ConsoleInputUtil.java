package com.example.ecommerce.util;

import java.math.BigDecimal;
import java.util.Scanner;

/**
 * Small helper that keeps the console input loop robust against bad input
 * (non-numeric text where a number is expected, etc.) so the application
 * never crashes just because the user mistyped something.
 */
public class ConsoleInputUtil {

    private final Scanner scanner;

    public ConsoleInputUtil(Scanner scanner) {
        this.scanner = scanner;
    }

    public String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    public int readInt(String prompt) {
        while (true) {
            String value = readLine(prompt);
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }

    public Long readLong(String prompt) {
        while (true) {
            String value = readLine(prompt);
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid numeric ID.");
            }
        }
    }

    public BigDecimal readBigDecimal(String prompt) {
        while (true) {
            String value = readLine(prompt);
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }
}
