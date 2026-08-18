package com.codeit.sb13.monew.user.domain;


import com.codeit.sb13.monew.global.domain.DeletedAtEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends DeletedAtEntity {
    private String email;
    private String nickname;
    private String password;
}