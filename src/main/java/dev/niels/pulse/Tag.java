package dev.niels.pulse;

/** The type tags of a tagstruct ({@code pulsecore/tagstruct.h}). */
final class Tag {
    static final int STRING = 't';
    static final int STRING_NULL = 'N';
    static final int U32 = 'L';
    static final int U8 = 'B';
    static final int U64 = 'R';
    static final int S64 = 'r';
    static final int SAMPLE_SPEC = 'a';
    static final int ARBITRARY = 'x';
    static final int BOOLEAN_TRUE = '1';
    static final int BOOLEAN_FALSE = '0';
    static final int TIMEVAL = 'T';
    static final int USEC = 'U';
    static final int CHANNEL_MAP = 'm';
    static final int CVOLUME = 'v';
    static final int PROPLIST = 'P';
    static final int VOLUME = 'V';
    static final int FORMAT_INFO = 'f';

    private Tag() {
    }
}
