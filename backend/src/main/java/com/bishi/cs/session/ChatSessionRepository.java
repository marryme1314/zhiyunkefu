package com.bishi.cs.session;

import com.bishi.cs.user.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findByUserOrderByUpdatedAtDesc(UserAccount user);

    Optional<ChatSession> findByIdAndUser(Long id, UserAccount user);

    @Query("""
            select s from ChatSession s
            join fetch s.user
            order by s.updatedAt desc
            """)
    List<ChatSession> findAllWithUserOrderByUpdatedAtDesc();

    long countByUser(UserAccount user);
}
