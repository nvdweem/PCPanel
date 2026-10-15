package com.getpcpanel.integration.volume.platform.osx;

import java.time.Instant;

import javax.annotation.Nullable;

/** A PID is reusable; the start instant scopes audio state to one process lifetime. */
record ProcessIdentity(int pid, Instant startedAt) {
    /** Where {@link OsxProcessTapService} looks processes up; {@link #SYSTEM} asks the OS. */
    interface Lookup {
        Lookup SYSTEM = new Lookup() {
            @Override
            public @Nullable ProcessIdentity find(int pid) {
                return ProcessIdentity.find(pid);
            }

            @Override
            public void onExit(ProcessIdentity identity, Runnable action) {
                ProcessIdentity.onExit(identity, action);
            }
        };

        @Nullable ProcessIdentity find(int pid);

        void onExit(ProcessIdentity identity, Runnable action);
    }

    static @Nullable ProcessIdentity find(int pid) {
        if (pid <= 0) {
            return null;
        }
        return ProcessHandle.of(pid).filter(ProcessHandle::isAlive)
                .flatMap(process -> process.info().startInstant())
                .map(start -> new ProcessIdentity(pid, start)).orElse(null);
    }

    /** Watch only this lifetime; a reused PID must not postpone cleanup of the original owner. */
    static void onExit(ProcessIdentity identity, Runnable action) {
        ProcessHandle.of(identity.pid())
                .filter(process -> process.info().startInstant().filter(identity.startedAt()::equals).isPresent())
                .ifPresentOrElse(process -> process.onExit().thenRun(action), action);
    }
}
