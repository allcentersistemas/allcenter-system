package com.allcenter.modulesystem.support;

import com.allcenter.modulesystem.exception.BadRequestException;
import java.util.regex.Pattern;

public final class PasswordPolicy {

    private static final Pattern STRONG =
            Pattern.compile("^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,128}$");

    public static final String MESSAGE =
            "La contraseña debe tener al menos 8 caracteres, una letra mayúscula, un número y un símbolo";

    private PasswordPolicy() {}

    public static void requireStrong(String password) {
        if (password == null || !STRONG.matcher(password).matches()) {
            throw new BadRequestException(MESSAGE);
        }
    }
}
