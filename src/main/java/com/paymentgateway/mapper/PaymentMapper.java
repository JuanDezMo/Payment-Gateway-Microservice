package com.paymentgateway.mapper;

import com.paymentgateway.dto.PaymentResponse;
import com.paymentgateway.exception.ErrorCode;
import com.paymentgateway.model.PaymentAttempt;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/** Solo expone estado, transactionId, codigo y mensaje; nunca numero completo, CVV ni vencimiento. */
@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "code", source = "errorCode")
    @Mapping(target = "message", source = "errorCode", qualifiedByName = "messageOf")
    PaymentResponse toResponse(PaymentAttempt attempt);

    @Named("messageOf")
    default String messageOf(String errorCode) {
        return errorCode == null ? null : ErrorCode.valueOf(errorCode).message();
    }
}
