package com.cadence.auth_service.consumers;

import com.cadence.auth_service.model.UserStatus;
import com.cadence.auth_service.producers.UserUpdatedProducer;
import com.cadence.auth_service.repository.UserRepository;
import com.cadence.events.MediaUpdatedEvent;
import com.cadence.events.Topics;
import com.cadence.messaging.inbox.IdempotentEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Applies avatar uploads stored by catalog-service to users.profile_url, then republishes the user snapshot.
 * A user may only change their own avatar (admins any); checked again here because this service owns the row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaUpdatedConsumer {
    static final String HANDLER = "auth.apply-avatar";

    private final IdempotentEventHandler eventHandler;
    private final UserRepository userRepository;
    private final UserUpdatedProducer userUpdatedProducer;

    @KafkaListener(topics = Topics.MEDIA_UPDATED_TOPIC, groupId = "auth-service")
    public void handleMediaUpdated(String message) {
        eventHandler.handle(HANDLER, message, MediaUpdatedEvent.class, envelope -> {
            MediaUpdatedEvent media = envelope.payload();
            if (media.target() != MediaUpdatedEvent.Target.USER_AVATAR) {
                return;
            }
            if (!media.requestedByAdmin() && !media.targetId().equals(media.requestedBy())) {
                log.warn("Ignoring avatar update for user {} requested by {}", media.targetId(), media.requestedBy());
                return;
            }
            userRepository.findById(media.targetId()).ifPresentOrElse(user -> {
                // An upload still in flight when the account is being deleted must not re-create the user's
                // replica in catalog after it was purged.
                if (user.getStatus() != UserStatus.ACTIVE) {
                    log.info("Ignoring avatar update for user {} in status {}", user.getId(), user.getStatus());
                    return;
                }
                user.setProfileUrl(media.url());
                userRepository.save(user);
                userUpdatedProducer.send(user);
            }, () -> log.warn("Ignoring avatar update for missing user {}", media.targetId()));
        });
    }
}
