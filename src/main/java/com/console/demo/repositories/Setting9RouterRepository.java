package com.console.demo.repositories;

import com.console.demo.entities.Setting9RouterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Setting9RouterRepository extends JpaRepository<Setting9RouterEntity, Long> {
}
