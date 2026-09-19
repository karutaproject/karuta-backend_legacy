package com.eportfolium.karuta.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eportfolium.karuta.entity.SMSEntity;

public interface SMSRepository extends JpaRepository<SMSEntity, Long> {

	List<SMSEntity> findByUserId(Long userId);

}
