package com.getpcpanel.template;

/**
 * The control a command is running for, so a command rendering a template knows its device, control and dial
 * without every action signature carrying them. {@link com.getpcpanel.commands.PCPanelControlEvent} sets it
 * around the actions it runs; it is empty outside that.
 */
public final class TemplateContext {
    private static final ThreadLocal<TemplateScope> CURRENT = new ThreadLocal<>();

    private TemplateContext() {
    }

    public static TemplateScope current() {
        var scope = CURRENT.get();
        return scope == null ? TemplateScope.EMPTY : scope;
    }

    /** Runs {@code action} with {@code scope} as the current scope, restoring the previous one afterwards. */
    public static void run(TemplateScope scope, Runnable action) {
        var previous = CURRENT.get();
        CURRENT.set(scope);
        try {
            action.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
