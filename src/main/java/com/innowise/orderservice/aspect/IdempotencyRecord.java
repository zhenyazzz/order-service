package com.innowise.orderservice.aspect;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {

    private String status; 
    private String requestHash;
    private IdempotencyResponse response;

    public static IdempotencyRecord processing(String requestHash) {
        return new IdempotencyRecord("PROCESSING", requestHash, null);
    }

    public static IdempotencyRecord done(String requestHash, IdempotencyResponse response) {
        return new IdempotencyRecord("DONE", requestHash, response);
    }
}
