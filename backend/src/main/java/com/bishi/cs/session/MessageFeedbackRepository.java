package com.bishi.cs.session;

import com.bishi.cs.user.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MessageFeedbackRepository extends JpaRepository<MessageFeedback, Long> {
    Optional<MessageFeedback> findByMessageAndUser(ChatMessage message, UserAccount user);

    void deleteByMessage(ChatMessage message);

    @Query("""
            select f from MessageFeedback f
            join fetch f.message m
            join fetch m.session s
            join fetch f.user u
            order by f.createdAt desc
            """)
    List<MessageFeedback> findAllDetailed();

    @Query("select f.type, count(f) from MessageFeedback f group by f.type")
    List<Object[]> countByType();

    long countByMessage(ChatMessage message);

    long countByUser(UserAccount user);

    void deleteByUser(UserAccount user);
}
