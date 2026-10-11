package br.com.wallet.edge.api;

/**
 * Supported ingress command types with their 2-byte compact binary journal representation.
 * Specified in PLAN-000.9 Section 4.2.
 */
public enum CommandType {
    TRANSFER((short) 1),
    DEPOSIT((short) 2),
    WITHDRAW((short) 3);

    private final short code;

    CommandType(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public static CommandType fromCode(final short code) {
        for (final CommandType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown CommandType code: " + code);
    }
}
