package com.innowise.internship.orderservice.aspect;

import java.time.Duration;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import com.innowise.internship.orderservice.exception.RequestAlreadyProcessingException;
import com.innowise.internship.orderservice.security.SecurityUtils;

@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${idempotency.processing-ttl-minutes:5}")
    private long processingTtlMinutes;
    
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String PROCESSING_STATUS = "PROCESSING";
    private static final String REDIS_KEY_NAMESPACE = "idempotency:";

    @Around("@annotation(idempotent)")
    public Object checkIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is missing");
        }

        String userId = SecurityUtils.getCurrentUser()
                .map(u -> u.userId().toString())
                .orElse("anon");
        String redisKey = REDIS_KEY_NAMESPACE + idempotent.prefix() + ":" + userId + ":" + idempotencyKey;

        Boolean isFirstRequest = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, PROCESSING_STATUS, Duration.ofMinutes(processingTtlMinutes));

        if (!isFirstRequest) {
            String savedValue = redisTemplate.opsForValue().get(redisKey);

            if (PROCESSING_STATUS.equals(savedValue)) {
                throw new RequestAlreadyProcessingException("Request is already processing. Please wait.");
            }

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            JavaType javaType = objectMapper.getTypeFactory()
                    .constructType(signature.getMethod().getGenericReturnType());

            return objectMapper.readValue(savedValue, javaType);
        }

        try {
            Object result = joinPoint.proceed();

            String jsonResult = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(redisKey, jsonResult, Duration.ofMinutes(idempotent.ttlMinutes()));

            return result;
        } catch (Throwable e) {
            redisTemplate.delete(redisKey);
            throw e;
        }
    }
}
