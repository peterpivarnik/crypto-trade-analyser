package com.psw.cta.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Aspect
@Component
class TimeAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeAspect.class);

    @Around("@annotation(Time)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        final LocalDateTime startTime = LocalDateTime.now();
        LOGGER.info(methodName + " started at: " + startTime);
        final Object proceed = joinPoint.proceed();
        final LocalDateTime endTime = LocalDateTime.now();
        LOGGER.info(methodName + " ended at: " + endTime);
        final Duration duration = Duration.between(startTime, endTime);
        LOGGER.info(methodName + " execution  tooks " + duration.toMillis() + " miliseconds");
        return proceed;
    }
}
