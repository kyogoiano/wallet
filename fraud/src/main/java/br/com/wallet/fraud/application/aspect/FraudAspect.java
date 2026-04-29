package br.com.wallet.fraud.application.aspect;

import br.com.wallet.fraud.application.FraudService;
import br.com.wallet.fraud.domain.context.FraudContext;
import br.com.wallet.fraud.exceptions.FraudBlockedException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static br.com.wallet.fraud.domain.FraudDecision.BLOCK;

@Aspect
@Component
public class FraudAspect {


    private static final Logger log = LoggerFactory.getLogger(FraudAspect.class);

    private final FraudService fraudService;

    public FraudAspect(FraudService fraudService) {
        this.fraudService = fraudService;
    }

    @Around("@annotation(FraudCheck)")
    public Object checkFraud(@NonNull final ProceedingJoinPoint pjp) throws Throwable {

        final var ctx = extractContext(pjp);

        final var decision = fraudService.check(ctx);

        if (decision == BLOCK) {
            log.warn("Fraud BLOCKED operationId={}, userId={}", ctx.operationId(), ctx.userId());
            throw new FraudBlockedException(ctx.operationId(), ctx.userId(), ctx.targetUserId());
        }

        try {
            return pjp.proceed();
        } catch (Throwable ex) {
            log.error("Throwable caught in aspect: {}", ex.getMessage());
            throw ex;
        }
    }

    private @NonNull FraudContext extractContext(@NonNull final ProceedingJoinPoint pjp) {
        for (final Object arg : pjp.getArgs()) {
            if (arg instanceof FraudContext ctx) {
                log.debug("Aspect FraudContext operation id: {}", ctx.operationId());
                return ctx;
            }
        }
        throw new IllegalArgumentException("No FraudContext found in arguments");
    }
}
