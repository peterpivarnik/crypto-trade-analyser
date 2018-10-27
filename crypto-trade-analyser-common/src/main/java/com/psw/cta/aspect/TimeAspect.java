package com.psw.cta.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Aspect
@Component
@Slf4j
class TimeAspect {

    @Around("@annotation(Time)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        final LocalDateTime startTime = LocalDateTime.now();
        log.info("Starting at: " + startTime);
        final Object proceed = joinPoint.proceed();
        final LocalDateTime endTime = LocalDateTime.now();
        log.info("Ending at: " + endTime);
        final Duration duration = Duration.between(startTime, endTime);

        log.info(String.format("Execution of method %s tooks %d miliseconds",
                               joinPoint.getSignature().toShortString(),
                               duration.toMillis()));
        return proceed;
    }
}
