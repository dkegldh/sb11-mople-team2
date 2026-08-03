package com.codeit.mople.domain.directmessage.repository;

import com.codeit.mople.domain.directmessage.entity.DirectMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID>, DirectMessageRepositoryCustom {

}
