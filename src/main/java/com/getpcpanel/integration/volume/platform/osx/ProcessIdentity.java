package com.getpcpanel.integration.volume.platform.osx;

import java.time.Instant;

import javax.annotation.Nullable;

/** A PID is reusable; the start instant scopes audio state to one process lifetime. */
record ProcessIdentity(int pid, Instant startedAt) {
    static @Nullable ProcessIdentity find(int pid) {
        if (pid <= 0) {
            return null;
        }
        return ProcessHandle.of(pid).filter(ProcessHandle::isAlive)
                .flatMap(process -> process.info().startInstant())
                .map(start -> new ProcessIdentity(pid, start)).orElse(null);
    }
}
