package com.prisma.api.aspect;

import com.prisma.api.entity.AuditLog;
import com.prisma.api.repository.AuditLogRepository;
import com.prisma.api.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuditActionAspect {

    private static final Logger logger = LoggerFactory.getLogger(AuditActionAspect.class);
    private final AuditLogRepository auditLogRepository;

    public AuditActionAspect(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Around("@annotation(auditableAction)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, AuditableAction auditableAction) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        String resultado = "EXITOSO";
        Throwable caughtException = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            resultado = "ERROR";
            caughtException = ex;
            throw ex;
        } finally {
            try {
                recordAudit(joinPoint, auditableAction, resultado, System.currentTimeMillis() - startTime);
            } catch (Exception ex) {
                logger.error("Error al registrar rastro de auditoría: {}", ex.getMessage());
            }
        }
    }

    private void recordAudit(ProceedingJoinPoint joinPoint, AuditableAction annotation, String resultado, long durationMs) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long agenteId = 0L;
        String agenteNombre = "ANONIMO";
        String agenteEmail = "sistema@prisma.com.co";
        String agenteRol = "SISTEMA";
        Long proveedorId = null;

        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            agenteId = principal.getId();
            agenteNombre = principal.getNombreCompleto();
            agenteEmail = principal.getUsername();
            agenteRol = principal.getAuthorities().stream().findFirst().map(Object::toString).orElse("DESCONOCIDO");
            proveedorId = principal.getProveedorId();
        }

        String ipOrigen = "127.0.0.1";
        String userAgent = "Unknown";

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            ipOrigen = (forwarded != null && !forwarded.isBlank()) ? forwarded.split(",")[0] : request.getRemoteAddr();
            userAgent = request.getHeader("User-Agent") != null ? request.getHeader("User-Agent") : "Unknown";
        }

        AuditLog log = new AuditLog();
        log.setAgenteId(agenteId);
        log.setAgenteNombre(agenteNombre);
        log.setAgenteEmail(agenteEmail);
        log.setAgenteRol(agenteRol);
        log.setProveedorId(proveedorId);
        log.setTipoEvento(annotation.action());
        log.setNivelSeveridad(annotation.severity());
        log.setRecursoAfectadoTipo(annotation.resourceType());
        log.setRecursoAfectadoId("METODO:" + joinPoint.getSignature().getName());
        log.setJustificacion("Ejecución auditada de acción " + annotation.action() + " (duración: " + durationMs + "ms)");
        log.setIpOrigen(ipOrigen);
        log.setUserAgent(userAgent);
        log.setResultado(resultado);

        auditLogRepository.save(log);
    }
}
