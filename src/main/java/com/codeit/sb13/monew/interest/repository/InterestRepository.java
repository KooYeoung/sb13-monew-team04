package com.codeit.sb13.monew.interest.repository;

import com.codeit.sb13.monew.interest.domain.Interest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InterestRepository extends JpaRepository<Interest, UUID> {
}
