package com.cadence.streaming_service.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudFrontSignerTest {

    private static final String DOMAIN = "d111.cloudfront.net";
    private static final String KEY_PAIR_ID = "K2TESTKEYPAIR";
    private static final String OBJECT_KEY = "song/song_url/song-1";

    private static KeyPair keyPair;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @Test
    void signedUrl_pointsAtDistribution_withCannedPolicyParams(@TempDir Path dir) throws Exception {
        Instant expiresAt = Instant.parse("2026-01-01T01:00:00Z");
        Path keyFile = Files.writeString(dir.resolve("private_key.pem"), pkcs8Pem(true));
        CloudFrontSigner signer = new CloudFrontSigner(DOMAIN, KEY_PAIR_ID, keyFile.toString());

        URI url = URI.create(signer.signedUrl(OBJECT_KEY, expiresAt));
        Map<String, String> params = queryParams(url);

        assertThat(url.getScheme()).isEqualTo("https");
        assertThat(url.getHost()).isEqualTo(DOMAIN);
        assertThat(url.getPath()).isEqualTo("/" + OBJECT_KEY);
        assertThat(params).containsEntry("Expires", String.valueOf(expiresAt.getEpochSecond()))
                .containsEntry("Key-Pair-Id", KEY_PAIR_ID);

        // CloudFront verifies SHA1withRSA over the canned policy, with its URL-safe base64 alphabet
        String policy = "{\"Statement\":[{\"Resource\":\"https://" + DOMAIN + "/" + OBJECT_KEY
                + "\",\"Condition\":{\"DateLessThan\":{\"AWS:EpochTime\":" + expiresAt.getEpochSecond() + "}}}]}";
        byte[] signature = Base64.getDecoder().decode(
                params.get("Signature").replace('-', '+').replace('_', '=').replace('~', '/'));
        Signature verifier = Signature.getInstance("SHA1withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(policy.getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(signature)).isTrue();
    }

    @Test
    void constructor_failsFast_whenKeyFileMissing(@TempDir Path dir) {
        String missing = dir.resolve("missing.pem").toString();

        assertThatThrownBy(() -> new CloudFrontSigner(DOMAIN, KEY_PAIR_ID, missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cloud.aws.cloudfront.private-key-path")
                .hasMessageContaining(missing);
    }

    @Test
    void parsePrivateKey_readsPkcs8_withOrWithoutLineBreaks() {
        assertThat(CloudFrontSigner.parsePrivateKey(pkcs8Pem(true)).getEncoded()).isEqualTo(keyPair.getPrivate().getEncoded());
        assertThat(CloudFrontSigner.parsePrivateKey(pkcs8Pem(false)).getEncoded()).isEqualTo(keyPair.getPrivate().getEncoded());
    }

    @Test
    void parsePrivateKey_readsPkcs1() {
        // A 2048-bit RSA PKCS#8 encoding is a fixed 26-byte header wrapping the PKCS#1 key
        byte[] pkcs8 = keyPair.getPrivate().getEncoded();
        byte[] pkcs1 = Arrays.copyOfRange(pkcs8, 26, pkcs8.length);
        String pem = "-----BEGIN RSA PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(pkcs1)
                + "\n-----END RSA PRIVATE KEY-----\n";

        assertThat(CloudFrontSigner.parsePrivateKey(pem).getEncoded()).isEqualTo(pkcs8);
    }

    @Test
    void parsePrivateKey_failsFast_onGarbage() {
        assertThatThrownBy(() -> CloudFrontSigner.parsePrivateKey("not a key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a valid RSA private key");
    }

    private static String pkcs8Pem(boolean lineBreaks) {
        byte[] der = keyPair.getPrivate().getEncoded();
        String body = lineBreaks ? Base64.getMimeEncoder().encodeToString(der) : Base64.getEncoder().encodeToString(der);
        String nl = lineBreaks ? "\n" : "";
        return "-----BEGIN PRIVATE KEY-----" + nl + body + nl + "-----END PRIVATE KEY-----" + nl;
    }

    private static Map<String, String> queryParams(URI url) {
        Map<String, String> params = new HashMap<>();
        for (String pair : url.getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(kv[0], kv[1]);
        }
        return params;
    }
}
