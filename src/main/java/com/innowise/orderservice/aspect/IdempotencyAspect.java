package com.innowise.orderservice.aspect;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
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
    
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
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

        String requestHash = buildRequestHash(joinPoint, request);

        Boolean isFirst = redisTemplate.opsForValue()
            .setIfAbsent(redisKey,
                    buildProcessingValue(requestHash),
                    Duration.ofMinutes(processingTtlMinutes));

        if (!Boolean.TRUE.equals(isFirst)) {

            String saved = redisTemplate.opsForValue().get(redisKey);

            if (saved == null) {
                throw new RequestAlreadyProcessingException("Idempotency state is invalid. Please retry.");
            }
            
            IdempotencyRecord idempotencyRecord = objectMapper.readValue(saved, IdempotencyRecord.class);

            if (!idempotencyRecord.getRequestHash().equals(requestHash)) {
                throw new IllegalArgumentException("Idempotency-Key reused with different request");
            }

            if ("PROCESSING".equals(idempotencyRecord.getStatus())) {
                throw new RequestAlreadyProcessingException("Request is already processing");
            }

            IdempotencyResponse resp = idempotencyRecord.getResponse();

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

            IdempotencyRecord idempotencyRecord = IdempotencyRecord.done(
                    requestHash,
                    new IdempotencyResponse(status, bodyString)
            );

            redisTemplate.opsForValue().set(
                    redisKey,
                    objectMapper.writeValueAsString(idempotencyRecord),
                    Duration.ofMinutes(idempotent.ttlMinutes())
            );

            return result;

        } catch (Throwable e) {
            redisTemplate.delete(redisKey);
            throw e;
        }
    }

    private String buildProcessingValue(String requestHash) {
        IdempotencyRecord idempotencyRecord = IdempotencyRecord.processing(requestHash);
        return objectMapper.writeValueAsString(idempotencyRecord);
    }

    private String buildRequestHash(ProceedingJoinPoint joinPoint, HttpServletRequest request) {
        String query = request.getQueryString();
        List<Object> payloadArgs = Arrays.stream(joinPoint.getArgs())
                .filter(arg -> !(arg instanceof ServletRequest))
                .filter(arg -> !(arg instanceof ServletResponse))
                .filter(arg -> !(arg instanceof BindingResult))
                .toList();

        String argsCanonical;
        try {
            argsCanonical = objectMapper.writeValueAsString(payloadArgs);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize request arguments for idempotency hash", e);
        }

        String raw = request.getMethod() + "\n"
                + request.getRequestURI() + "\n"
                + (query != null ? query : "") + "\n"
                + argsCanonical;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }
    }

}
