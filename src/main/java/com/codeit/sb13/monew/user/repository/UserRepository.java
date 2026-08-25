package com.codeit.sb13.monew.user.repository;

import com.codeit.sb13.monew.user.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  boolean existsByEmail(String email);

  Optional<User> findByEmail(String email);

  @Modifying(clearAutomatically = true)
  @Query("UPDATE User u SET u.deletedAt = :now WHERE u.id = :userId AND u.deletedAt IS NULL")
  int softDeleteIfNotDeleted(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

  List<User> findByDeletedAtBefore(LocalDateTime threshold);

}
