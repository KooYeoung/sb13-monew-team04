package com.codeit.sb13.monew.article.s3.service;

import com.codeit.sb13.monew.article.s3.service.dto.StorageCommand;
import com.codeit.sb13.monew.article.s3.service.dto.StorageSaveResult;
import com.codeit.sb13.monew.article.s3.service.dto.StorageSearchCommand;

import java.util.Optional;

public interface Storage {
    StorageSaveResult saveIfAbsent(StorageCommand command);

    Optional<String> find(StorageSearchCommand searchCommand);

    boolean exists(StorageSearchCommand searchCommand);

    String resolveBackupObjectKey(StorageSearchCommand searchCommand);
}
