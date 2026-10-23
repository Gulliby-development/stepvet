package io.github.gulliby.stepvet.base;

/** A user-facing failure with an exact diagnostic message. */
public final class Failure extends RuntimeException {
    public Failure(String message) {
        super(message);
    }
}
