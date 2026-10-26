package com.prisma.api.aspect;

import com.prisma.api.enums.SeveridadAudit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditableAction {
    String action();
    SeveridadAudit severity() default SeveridadAudit.INFO;
    String resourceType();
    boolean requiresJustification() default false;
}
