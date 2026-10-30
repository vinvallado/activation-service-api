package com.deroyque.activationserviceapi.repository;

import com.deroyque.activationserviceapi.model.ActivationStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ActivationTaskClaimRepositoryImpl implements ActivationTaskClaimRepository {
    private final JdbcTemplate jdbc;
    public ActivationTaskClaimRepositoryImpl(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override @Transactional
    public Optional<ClaimedTask> claimNext(Instant now, Instant leaseUntil) {
        jdbc.update("""
            UPDATE activation_service.activation_tasks
            SET status='PENDING', claim_token=NULL, lease_until=NULL, next_attempt_at=?, updated_at=?,
                last_error=COALESCE(last_error, 'Lease expirada; tarefa recuperada')
            WHERE status='PROCESSING' AND lease_until < ?
            """, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
            UPDATE activation_service.activation_requests r SET status='PENDING', updated_at=?
            FROM activation_service.activation_tasks t
            WHERE t.request_id=r.id AND t.status='PENDING' AND t.updated_at=?
            """, Timestamp.from(now), Timestamp.from(now));
        var rows=jdbc.query("""
            SELECT id, request_id, attempts, correlation_id FROM activation_service.activation_tasks
            WHERE status = 'PENDING' AND next_attempt_at <= ?
            ORDER BY next_attempt_at, id FOR UPDATE SKIP LOCKED LIMIT 1
            """, (rs,n) -> new Object[]{UUID.fromString(rs.getString("id")),UUID.fromString(rs.getString("request_id")),rs.getInt("attempts"),rs.getString("correlation_id")}, Timestamp.from(now));
        if(rows.isEmpty()) return Optional.empty();
        Object[] row=rows.getFirst(); UUID taskId=(UUID)row[0]; UUID token=UUID.randomUUID(); int attempts=(int)row[2]+1;
        int updated=jdbc.update("""
            UPDATE activation_service.activation_tasks SET status='PROCESSING', attempts=?, claim_token=?, lease_until=?, updated_at=?
            WHERE id=? AND status='PENDING'
            """,attempts,token,Timestamp.from(leaseUntil),Timestamp.from(now),taskId);
        return updated == 1 ? Optional.of(new ClaimedTask(taskId,(UUID)row[1],token,attempts,(String)row[3])) : Optional.empty();
    }
    @Override @Transactional
    public boolean complete(UUID taskId, UUID token, ActivationStatus requestStatus, Instant now, String error) {
        int changed=jdbc.update("""
            UPDATE activation_service.activation_tasks SET status=?, claim_token=NULL, lease_until=NULL, last_error=?, updated_at=?
            WHERE id=? AND claim_token=? AND status='PROCESSING'
            """,requestStatus.name(),error,Timestamp.from(now),taskId,token);
        if(changed != 1) return false;
        jdbc.update("""
            UPDATE activation_service.activation_requests r SET status=?, updated_at=?
            FROM activation_service.activation_tasks t WHERE t.id=? AND t.request_id=r.id
            """,requestStatus.name(),Timestamp.from(now),taskId);
        return true;
    }
    @Override @Transactional
    public boolean reschedule(UUID taskId, UUID token, Instant nextAttemptAt, Instant now, String error) {
        int changed=jdbc.update("""
            UPDATE activation_service.activation_tasks SET status='PENDING', claim_token=NULL, lease_until=NULL, next_attempt_at=?, last_error=?, updated_at=?
            WHERE id=? AND claim_token=? AND status='PROCESSING'
            """,Timestamp.from(nextAttemptAt),error,Timestamp.from(now),taskId,token);
        if(changed != 1) return false;
        jdbc.update("""
            UPDATE activation_service.activation_requests r SET status='PENDING', updated_at=?
            FROM activation_service.activation_tasks t WHERE t.id=? AND t.request_id=r.id
            """,Timestamp.from(now),taskId);
        return true;
    }
}
