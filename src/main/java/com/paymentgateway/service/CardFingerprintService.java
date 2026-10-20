package com.paymentgateway.service;

import com.paymentgateway.config.PaymentProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/** Huella estable de la tarjeta con HMAC-SHA256; no permite recuperar el numero. */
@Service
public class CardFingerprintService {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public CardFingerprintService(PaymentProperties properties) {
        this.key = new SecretKeySpec(properties.cardFingerprintSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String of(String cardNumber) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(cardNumber.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
