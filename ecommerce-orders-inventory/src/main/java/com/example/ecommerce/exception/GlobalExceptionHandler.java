package com.example.ecommerce.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized, console-level exception handling.
 *
 * This is a plain console application, not a Spring MVC app, so there is no
 * DispatcherServlet request pipeline and therefore no @ExceptionHandler /
 * @ControllerAdvice to hook into. Instead, every console menu action is
 * routed through GlobalExceptionHandler.handle(...) so that:
 *   - a known business-rule violation is logged and shown to the user as a
 *     friendly, single-line message,
 *   - any other unexpected exception is logged with its stack trace but
 *     never crashes the JVM,
 *   - control always returns to the menu loop afterwards.
 */
public final class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private GlobalExceptionHandler() {
    }

    public static void handle(Runnable action) {
        try {
            action.run();
        } catch (ProductNotFoundException | CustomerNotFoundException
                 | OrderNotFoundException | InsufficientInventoryException
                 | InvalidOrderException e) {
            log.warn("Business rule violation: {}", e.getMessage());
            printError(e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid input: {}", e.getMessage());
            printError(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while handling console action", e);
            printError("An unexpected error occurred. Please try again.");
        }
    }

    private static void printError(String message) {
        System.out.println();
        System.out.println("ERROR: " + message);
        System.out.println();
    }
}
