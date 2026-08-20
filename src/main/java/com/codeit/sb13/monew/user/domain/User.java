package com.codeit.sb13.monew.user.domain;

import com.codeit.sb13.monew.global.domain.DeletedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends DeletedAtEntity {
  @Column(name = "email", nullable = false, length = 255)
  private String email;

  @Column(name = "nickname", nullable = false, length = 60)
  private String nickname;

  @Column(name = "password", nullable = false, length = 225)
  private String password;

  public void updateNickname(String nickname) {
    this.nickname = nickname;
  }
}