package com.example.demo.user.integration;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.service.UserService;
import com.example.demo.user.dto.request.UpdateUserRequest;
import com.example.demo.user.testdata.UserTestData;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(properties="user.lock-timeout-ms=250")
class UserServiceIT extends PostgresTestSupport {
    @Autowired UserRepository users;
    @Autowired UserService service;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @AfterEach void cleanup() {
        users.deleteAll();
    }
    @Test void updateUser_shouldNormalizeEmailAndPreserveOmittedValuesAcrossTransactions() {
        Long id=users.saveAndFlush(UserTestData.user("Alice")).getId();
        UpdateUserRequest first=new UpdateUserRequest();
        first.setEmail(" NAME@EXAMPLE.COM ");
        service.updateUser(id,first);
        UpdateUserRequest second=new UpdateUserRequest();
        second.setDisplayName(" New ");
        service.updateUser(id,second);
        assertThat(service.getCurrentUser(id).getEmail()).isEqualTo("name@example.com");
        assertThat(service.getCurrentUser(id).getUsername()).isEqualTo("Alice");
    }
    @Test void updateUser_shouldTimeoutWithoutPartialWritesWhileOtherUserProgresses() throws Exception {
        Long owner=users.saveAndFlush(UserTestData.user("Owner")).getId();
        Long other=users.saveAndFlush(UserTestData.user("Other")).getId();
        java.util.concurrent.CountDownLatch held=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);
        try(java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            java.util.concurrent.Future<?> holder=executor.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status-> {
                service.getUserEntityForUpdate(owner);held.countDown();try {
                    if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Release timeout");
                }
                catch(InterruptedException e) {
                    Thread.currentThread().interrupt();throw new IllegalStateException(e);
                }
                return null;
            }
            ));
            try {
                assertThat(held.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                UpdateUserRequest blocked=new UpdateUserRequest();
                blocked.setDisplayName("Must not persist");
                org.assertj.core.api.Assertions.assertThatThrownBy(()->service.updateUser(owner,blocked)).satisfies(error->assertThat(new com.example.demo.common.exception.ApiErrorFactory().from(error,"/api/me").getStatus()).isEqualTo(409));
                UpdateUserRequest allowed=new UpdateUserRequest();
                allowed.setDisplayName("Independent");
                assertThat(service.updateUser(other,allowed).getDisplayName()).isEqualTo("Independent");
            }
            finally {
                release.countDown();
                holder.get(15,java.util.concurrent.TimeUnit.SECONDS);
            }
        }
        assertThat(service.getCurrentUser(owner).getDisplayName()).isNull();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void updateUser_shouldApplyLatestSuppliedFieldsAfterPessimisticCoordination(boolean sameField) throws Exception {
        Long id=users.saveAndFlush(UserTestData.user("Owner")).getId();
        java.util.concurrent.CountDownLatch firstChanged=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);
        try(java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Future<?> first=executor.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
                UpdateUserRequest one=new UpdateUserRequest();one.setDisplayName("First");service.updateUser(id,one);firstChanged.countDown();
                try { if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Release timeout"); } catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return null;
            }));
            assertThat(firstChanged.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            java.util.concurrent.Future<?> second=executor.submit(()->{UpdateUserRequest two=new UpdateUserRequest();if(sameField)two.setDisplayName("Second");else two.setEmail("new@example.com");return service.updateUser(id,two);});
            release.countDown();first.get(10,java.util.concurrent.TimeUnit.SECONDS);second.get(10,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(service.getCurrentUser(id).getDisplayName()).isEqualTo(sameField?"Second":"First");
        if(!sameField)assertThat(service.getCurrentUser(id).getEmail()).isEqualTo("new@example.com");
    }
    @Test
    void updateUser_shouldProtectConcurrentCaseInsensitiveClaimsAcrossOwners() throws Exception {
        Long first=users.saveAndFlush(UserTestData.user("First")).getId();Long second=users.saveAndFlush(UserTestData.user("Second")).getId();java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
        try(java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Future<Integer> a=executor.submit(()->claim(first,"Shared",start));java.util.concurrent.Future<Integer> b=executor.submit(()->claim(second,"SHARED",start));start.countDown();assertThat(java.util.List.of(a.get(10,java.util.concurrent.TimeUnit.SECONDS),b.get(10,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
    }
    private int claim(Long owner,String name,java.util.concurrent.CountDownLatch start) throws Exception {
        if(!start.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Barrier timeout");
        UpdateUserRequest request=new UpdateUserRequest();request.setUsername(name);
        try {service.updateUser(owner,request);return 200;}catch(RuntimeException failure){return new com.example.demo.common.exception.ApiErrorFactory().from(failure,"/api/me").getStatus();}
    }
}
