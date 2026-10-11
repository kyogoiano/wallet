package br.com.wallet.infrastructure.rest.exception;

public record ApiError(ErrorCode code, String message) {}
