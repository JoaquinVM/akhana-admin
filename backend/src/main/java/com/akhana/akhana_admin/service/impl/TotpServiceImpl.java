package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.service.TotpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import java.util.OptionalLong;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

@Service
public class TotpServiceImpl implements TotpService {

    static final String ISSUER = "Akhana Admin";
    private static final int SECRET_BYTES = 20;
    private static final long STEP_SECONDS = 30;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public TotpServiceImpl() {
        this(Clock.systemUTC());
    }

    public TotpServiceImpl(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32(bytes);
    }

    @Override
    public OptionalLong matchingStep(String secret, String code, Long lastUsedStep) {
        if (secret == null || code == null) {
            return OptionalLong.empty();
        }
        String digits = code.replace(" ", "");
        if (!digits.matches("\\d{6}")) {
            return OptionalLong.empty();
        }
        byte[] key = base32Decode(secret);
        long current = Math.floorDiv(clock.instant().getEpochSecond(), STEP_SECONDS);
        for (long step = current - 1; step <= current + 1; step++) {
            if (lastUsedStep != null && step <= lastUsedStep) {
                continue;
            }
            if (MessageDigest.isEqual(code(key, step).getBytes(US_ASCII), digits.getBytes(US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    @Override
    public String otpauthUri(String secret, String username) {
        return "otpauth://totp/" + encode(ISSUER) + ":" + encode(username)
            + "?secret=" + secret + "&issuer=" + encode(ISSUER) + "&algorithm=SHA1&digits=6&period=30";
    }

    /** The 6-digit code of a time step (RFC 6238 over the HOTP truncation of RFC 4226). */
    public static String code(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 is not available", e);
        }
    }

    /** RFC 4648 Base32 without padding, the alphabet of the otpauth secret. */
    public static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
                buffer &= (1 << bits) - 1;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    public static byte[] base32Decode(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : text.toUpperCase(Locale.ROOT).toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("The second factor secret is not Base32");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
                buffer &= (1 << bits) - 1;
            }
        }
        return out.toByteArray();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, UTF_8).replace("+", "%20");
    }
}
