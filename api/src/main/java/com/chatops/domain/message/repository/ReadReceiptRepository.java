package com.chatops.domain.message.repository;

import com.chatops.domain.message.entity.ReadReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReadReceiptRepository extends JpaRepository<ReadReceipt, String> {

    List<ReadReceipt> findByUserIdAndMessageIdIn(String userId, List<String> messageIds);

    long countByMessageId(String messageId);

    @Modifying
    @Query("DELETE FROM ReadReceipt r WHERE r.messageId = :messageId")
    void deleteByMessageId(@Param("messageId") String messageId);
}
