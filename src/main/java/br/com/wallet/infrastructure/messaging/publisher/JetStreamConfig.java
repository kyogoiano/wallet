package br.com.wallet.infrastructure.messaging.publisher;

import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.time.Duration;

public interface JetStreamConfig {

    default void ensureStream(@NonNull final JetStreamManagement jsm,
                              @NonNull final String streamName,
                              @NonNull final String subjects,
                              @NonNull final Duration retention)
            throws IOException, JetStreamApiException {

        final var config = StreamConfiguration.builder()
                .name(streamName)
                .subjects(subjects)
                .retentionPolicy(RetentionPolicy.Limits)
                .maxAge(retention)
                .storageType(StorageType.File)
                .duplicateWindow(Duration.ofMinutes(5))
                .build();

        try {
            final StreamInfo streamInfo = jsm.getStreamInfo(streamName);
            if (streamInfo != null) {
                jsm.updateStream(config);
            }
        } catch (JetStreamApiException e) {
            if (e.getApiErrorCode() == 10059 || e.getApiErrorCode() == 404) {
                jsm.addStream(config);
            } else {
                throw e;
            }
        }
    }
}
