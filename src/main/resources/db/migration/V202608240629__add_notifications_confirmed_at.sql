-- Notification: 확인 처리 시각(confirmedAt) 컬럼 추가
-- updatedAt 대신 알림 삭제 배치의 기준 시각으로 사용
alter table notifications
    add column confirmed_at timestamp;