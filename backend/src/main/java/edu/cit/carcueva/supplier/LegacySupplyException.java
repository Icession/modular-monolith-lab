package edu.cit.carcueva.supplier;

import java.time.Duration;

/**
 * Anything that went wrong talking to LegacySupply, sorted into a Kind
 * that tells the adapter what to do about it. Package-private: this type
 * (and LegacySupply's error codes inside it) never leaves the module.
 */
class LegacySupplyException extends RuntimeException {

    enum Kind {
        /** Timeout, connection failure, 5xx, garbage response - worth retrying. */
        TRANSIENT,
        /** Session missing / not recognised / expired - sign in again, then retry. */
        AUTH,
        /** Our Client ID or API key was rejected (E-AUTH-01) - retrying won't help until config is fixed. */
        CREDENTIALS,
        /** Request quota exceeded (429) - back off, don't hammer. */
        RATE_LIMITED,
        /** Request id reused with different content (409). */
        CONFLICT,
        /** PO not found (404). */
        NOT_FOUND,
        /** Any other 4xx - the request itself is wrong (bad item, bad quantity...). */
        REJECTED
    }

    private final Kind kind;
    private final int httpStatus;
    private final String code;

    private LegacySupplyException(Kind kind, int httpStatus, String code, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.httpStatus = httpStatus;
        this.code = code;
    }

    static LegacySupplyException fromResponse(int httpStatus, String body) {
        String[] codeAndMessage = XmlCodec.readError(body);
        String code = codeAndMessage[0];
        String message = codeAndMessage[1];
        return new LegacySupplyException(classify(httpStatus, code), httpStatus, code, message, null);
    }

    static LegacySupplyException transport(Throwable cause) {
        return new LegacySupplyException(Kind.TRANSIENT, 0, null,
                "No usable response (" + cause.getClass().getSimpleName() + ": " + cause.getMessage() + ")", cause);
    }

    static LegacySupplyException malformed(String detail) {
        return new LegacySupplyException(Kind.TRANSIENT, 0, null, "Malformed response: " + detail, null);
    }

    static LegacySupplyException coolingDown(Duration remaining) {
        return new LegacySupplyException(Kind.TRANSIENT, 0, null,
                "Not calling LegacySupply for another " + remaining.toSeconds() + "s (local cool-down)", null);
    }

    private static Kind classify(int httpStatus, String code) {
        if (httpStatus == 401) {
            return "E-AUTH-01".equals(code) ? Kind.CREDENTIALS : Kind.AUTH;
        }
        if (httpStatus == 429) {
            return Kind.RATE_LIMITED;
        }
        if (httpStatus == 409) {
            return Kind.CONFLICT;
        }
        if (httpStatus == 404) {
            return Kind.NOT_FOUND;
        }
        if (httpStatus == 408 || httpStatus >= 500) {
            return Kind.TRANSIENT;
        }
        return Kind.REJECTED;
    }

    Kind kind() {
        return kind;
    }

    /** Short, loggable description, e.g. "HTTP 503 E-SYS-99 Service unavailable. Try later." */
    String describe() {
        StringBuilder sb = new StringBuilder();
        if (httpStatus > 0) {
            sb.append("HTTP ").append(httpStatus).append(' ');
        }
        if (code != null) {
            sb.append(code).append(' ');
        }
        sb.append(getMessage());
        return sb.toString().trim();
    }
}
