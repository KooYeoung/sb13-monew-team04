package com.codeit.sb13.monew.global.service;

public interface AdvisoryLockService {
    boolean executeWithLock(String lockKey, Runnable task);
}
