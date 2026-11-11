package com.example.bishe.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

final class CheckInCode {
    static final long PERIOD_MS = 6000;
    private static final long GRACE_MS = 2000;
    private static final SecureRandom RANDOM = new SecureRandom();

    static String newSeed() {
        byte[] seed = new byte[32];
        RANDOM.nextBytes(seed);
        return Base64.getEncoder().encodeToString(seed);
    }

    static long elapsedMillis(LocalDateTime published, LocalDateTime now) {
        return Math.max(0, Duration.between(published, now).toMillis());
    }

    static boolean accepts(String seed, long elapsed, String submitted) {
        if (submitted == null || !submitted.matches("[0-9]{4}")) return false;
        long round = elapsed / PERIOD_MS;
        return submitted.equals(value(seed, round))
                || (round > 0 && elapsed % PERIOD_MS < GRACE_MS && submitted.equals(value(seed, round - 1)));
    }

    static String value(String seed, long round) {
        // Permute the 10,000 possible codes so consecutive rounds always differ.
        int left = (int) (round / 100 % 100);
        int right = (int) (round % 100);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(seed), "HmacSHA256"));
            for (int step = 0; step < 6; step++) {
                byte[] hash = mac.doFinal(new byte[]{(byte) step, (byte) right});
                int shift = Integer.remainderUnsigned(ByteBuffer.wrap(hash).getInt(), 100);
                int next = (left + shift) % 100;
                left = right;
                right = next;
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("无法生成签到码", e);
        }
        return String.format(Locale.ROOT, "%04d", left * 100 + right);
    }

    private CheckInCode() {}
}
