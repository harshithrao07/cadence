package com.cadence.streaming_service.service;

import com.amazonaws.auth.RSA;
import com.amazonaws.services.cloudfront.CloudFrontUrlSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Signs URLs on the CloudFront distribution in front of the bucket, so audio is served (and cached) by CloudFront
 * edges instead of S3. Canned policy: the URL only expires, it isn't tied to an IP.
 */
@Component
public class CloudFrontSigner {
    private final String domain;
    private final String keyPairId;
    private final PrivateKey privateKey;

    public CloudFrontSigner(
            @Value("${cloud.aws.cloudfront.domain}") String domain,
            @Value("${cloud.aws.cloudfront.key-pair-id}") String keyPairId,
            @Value("${cloud.aws.cloudfront.private-key-path}") String privateKeyPath
    ) {
        this.domain = domain;
        this.keyPairId = keyPairId;
        this.privateKey = parsePrivateKey(readKeyFile(privateKeyPath));
    }

    public String signedUrl(String objectKey, Instant expiresAt) {
        return CloudFrontUrlSigner.getSignedURLWithCannedPolicy(
                "https://" + domain + "/" + objectKey,
                keyPairId,
                privateKey,
                Date.from(expiresAt)
        );
    }

    private static String readKeyFile(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (IOException e) {
            throw new IllegalStateException("Can't read the CloudFront private key at cloud.aws.cloudfront.private-key-path=" + path, e);
        }
    }

    /** Reads a PKCS#1 ("BEGIN RSA PRIVATE KEY") or PKCS#8 ("BEGIN PRIVATE KEY") PEM. */
    static PrivateKey parsePrivateKey(String pem) {
        String body = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(body);
            return pem.contains("BEGIN RSA PRIVATE KEY") ? RSA.privateKeyFromPKCS1(der) : RSA.privateKeyFromPKCS8(der);
        } catch (IllegalArgumentException | InvalidKeySpecException e) {
            throw new IllegalStateException("The CloudFront private key is not a valid RSA private key PEM", e);
        }
    }
}
