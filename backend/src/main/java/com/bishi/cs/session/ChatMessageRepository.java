package com.bishi.cs.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionOrderByCreatedAtAsc(ChatSession session);

    void deleteBySession(ChatSession session);

    @Query("select count(m) from ChatMessage m where m.session.user.id = :userId and m.role = 'USER' and m.createdAt >= :start")
    long countUserQuestionsSince(Long userId, LocalDateTime start);

    @Query("select count(m) from ChatMessage m where m.role = 'USER'")
    long countAllUserQuestions();

    @Query("select count(m) from ChatMessage m where m.role = 'USER' and m.createdAt >= :start")
    long countUserQuestionsSinceGlobal(LocalDateTime start);

    @Query(value = """
            select date(created_at) as day, count(*) as cnt
            from messages
            where role = 'USER' and created_at >= :start
            group by date(created_at)
            order by day
            """, nativeQuery = true)
    List<Object[]> dailyUserQuestionCounts(LocalDateTime start);

    @Query("""
            select coalesce(m.intent, 'UNKNOWN'), count(m)
            from ChatMessage m
            where m.role = 'USER'
            group by coalesce(m.intent, 'UNKNOWN')
            """)
    List<Object[]> countByIntent();

    @Query("select count(m) from ChatMessage m where m.session.user.id = :userId and m.role = 'USER'")
    long countUserQuestionsByUserId(Long userId);
}
