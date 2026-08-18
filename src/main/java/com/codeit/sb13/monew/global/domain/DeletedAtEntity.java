package com.codeit.sb13.monew.global.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@MappedSuperclass
@Getter
@ToString(callSuper = true)
public abstract class DeletedAtEntity extends UpdatedAtEntity {

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public void softDelete() {
        if (this.deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
    }

}
