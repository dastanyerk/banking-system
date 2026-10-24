package com.dastan.transaction_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@EntityListeners(AuditingEntityListener.class)
@Table(name = "transactions")

public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, name = "sender_acc_number")
    private String senderAccountNumber;

    @Column(nullable = false, name = "receiver_acc_number")
    private String receiverAccountNumber;

    @Column(nullable = false, precision = 15,scale = 2, name = "amount")
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "transaction_type", columnDefinition = "varchar(30)")
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "transaction_status", columnDefinition = "varchar(30)")
    private TransactionStatus transactionStatus;

    private String description;

    private String failureReason;

    private String referenceNumber;

    @CreatedDate
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
