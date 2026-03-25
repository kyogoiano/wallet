package br.com.wallet.interfaces.rest.exception;

public record ApiError(ErrorCode code, String message) {}
