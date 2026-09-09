package com.example.ui.internal;

/**
 * The account id the current request is acting for, so the outbound
 * {@link InternalAuthClientInterceptor} can put it in the signed header.
 * Set by {@link ActingUserFilter} from the session, cleared after the request.
 */
public final class ActingUser {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private ActingUser() {
    }

    public static void set(Long accountId) {
        CURRENT.set(accountId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
