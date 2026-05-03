package com.cadence.notification_service.services;

import com.cadence.notification_service.events.EmailVerificationEvent;
import com.cadence.notification_service.events.RecordCreatedEvent;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock MimeMessage mimeMessage;

    @InjectMocks WorkerService workerService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(workerService, "frontendUrl", "https://cadence.test/");
    }

    @Test
    void sendEmailVerificationMail_setsRecipient_subject_and_link() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        EmailVerificationEvent event = new EmailVerificationEvent("alice@example.com", "https://cadence.test/verify?token=abc");

        workerService.sendEmailVerificationMail(event);

        verify(mimeMessage).setSubject("Verify your email", "UTF-8");
        verify(mimeMessage).setRecipient(jakarta.mail.Message.RecipientType.TO, new jakarta.mail.internet.InternetAddress("alice@example.com"));
        verify(mimeMessage).setText("Click the link to verify your email:\nhttps://cadence.test/verify?token=abc", "UTF-8");
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendEmailVerificationMail_swallowsExceptions_andDoesNotPropagate() {
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("smtp down"));

        workerService.sendEmailVerificationMail(new EmailVerificationEvent("a@b.c", "link"));
    }

    @Test
    void notifyFollowersOfNewRelease_sendsOneMailPerFollower() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        RecordCreatedEvent event = new RecordCreatedEvent(
                "rec-1", "Certified Lover Boy",
                List.of("Drake"), "https://cdn/cover.jpg",
                List.of("alice@example.com", "bob@example.com", "carol@example.com")
        );

        workerService.notifyFollowersOfNewRelease(event);

        verify(mailSender, org.mockito.Mockito.times(3)).send(any(MimeMessage.class));
    }

    @Test
    void notifyFollowersOfNewRelease_sendsZeroMails_whenNoFollowers() {
        RecordCreatedEvent event = new RecordCreatedEvent(
                "rec-1", "Title", List.of("Drake"), "url", List.of()
        );

        workerService.notifyFollowersOfNewRelease(event);

        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendReleaseMail_setsHtmlSubject_withJoinedArtistNames() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        RecordCreatedEvent event = new RecordCreatedEvent(
                "rec-1", "Mr Morale",
                List.of("Kendrick", "Cole"), "https://cdn/cover.jpg",
                List.of("alice@example.com")
        );

        workerService.notifyFollowersOfNewRelease(event);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(mimeMessage).setSubject(subjectCaptor.capture(), org.mockito.ArgumentMatchers.eq("UTF-8"));
        assertThat(subjectCaptor.getValue()).isEqualTo("New Release from Kendrick, Cole");
    }
}
