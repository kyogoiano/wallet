package br.com.wallet.edge.api;

import java.util.UUID;

/**
 * SPI for authenticating and authorizing SSE operation streaming clients (TASK-5.11, REQ-EDG-019).
 */
@FunctionalInterface
public interface OperationAuthorizationProvider {

    /**
     * Evaluates whether the given tenant or principal is authorized to subscribe to the operation stream.
     *
     * @param operationId the operation identifier to be streamed
     * @param tenantId    the tenant/principal identifier extracted from the request
     * @return true if authorized; false otherwise
     */
    boolean isAuthorized(UUID operationId, String tenantId);
}
