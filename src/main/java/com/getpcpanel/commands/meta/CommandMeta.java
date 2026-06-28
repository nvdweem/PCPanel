package com.getpcpanel.commands.meta;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a command as user-assignable and carries the picker/registry metadata that used to be
 * hand-maintained in the frontend {@code command-catalog.ts}. A build step generates the frontend
 * command registry from these annotations (see {@code docs/feature-module-structure.md}), so the
 * authoritative list of which commands exist and how they are labelled/categorised/iconified lives
 * in Java, next to each command, in its own feature package.
 *
 * <p>Only the registry-level metadata is here; the per-command <em>field editors</em> remain in the
 * frontend because they are Angular UI, not data. A command without {@code @CommandMeta} still works
 * (it just is not offered in the assignment picker).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CommandMeta {
    /** Human-readable picker label. */
    String label();

    /** Picker grouping. */
    CommandCategory category();

    /** Control slots the command can be assigned to (dial / button). */
    CommandKind[] kinds();

    /** Owning integration id (e.g. {@code "obs"}) for {@code category == integration}; blank for core. */
    String integration() default "";

    /** Icon name; must be one of the frontend {@code IconName} set. */
    String icon();

    /**
     * Historical {@code _type} discriminator values older profiles.json may contain (typically the
     * command's previous fully-qualified class name). The current id is the {@code @JsonTypeName};
     * these are accepted on read only, mapped back via a DeserializationProblemHandler in
     * {@link com.getpcpanel.commands.CommandSubtypeRegistrar}, so old saves keep loading while new
     * saves are written with the nice current id.
     */
    String[] legacyIds() default {};
}
