package com.innowise.orderservice.aspect;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import com.innowise.orderservice.exception.conflict.RequestAlreadyProcessingException;
import com.innowise.orderservice.security.SecurityUtils;

@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${idempotency.processing-ttl-minutes:5}")
    private long processingTtlMinutes;
    
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
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

        String path = request.getRequestURI();
        String method = request.getMethod();
        
        String redisKey = REDIS_KEY_NAMESPACE + method + ":" + path + ":" + userId + ":" + idempotencyKey;

        String requestHash = buildRequestHash(request);

        Boolean isFirst = redisTemplate.opsForValue()
            .setIfAbsent(redisKey,
                    buildProcessingValue(requestHash),
                    Duration.ofMinutes(processingTtlMinutes));

        if (!Boolean.TRUE.equals(isFirst)) {

            String saved = redisTemplate.opsForValue().get(redisKey);

            if (saved == null) {
                throw new RequestAlreadyProcessingException("Idempotency state is invalid. Please retry.");
            }
            
            IdempotencyRecord record = objectMapper.readValue(saved, IdempotencyRecord.class);

            if (!record.getRequestHash().equals(requestHash)) {
                throw new IllegalArgumentException("Idempotency-Key reused with different request");
            }

            if ("PROCESSING".equals(record.getStatus())) {
                throw new RequestAlreadyProcessingException("Request is already processing");
            }

            IdempotencyResponse resp = record.getResponse();

            return ResponseEntity
                    .status(resp.getStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(resp.getBody());
        }

        try {
            Object result = joinPoint.proceed();

            int status = 200;
            Object body = result;

            if (result instanceof ResponseEntity<?> responseEntity) {
                status = responseEntity.getStatusCode().value();
                body = responseEntity.getBody();
            }

            String bodyString = objectMapper.writeValueAsString(body);

            IdempotencyRecord record = IdempotencyRecord.done(
                    requestHash,
                    new IdempotencyResponse(status, bodyString)
            );

            redisTemplate.opsForValue().set(
                    redisKey,
                    objectMapper.writeValueAsString(record),
                    Duration.ofMinutes(idempotent.ttlMinutes())
            );

            return result;

        } catch (Throwable e) {
            redisTemplate.delete(redisKey);
            throw e;
        }
    }

    private String buildProcessingValue(String requestHash) {
        IdempotencyRecord record = IdempotencyRecord.processing(requestHash);
        return objectMapper.writeValueAsString(record);
    }

    private String buildRequestHash(HttpServletRequest request) {
        try {
            String query = request.getQueryString();
            String raw = request.getMethod() + "\n"
                    + request.getRequestURI() + "\n"
                    + (query != null ? query : "");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build request hash", e);
        }
    }

}
