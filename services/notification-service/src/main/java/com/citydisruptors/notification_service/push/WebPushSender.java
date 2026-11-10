package com.citydisruptors.notification_service.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

@Component
public class WebPushSender {

    public enum Urgency { VERY_LOW, LOW, NORMAL, HIGH }

    private static final int RECORD_SIZE = 4096;
    private static final Duration TIME_TO_LIVE = Duration.ofHours(24);
    private static final Duration TOKEN_VALIDITY = Duration.ofHours(12);
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final PushProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final SecureRandom random = new SecureRandom();
    private final ECParameterSpec curve;

    public WebPushSender(PushProperties properties, ObjectMapper objectMapper) throws GeneralSecurityException {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        this.curve = parameters.getParameterSpec(ECParameterSpec.class);
    }

    public int send(PushSubscription subscription, byte[] payload, Urgency urgency)
            throws IOException, InterruptedException, GeneralSecurityException {
        URI endpoint = URI.create(subscription.getEndpoint());
        byte[] body = encrypt(payload, decode(subscription.getP256dh()), decode(subscription.getAuth()));

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", vapidAuthorization(endpoint))
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("TTL", Long.toString(TIME_TO_LIVE.toSeconds()))
                .header("Urgency", urgency.name().toLowerCase().replace('_', '-'))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    public void validateKeys(String p256dh, String auth) {
        try {
            publicKey(decode(p256dh));
            if (decode(auth).length != 16) {
                throw new IllegalArgumentException("Push subscription auth secret must be 16 bytes");
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid push subscription keys", e);
        }
    }

    private byte[] encrypt(byte[] plaintext, byte[] userAgentPublic, byte[] authSecret) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(curve, random);
        KeyPair serverKeys = generator.generateKeyPair();
        byte[] serverPublic = encode((ECPublicKey) serverKeys.getPublic());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(serverKeys.getPrivate());
        agreement.doPhase(publicKey(userAgentPublic), true);
        byte[] sharedSecret = agreement.generateSecret();

        byte[] salt = new byte[16];
        random.nextBytes(salt);

        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), userAgentPublic, serverPublic);
        byte[] inputKey = expand(hmac(authSecret, sharedSecret), keyInfo, 32);
        byte[] pseudoRandomKey = hmac(salt, inputKey);
        byte[] contentKey = expand(pseudoRandomKey, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = expand(pseudoRandomKey, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentKey, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[]{2}));

        return ByteBuffer.allocate(16 + 4 + 1 + serverPublic.length + ciphertext.length)
                .put(salt)
                .putInt(RECORD_SIZE)
                .put((byte) serverPublic.length)
                .put(serverPublic)
                .put(ciphertext)
                .array();
    }

    private String vapidAuthorization(URI endpoint) throws GeneralSecurityException, JsonProcessingException {
        String audience = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        String header = BASE64_URL.encodeToString(objectMapper.writeValueAsBytes(Map.of("typ", "JWT", "alg", "ES256")));
        String claims = BASE64_URL.encodeToString(objectMapper.writeValueAsBytes(Map.of(
                "aud", audience,
                "exp", Instant.now().plus(TOKEN_VALIDITY).getEpochSecond(),
                "sub", properties.subject())));
        String unsigned = header + "." + claims;

        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(privateKey(decode(properties.privateKey())));
        signer.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        String token = unsigned + "." + BASE64_URL.encodeToString(signer.sign());

        return "vapid t=" + token + ", k=" + properties.publicKey();
    }

    private PublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) {
            throw new IllegalArgumentException("Expected an uncompressed P-256 public key");
        }
        ECPoint point = new ECPoint(
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, curve));
    }

    private PrivateKey privateKey(byte[] scalar) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), curve));
    }

    private static byte[] encode(ECPublicKey key) {
        return concat(new byte[]{4}, unsigned32(key.getW().getAffineX()), unsigned32(key.getW().getAffineY()));
    }

    private static byte[] unsigned32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] result = new byte[32];
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, result, 32 - length, length);
        return result;
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] expand(byte[] pseudoRandomKey, byte[] info, int length) throws GeneralSecurityException {
        return Arrays.copyOf(hmac(pseudoRandomKey, concat(info, new byte[]{1})), length);
    }

    private static byte[] decode(String base64Url) {
        return BASE64_URL_DECODER.decode(base64Url.replace('+', '-').replace('/', '_').replace("=", ""));
    }

    private static byte[] concat(byte[]... parts) {
        int length = Arrays.stream(parts).mapToInt(part -> part.length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (byte[] part : parts) {
            buffer.put(part);
        }
        return buffer.array();
    }
}
