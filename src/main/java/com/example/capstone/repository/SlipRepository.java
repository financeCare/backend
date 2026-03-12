package com.example.capstone.repository;

import com.example.capstone.entity.Slip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SlipRepository extends JpaRepository<Slip, Long> {
    List<Slip> findByUserId(UUID userId);
}
