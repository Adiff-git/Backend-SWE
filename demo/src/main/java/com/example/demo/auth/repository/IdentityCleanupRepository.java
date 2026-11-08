package com.example.demo.auth.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository @RequiredArgsConstructor
public class IdentityCleanupRepository {
    private final JdbcTemplate jdbc;
    public int eraseExpiredMail(Instant now){
        return jdbc.update("WITH batch AS (SELECT id FROM verification_mail_outbox WHERE encrypted_payload IS NOT NULL AND expires_at<=? ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED) UPDATE verification_mail_outbox o SET encrypted_payload=NULL,nonce=NULL,state='EXPIRED',lease_until=NULL FROM batch b WHERE o.id=b.id",Timestamp.from(now));
    }
    public boolean hasExpiredMail(Instant now){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM verification_mail_outbox WHERE encrypted_payload IS NOT NULL AND expires_at<=?)",Boolean.class,Timestamp.from(now)));}
    public void deleteExpiredHandoffs(Instant cutoff){jdbc.update("DELETE FROM oauth_handoffs WHERE id IN(SELECT id FROM oauth_handoffs WHERE expires_at<=? ORDER BY id LIMIT 100)",Timestamp.from(cutoff));}
    public List<UUID> expiredAttempts(Instant cutoff){return jdbc.query("SELECT id FROM oauth_login_attempts WHERE expires_at<=? ORDER BY id LIMIT 100",(row,index)->row.getObject(1,UUID.class),Timestamp.from(cutoff));}
    public void deleteAttempt(UUID id){
        jdbc.queryForList("SELECT id FROM oauth_login_attempts WHERE id=? FOR UPDATE",UUID.class,id);
        jdbc.update("DELETE FROM oauth_handoffs WHERE attempt_id=?",id);
        jdbc.update("UPDATE oauth_browser_slots SET current_attempt_id=NULL WHERE current_attempt_id=?",id);
        jdbc.update("DELETE FROM oauth_login_attempts WHERE id=?",id);
    }
    public void deleteExpiredProofs(Instant cutoff){
        List<UUID> dependents=jdbc.query("SELECT a.id FROM oauth_login_attempts a JOIN recent_auth_proofs p ON p.id=a.recent_auth_proof_id WHERE p.expires_at<=? ORDER BY a.id LIMIT 100",(row,index)->row.getObject(1,UUID.class),Timestamp.from(cutoff));
        for(UUID attempt:dependents)deleteAttempt(attempt);
        jdbc.update("DELETE FROM recent_auth_proofs WHERE id IN (SELECT p.id FROM recent_auth_proofs p WHERE p.expires_at<=? AND NOT EXISTS(SELECT 1 FROM oauth_login_attempts a WHERE a.recent_auth_proof_id=p.id) AND NOT EXISTS(SELECT 1 FROM oauth_handoffs h WHERE h.recent_auth_proof_id=p.id) ORDER BY p.id LIMIT 100)",Timestamp.from(cutoff));
    }
    public void deleteEmptySlots(Instant cutoff){jdbc.update("DELETE FROM oauth_browser_slots WHERE id IN(SELECT s.id FROM oauth_browser_slots s WHERE s.expires_at<=? AND s.current_attempt_id IS NULL AND NOT EXISTS(SELECT 1 FROM oauth_login_attempts a WHERE a.browser_slot_id=s.id) AND NOT EXISTS(SELECT 1 FROM oauth_handoffs h WHERE h.browser_slot_id=s.id) ORDER BY s.id LIMIT 100)",Timestamp.from(cutoff));}
    public List<UUID> expiredPending(Instant cutoff){return jdbc.query("SELECT id FROM pending_registrations WHERE expires_at<=? ORDER BY id LIMIT 100",(row,index)->row.getObject(1,UUID.class),Timestamp.from(cutoff));}
    public List<Long> expiredChallengeOwners(Instant cutoff){return jdbc.queryForList("SELECT DISTINCT user_id FROM email_verification_challenges WHERE user_id IS NOT NULL AND expires_at<=? ORDER BY user_id LIMIT 100",Long.class,Timestamp.from(cutoff));}
    public void deletePending(UUID pending,Instant now){
        jdbc.queryForList("SELECT id FROM pending_registrations WHERE id=? FOR UPDATE",UUID.class,pending);
        jdbc.update("UPDATE pending_registrations SET credential_hash=NULL,state=CASE WHEN state='ACTIVE' THEN 'EXPIRED' ELSE state END,expired_at=CASE WHEN state='ACTIVE' THEN clock_timestamp() ELSE expired_at END WHERE id=?",pending);
        deleteChallenges("pending_registration_id",pending,null,now);
        jdbc.update("DELETE FROM pending_registrations WHERE id=? AND NOT EXISTS(SELECT 1 FROM email_verification_challenges WHERE pending_registration_id=?)",pending,pending);
    }
    public void deleteUserChallenges(Long owner,Instant cutoff,Instant now){deleteChallenges("user_id",owner,cutoff,now);}
    private void deleteChallenges(String ownerColumn,Object owner,Instant cutoff,Instant now){
        String clause=ownerColumn+"=?"+(cutoff==null?"":" AND expires_at<=?");
        Object[] values=cutoff==null?new Object[]{owner}:new Object[]{owner,Timestamp.from(cutoff)};
        List<UUID> ids=jdbc.query("SELECT id FROM email_verification_challenges WHERE "+clause+" ORDER BY id LIMIT 100 FOR UPDATE",(row,index)->row.getObject(1,UUID.class),values);
        for(UUID id:ids){
            jdbc.update("UPDATE email_verification_challenges SET active=FALSE,code_mac=NULL WHERE id=?",id);
            jdbc.update("DELETE FROM verification_mail_outbox WHERE challenge_id=?",id);
            jdbc.update("DELETE FROM email_verification_challenges c WHERE c.id=? AND NOT EXISTS(SELECT 1 FROM verification_mail_outbox o WHERE o.challenge_id=c.id)",id);
        }
    }
    public List<UUID> sessionAttempts(UUID session){return jdbc.query("SELECT id FROM oauth_login_attempts WHERE initiating_session_id=? ORDER BY id",(row,index)->row.getObject(1,UUID.class),session);}
    public void deleteSessionProofs(UUID session){jdbc.update("DELETE FROM recent_auth_proofs WHERE initiating_session_id=?",session);}
    public List<Long> backlog(Instant now){return jdbc.queryForList("SELECT COUNT(*) FROM verification_mail_outbox WHERE encrypted_payload IS NOT NULL AND expires_at<=?",Long.class,Timestamp.from(now));}
}
