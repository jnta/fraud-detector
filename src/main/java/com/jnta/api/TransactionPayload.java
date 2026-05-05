package com.jnta.api;

import io.micronaut.serde.annotation.Serdeable;
import java.time.OffsetDateTime;
import java.util.List;

@Serdeable
public record TransactionPayload(
    String id,
    TransactionData transaction,
    CustomerData customer,
    MerchantData merchant,
    TerminalData terminal,
    LastTransactionData last_transaction
) {
    @Serdeable
    public record TransactionData(
        float amount,
        int installments,
        OffsetDateTime requested_at
    ) {}

    @Serdeable
    public record CustomerData(
        float avg_amount,
        int tx_count_24h,
        List<String> known_merchants
    ) {}

    @Serdeable
    public record MerchantData(
        String id,
        String mcc,
        float avg_amount
    ) {}

    @Serdeable
    public record TerminalData(
        boolean is_online,
        boolean card_present,
        float km_from_home
    ) {}

    @Serdeable
    public record LastTransactionData(
        OffsetDateTime timestamp,
        float km_from_current
    ) {}
}
