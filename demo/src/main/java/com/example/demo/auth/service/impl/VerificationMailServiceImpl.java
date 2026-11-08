package com.example.demo.auth.service.impl;

import com.example.demo.auth.config.VerificationProperties;
import com.example.demo.auth.entity.EmailVerificationChallenge;
import com.example.demo.auth.entity.VerificationMailOutbox;
import com.example.demo.auth.repository.EmailVerificationChallengeRepository;
import com.example.demo.auth.repository.VerificationMailOutboxRepository;
import com.example.demo.auth.service.IdentityProofService;
import com.example.demo.auth.service.VerificationMailService;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service @RequiredArgsConstructor
@ConditionalOnProperty(name="security.verification.enabled",havingValue="true")
public class VerificationMailServiceImpl implements VerificationMailService {
    private final VerificationMailOutboxRepository outbox;
    private final EmailVerificationChallengeRepository challenges;
    private final IdentityProofService proofs;
    private final VerificationProperties properties;
    private final ObjectProvider<JavaMailSender> senders;
    private final PlatformTransactionManager transactions;
    private final Clock clock;
    private final JsonMapper json;
    private final com.example.demo.auth.service.IdentityAuditService audit;
    private final com.example.demo.auth.service.IdentityCleanupService cleanup;
    private static final long[] RETRY_SECONDS={5,30,120};
    public Long enqueue(EmailVerificationChallenge challenge,String code){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Outbox requires business transaction");
        byte[] encrypted=proofs.encrypt(json.writeValueAsBytes(Map.of("recipient",challenge.getDestination(),"code",code,"challengeId",challenge.getId().toString())),challenge.getId().toString());
        VerificationMailOutbox row=new VerificationMailOutbox();row.setChallengeId(challenge.getId());
        row.setNonce(Arrays.copyOf(encrypted,12));row.setEncryptedPayload(encrypted);row.setKeyId(properties.getKeyId());row.setState(VerificationMailOutbox.State.PENDING);
        row.setNextAttemptAt(clock.instant());row.setExpiresAt(challenge.getExpiresAt());return outbox.saveAndFlush(row).getId();
    }
    public void supersede(UUID challenge){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Superseding requires business transaction");
        outbox.findForUpdateByChallengeId(challenge).forEach(row->{if(row.getState()==VerificationMailOutbox.State.PENDING || row.getState()==VerificationMailOutbox.State.LEASED)erase(row,VerificationMailOutbox.State.EXPIRED);});
        outbox.flush();
    }
    public void dispatch(){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("SMTP must run outside transactions");
        if(!cleanup.purgeOverdueMail())return;
        TransactionTemplate tx=new TransactionTemplate(transactions);
        List<VerificationMailOutbox> batch=tx.execute(status->{
            Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            List<VerificationMailOutbox> rows=outbox.findDispatchBatch(now);
            for(VerificationMailOutbox row:rows){
                EmailVerificationChallenge challenge=challenges.findById(row.getChallengeId()).orElseThrow();
                if(!now.isBefore(row.getExpiresAt()) || !challenge.isActive() || row.getAttemptCount()>=4){erase(row,VerificationMailOutbox.State.EXPIRED);continue;}
                row.setState(VerificationMailOutbox.State.LEASED);row.setLeaseUntil(now.plusSeconds(30));row.setAttemptCount(row.getAttemptCount()+1);
            }
            outbox.flush();return rows;
        });
        for(VerificationMailOutbox row:batch){
            if(row.getState()!=VerificationMailOutbox.State.LEASED)continue;
            boolean delivered=false;
            try{
                if(!properties.getKeyId().equals(row.getKeyId()))throw new IllegalStateException("Unknown mail key");
                Map<?,?> payload=json.readValue(proofs.decrypt(row.getEncryptedPayload(),row.getChallengeId().toString()),Map.class);
                SimpleMailMessage message=new SimpleMailMessage();message.setFrom(properties.getMailFrom());message.setTo((String)payload.get("recipient"));
                message.setSubject("Verify your email");message.setText("Verification code: "+payload.get("code")+"\nChallenge: "+payload.get("challengeId")+"\nThis code expires in ten minutes.");
                JavaMailSender sender=senders.getIfAvailable();if(sender==null)throw new IllegalStateException("Mail unavailable");
                sender.send(message);delivered=true;
            }catch(RuntimeException failure){ /* No raw SMTP payload or address in logs. Retry from durable state. */ }
            boolean sent=delivered;
            tx.executeWithoutResult(status->{
                // Lock just the outbox row; never acquire its owner/challenge locks in reverse order.
                VerificationMailOutbox current=outbox.findForUpdateByChallengeId(row.getChallengeId()).stream().filter(v->v.getId().equals(row.getId())).findFirst().orElse(null);
                if(current==null || current.getState()!=VerificationMailOutbox.State.LEASED || current.getAttemptCount()!=row.getAttemptCount() || !java.util.Objects.equals(current.getLeaseUntil(),row.getLeaseUntil()))return;
                if(sent)erase(current,VerificationMailOutbox.State.SENT);
                else if(current.getAttemptCount()>=4 || !clock.instant().isBefore(current.getExpiresAt()))erase(current,VerificationMailOutbox.State.FAILED);
                else {current.setState(VerificationMailOutbox.State.PENDING);current.setNextAttemptAt(clock.instant().plusSeconds(RETRY_SECONDS[current.getAttemptCount()-1]));current.setLeaseUntil(null);}
                outbox.flush();
            });
        }
    }
    private void erase(VerificationMailOutbox row,VerificationMailOutbox.State state){row.setState(state);row.setEncryptedPayload(null);row.setNonce(null);row.setLeaseUntil(null);}
}
