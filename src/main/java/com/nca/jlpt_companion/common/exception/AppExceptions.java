package com.nca.jlpt_companion.common.exception;

/**
 * Kumpulan exception runtime ringan untuk mapping error yang semantik.
 * Ditangani oleh GlobalExceptionhandler.
 */
public final class AppExceptions {

    private AppExceptions() {}

    /** 409 Conflict (contoh: email sudah terpakai) */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
        public ConflictException(String message, Throwable cause) { super(message, cause); }
    }

    /** 401 Unauthorized (contoh: login gagal) */
    public static class AuthFailedException extends RuntimeException {
        public AuthFailedException(String message) { super(message); }
        public AuthFailedException(String message, Throwable cause) { super(message, cause); }
    }
}
