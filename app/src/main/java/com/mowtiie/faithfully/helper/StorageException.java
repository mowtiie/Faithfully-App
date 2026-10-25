package com.mowtiie.faithfully.helper;

import java.io.IOException;

/** A failed Worker call; the message is written for the user and safe to show in a toast. */
public class StorageException extends IOException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
