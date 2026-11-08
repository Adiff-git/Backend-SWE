package com.example.demo.auth.service;

import com.example.demo.auth.service.impl.IdentityAuditServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class IdentityAuditServiceTest {
    @Test void outcomes_shouldExposeOnlyFixedCategoriesAndAggregateCounts(CapturedOutput output){
        IdentityAuditService audit=new IdentityAuditServiceImpl();audit.record(IdentityAuditService.Event.PROOF_REJECTED);audit.record(IdentityAuditService.Event.PROOF_REJECTED);audit.record(IdentityAuditService.Event.MAIL_SENT);
        assertThat(audit.counts()).containsEntry(IdentityAuditService.Event.PROOF_REJECTED,2L).containsEntry(IdentityAuditService.Event.MAIL_SENT,1L);
        assertThat(output.getAll()).contains("Identity outcome PROOF_REJECTED count=2").doesNotContain("@","Bearer ","registrationCredential","codeVerifier");
    }
}
