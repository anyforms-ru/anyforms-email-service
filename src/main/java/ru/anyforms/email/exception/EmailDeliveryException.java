package ru.anyforms.email.exception;

public class EmailDeliveryException extends RuntimeException {

    private final boolean permanent;

    public EmailDeliveryException(String message, boolean permanent) {
        super(message);
        this.permanent = permanent;
    }

    public EmailDeliveryException(String message, boolean permanent, Throwable cause) {
        super(message, cause);
        this.permanent = permanent;
    }

    public boolean isPermanent() {
        return permanent;
    }
}
