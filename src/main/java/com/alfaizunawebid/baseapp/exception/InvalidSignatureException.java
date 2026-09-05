package com.alfaizunawebid.baseapp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception yang dilempar saat verifikasi HMAC signature gagal.
 * Otomatis mengembalikan HTTP status 401 Unauthorized ke pengirim request.
 * ---
 * Thrown when HMAC signature verification fails.
 * Automatically returns HTTP 401 Unauthorized status to the caller.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidSignatureException extends RuntimeException {

    public InvalidSignatureException(String message) {
        super(message);
    }

    public InvalidSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
