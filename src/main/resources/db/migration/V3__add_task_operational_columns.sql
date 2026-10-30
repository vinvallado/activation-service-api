ALTER TABLE activation_service.activation_tasks RENAME COLUMN available_at TO next_attempt_at;
ALTER TABLE activation_service.activation_tasks RENAME COLUMN locked_until TO lease_until;
ALTER TABLE activation_service.activation_tasks ADD COLUMN correlation_id VARCHAR(100);
CREATE INDEX idx_activation_requests_owner_created ON activation_service.activation_requests (owner_client_id, created_at, id);
CREATE INDEX idx_activation_requests_owner_status_created ON activation_service.activation_requests (owner_client_id, status, created_at, id);
CREATE INDEX idx_activation_tasks_eligible ON activation_service.activation_tasks (next_attempt_at, id) WHERE status = 'PENDING';
CREATE INDEX idx_activation_tasks_expired_lease ON activation_service.activation_tasks (lease_until, id) WHERE status = 'PROCESSING';
