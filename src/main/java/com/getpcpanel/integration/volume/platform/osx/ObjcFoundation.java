package com.getpcpanel.integration.volume.platform.osx;

import javax.annotation.Nullable;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * Minimal Objective-C runtime bridge (message sending + Foundation collections). CoreAudio's process-tap
 * API takes an ObjC {@code CATapDescription} and a {@code CFDictionaryRef}; Foundation's
 * {@code NSMutableDictionary}/{@code NSArray} are toll-free bridged to their CF counterparts, so building
 * everything through {@code objc_msgSend} avoids binding the CoreFoundation collection-callback globals.
 *
 * <p>{@code objc_msgSend} is declared once per argument shape: it has no fixed C prototype (the callee
 * defines the signature), so each overload below invokes it with the exact fixed-arg calling convention
 * of the selectors it is used for — correct on both x86_64 and arm64.
 */
final class ObjcFoundation {
    private ObjcFoundation() {
    }

    interface ObjcLib extends Library {
        ObjcLib INSTANCE = Native.load("objc", ObjcLib.class);

        Pointer objc_getClass(String name);

        Pointer sel_registerName(String name);

        Pointer objc_msgSend(Pointer receiver, Pointer selector);

        Pointer objc_msgSend(Pointer receiver, Pointer selector, Pointer arg);

        Pointer objc_msgSend(Pointer receiver, Pointer selector, Pointer arg1, Pointer arg2);

        Pointer objc_msgSend(Pointer receiver, Pointer selector, String arg);

        Pointer objc_msgSend(Pointer receiver, Pointer selector, int arg);

        Pointer objc_msgSend(Pointer receiver, Pointer selector, long arg);

        Pointer objc_autoreleasePoolPush();

        void objc_autoreleasePoolPop(Pointer pool);
    }

    private static ObjcLib objc() {
        return ObjcLib.INSTANCE;
    }

    static Pointer cls(String name) {
        return objc().objc_getClass(name);
    }

    static Pointer sel(String name) {
        return objc().sel_registerName(name);
    }

    static Pointer msg(Pointer receiver, String selector) {
        return objc().objc_msgSend(receiver, sel(selector));
    }

    static Pointer msg(Pointer receiver, String selector, Pointer arg) {
        return objc().objc_msgSend(receiver, sel(selector), arg);
    }

    static Pointer msg(Pointer receiver, String selector, Pointer arg1, Pointer arg2) {
        return objc().objc_msgSend(receiver, sel(selector), arg1, arg2);
    }

    static Pointer msg(Pointer receiver, String selector, int arg) {
        return objc().objc_msgSend(receiver, sel(selector), arg);
    }

    static Pointer msg(Pointer receiver, String selector, long arg) {
        return objc().objc_msgSend(receiver, sel(selector), arg);
    }

    /** Convenience constructors return autoreleased objects; wrap creation bursts so they are actually freed. */
    static Pointer autoreleasePoolPush() {
        return objc().objc_autoreleasePoolPush();
    }

    static void autoreleasePoolPop(Pointer pool) {
        objc().objc_autoreleasePoolPop(pool);
    }

    static void release(@Nullable Pointer object) {
        if (object != null) {
            msg(object, "release");
        }
    }

    /** Autoreleased {@code NSString} from a Java string. */
    static Pointer nsString(String value) {
        return objc().objc_msgSend(cls("NSString"), sel("stringWithUTF8String:"), value);
    }

    /** Autoreleased {@code NSNumber} carrying an unsigned 32-bit value (e.g. an {@code AudioObjectID}). */
    static Pointer nsNumber(int value) {
        return objc().objc_msgSend(cls("NSNumber"), sel("numberWithUnsignedInt:"), value);
    }

    /** Autoreleased boolean {@code NSNumber} ({@code CFBoolean}-compatible for plist-style dictionaries). */
    static Pointer nsBool(boolean value) {
        return objc().objc_msgSend(cls("NSNumber"), sel("numberWithBool:"), value ? 1 : 0);
    }

    /** Autoreleased single-element {@code NSArray}. */
    static Pointer nsArray(Pointer element) {
        return objc().objc_msgSend(cls("NSArray"), sel("arrayWithObject:"), element);
    }

    /** Autoreleased empty {@code NSMutableDictionary}. */
    static Pointer nsMutableDictionary() {
        return msg(cls("NSMutableDictionary"), "dictionary");
    }

    static void put(Pointer dictionary, String key, Pointer value) {
        msg(dictionary, "setObject:forKey:", value, nsString(key));
    }

    /** Java string from an {@code NSString} (via its UTF-8 C string, valid within the current pool). */
    @Nullable
    static String javaString(@Nullable Pointer nsString) {
        if (nsString == null) {
            return null;
        }
        var utf8 = msg(nsString, "UTF8String");
        return utf8 == null ? null : utf8.getString(0);
    }
}
