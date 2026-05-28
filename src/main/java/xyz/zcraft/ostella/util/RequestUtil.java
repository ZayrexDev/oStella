package xyz.zcraft.ostella.util;

import io.javalin.http.Context;
import xyz.zcraft.ostella.network.ApiException;
import xyz.zcraft.ostella.network.ErrorCode;

import java.util.Arrays;
import java.util.Objects;

public class RequestUtil {
    public static int requireInt(Context context, String param) throws ApiException {
        try {
            return Integer.parseInt(Objects.requireNonNull(context.queryParam(param)));
        } catch (Exception e) {
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Invalid parameter: " + param);
        }
    }

    public static double optionalDouble(Context context, String param) throws ApiException {
        try {
            final String obj = context.queryParam(param);
            if (obj == null) return Double.NaN;
            return Double.parseDouble(obj);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Invalid parameter: " + param);
        }
    }

    public static String requireString(Context context, String param) throws ApiException {
        final String s = context.queryParam(param);
        if (s != null && !s.isBlank()) {
            return s;
        } else {
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Missing or empty parameter: " + param);
        }
    }

    public static String optionalString(Context context, String param) throws ApiException {
        return context.queryParam(param);
    }

    public static long requireLong(Context context, String param) throws ApiException {
        try {
            return Long.parseLong(Objects.requireNonNull(context.queryParam(param)));
        } catch (Exception e) {
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Invalid parameter: " + param);
        }
    }

    public static long requirePathLong(Context context, String param) throws ApiException {
        try {
            final String s = context.pathParam(param);
            return Long.parseLong(Objects.requireNonNull(s));
        } catch (Exception e) {
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Invalid path parameter: " + param);
        }
    }

    public static int requirePathInt(Context context, String param) throws ApiException {
        try {
            final String s = context.pathParam(param);
            return Integer.parseInt(Objects.requireNonNull(s));
        } catch (Exception e) {
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Invalid path parameter: " + param);
        }
    }

    public static String requireStringFrom(Context context, String param, String... values) throws ApiException {
        final String s = context.queryParam(param);

        if (s != null && !s.isBlank()) {
            if (Arrays.asList(values).contains(s)) {
                return s;
            } else {
                throw new ApiException(
                        ErrorCode.ILLEGAL_ARGUMENT,
                        "Invalid parameter: " + param + ", should be one of " + Arrays.toString(values)
                );
            }
        }

        throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Missing or empty parameter: " + param);
    }
}
