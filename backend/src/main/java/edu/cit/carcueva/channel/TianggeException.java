package edu.cit.carcueva.channel;

class TianggeException extends RuntimeException {
    enum Kind {
        TRANSIENT,
        AUTH,
        CONFLICT,
        NOT_FOUND,
        REJECTED
    }

    private final Kind kind;
    private final int httpStatus;
    private final String code;

    private TianggeException(Kind kind, int httpStatus, String code, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.httpStatus = httpStatus;
        this.code = code;
    }

    static TianggeException fromResponse(int httpStatus, String code, String message) {
        return new TianggeException(classify(httpStatus), httpStatus, code, message, null);
    }

    static TianggeException transport(Throwable cause) {
        return new TianggeException(Kind.TRANSIENT, 0, null,
                "No usable response (" + cause.getClass().getSimpleName() + ": " + cause.getMessage() + ")", cause);
    }

    static TianggeException malformed(String detail) {
        return new TianggeException(Kind.TRANSIENT, 0, null, "Malformed response: " + detail, null);
    }

    private static Kind classify(int httpStatus) {
        if (httpStatus == 401) {
            return Kind.AUTH;
        }
        if (httpStatus == 409) {
            return Kind.CONFLICT;
        }
        if (httpStatus == 404) {
            return Kind.NOT_FOUND;
        }
        if (httpStatus == 408 || httpStatus == 429 || httpStatus >= 500) {
            return Kind.TRANSIENT;
        }
        return Kind.REJECTED;
    }

    Kind kind() {
        return kind;
    }

    String code() {
        return code;
    }

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
