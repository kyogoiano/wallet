package br.com.wallet.infrasctructure.utils;

import tools.jackson.databind.ObjectMapper;

public final class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
