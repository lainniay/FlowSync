package hgc.flowsync.common.error;

public record FieldViolation(String field, String code, String message) {
}
