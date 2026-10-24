package com.proyecto.servicios.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida que la fecha de nacimiento corresponda a una persona con al menos {@link #edadMinima()} anos.
 * Un valor nulo se considera valido (usar junto con {@code @NotNull}).
 */
@Documented
@Constraint(validatedBy = MayorDeEdadValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MayorDeEdad {

    String message() default "El cliente debe ser mayor de edad (18 años o más).";

    int edadMinima() default 18;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
