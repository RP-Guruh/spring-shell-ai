package com.console.demo.repositories;

import com.console.demo.dto.MessageDto;
import com.console.demo.entities.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {
    List<MessageDto> findAllBy();

}
