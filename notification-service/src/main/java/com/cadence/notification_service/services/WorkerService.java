package com.cadence.notification_service.services;

import com.cadence.notification_service.events.EmailVerificationEvent;
import com.cadence.notification_service.events.RecordCreatedEvent;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkerService {
    private final JavaMailSender mailSender;
    @Value("${frontend.url}")
    private String frontendUrl;


    public void notifyFollowersOfNewRelease(RecordCreatedEvent event) {
        String artistNames = event.getArtists()
                .stream()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        event.getFollowerEmails()
                .forEach(email ->
                        sendReleaseMail(email, event.getRecordTitle(), artistNames, event.getCoverUrl(), event.getRecordId())
                );
    }

    @Async
    public void sendEmailVerificationMail(EmailVerificationEvent event) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setTo(event.getEmail());
            helper.setSubject("Verify your email");
            helper.setText("Click the link to verify your email:\n" + event.getVerificationLink(), false);

            mailSender.send(message);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @Async
    public void sendReleaseMail(String email,
                                String recordTitle,
                                String artistNames, String coverUrl, String recordId) {

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("New Release from " + artistNames);

            String htmlContent = buildReleaseTemplate(recordTitle, artistNames, coverUrl, recordId);

            helper.setText(htmlContent, true); // true = HTML

            mailSender.send(message);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private String buildReleaseTemplate(String recordTitle,
                                        String artistNames,
                                        String coverUrl,
                                        String recordId) {

        return """
                <html>
                <body style="margin:0; padding:0; background:#f2f2f2; font-family:Arial, sans-serif;">
                    <div style="max-width:600px; margin:auto; background:white; padding:20px;">
                
                        <h2 style="color:#1db954; margin-bottom:10px;">
                            🎵 New Release from %s
                        </h2>
                
                        <img src="%s"
                             alt="Record Cover"
                             style="width:100%%; border-radius:10px; margin-bottom:15px;" />
                
                        <h3 style="margin:0;">"%s"</h3>
                
                        <p style="color:#555;">
                            A new record just dropped. Be the first to listen.
                        </p>
                
                        <a href="%s"
                           style="display:inline-block;
                                  margin-top:15px;
                                  padding:12px 24px;
                                  background:#1db954;
                                  color:white;
                                  text-decoration:none;
                                  border-radius:25px;
                                  font-weight:bold;">
                            ▶ Listen Now
                        </a>
                
                        <hr style="margin:30px 0;" />
                
                        <p style="font-size:12px; color:#888;">
                            You’re receiving this because you follow %s on Cadence.
                        </p>
                
                    </div>
                </body>
                </html>
                """.formatted(artistNames, coverUrl, recordTitle, frontendUrl + "records/" + recordId, artistNames);
    }
}
