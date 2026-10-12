package com.console.demo.services;

import com.console.demo.dto.Setting9Router;
import com.console.demo.entities.Setting9RouterEntity;
import com.console.demo.repositories.Setting9RouterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class Setting9RouterService {

    private static final Logger log = LoggerFactory.getLogger(Setting9RouterService.class);

    private final Setting9RouterRepository setting9RouterRepository;

    public Setting9RouterService(Setting9RouterRepository setting9RouterRepository) {
        this.setting9RouterRepository = setting9RouterRepository;
    }


    public Setting9RouterEntity add(Setting9Router dto) {
        if (setting9RouterRepository.count() > 0) {
            throw new IllegalStateException(
                    "Data sudah ada. Tidak bisa menambah lebih dari satu, silakan gunakan update.");
        }
        Setting9RouterEntity entity = new Setting9RouterEntity();
        entity.setBaseUrl(dto.getApiUrl());
        entity.setApiKey(dto.getApiKey());
        entity.setComboName(dto.getComboName());
        return setting9RouterRepository.save(entity);
    }


    public List<Setting9RouterEntity> showAll() {
        return setting9RouterRepository.findAll();
    }


    public Setting9RouterEntity showById(Long id) {
        return setting9RouterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Data dengan id " + id + " tidak ditemukan"));
    }


    public Setting9RouterEntity update(Long id, Setting9Router dto) {
        Setting9RouterEntity entity = showById(id);
        entity.setBaseUrl(dto.getApiUrl());
        entity.setApiKey(dto.getApiKey());
        entity.setComboName(dto.getComboName());
        return setting9RouterRepository.save(entity);
    }


    public void delete(Long id) {
        Setting9RouterEntity entity = showById(id);
        setting9RouterRepository.delete(entity);
        log.info("Data setting 9Router dengan id {} berhasil dihapus", id);
    }
}