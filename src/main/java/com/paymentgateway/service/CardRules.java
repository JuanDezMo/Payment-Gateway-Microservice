package com.paymentgateway.service;

import com.paymentgateway.dto.CardData;
import com.paymentgateway.dto.PaymentRequest;
import com.paymentgateway.exception.ErrorCode;
import com.paymentgateway.exception.InvalidPaymentRequestException;
import com.paymentgateway.model.CardBrand;
import com.paymentgateway.model.SimulationResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Reglas de tarjeta en memoria, sin acceso a BD. No aplica Luhn ni prefijo por franquicia. */
@Component
public class CardRules {

    private static final String REJECTED_SUFFIX = "0000";
    private static final String ERROR_SUFFIX = "9999";
    private static final Pattern NUMBER_FORMAT = Pattern.compile("\\d{13,19}");
    private static final Pattern EXPIRY_FORMAT = Pattern.compile("(0[1-9]|1[0-2])/(\\d{2})");
    private static final Pattern CVV_FORMAT = Pattern.compile("\\d{3}");
    private static final int CENTURY = 2000;
    private static final int LAST_DIGITS = 4;

    private final Clock clock;

    public CardRules(Clock clock) {
        this.clock = clock;
    }

    public void validate(PaymentRequest request) {
        CardData card = request.card();
        if (CardBrand.from(card.brand()).isEmpty()) {
            throw new InvalidPaymentRequestException(ErrorCode.BRAND_NOT_ALLOWED);
        }
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0
                || !NUMBER_FORMAT.matcher(card.number()).matches()
                || !CVV_FORMAT.matcher(card.cvv()).matches()
                || isExpired(card.expiry())) {
            throw new InvalidPaymentRequestException(ErrorCode.INVALID_REQUEST);
        }
    }

    public SimulationResult simulate(String number) {
        if (number.endsWith(REJECTED_SUFFIX)) {
            return SimulationResult.REJECTED;
        }
        if (number.endsWith(ERROR_SUFFIX)) {
            return SimulationResult.ERROR;
        }
        return SimulationResult.APPROVED;
    }

    public String lastDigits(String number) {
        return number.substring(number.length() - LAST_DIGITS);
    }

    // Una tarjeta es valida hasta el ultimo dia del mes de vencimiento
    private boolean isExpired(String expiry) {
        var matcher = EXPIRY_FORMAT.matcher(expiry);
        if (!matcher.matches()) {
            return true;
        }
        YearMonth expiryMonth = YearMonth.of(CENTURY + Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(1)));
        return expiryMonth.isBefore(YearMonth.now(clock));
    }
}
