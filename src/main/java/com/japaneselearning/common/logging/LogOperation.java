package com.japaneselearning.common.logging;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;
import org.jboss.logging.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.Inherited;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Inherited
@InterceptorBinding
@Retention(RUNTIME)
@Target({METHOD, TYPE})
public @interface LogOperation {
    @Nonbinding
    String value();

    @Nonbinding
    // Alias only for a Long parameter named "id"; empty omits that ambiguous parameter.
    String id() default "";

    @Nonbinding
    // Opt-in human-readable lifecycle description; existing callers retain their original events and levels.
    String description() default "";

    @Nonbinding
    // Only allowlisted numeric fields are substituted in {field} placeholders.
    String successMessage() default "";

    @Nonbinding
    Logger.Level level() default Logger.Level.INFO;
}
