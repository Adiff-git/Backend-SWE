package com.example.demo.user.integration;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.repository.AddressRepository;
import com.example.demo.user.service.AddressService;
import com.example.demo.user.testdata.UserTestData;
import com.example.demo.user.testdata.AddressTestData;
import com.example.demo.user.dto.response.AddressResponse;
import com.example.demo.user.exception.BusinessValidationException;
import com.example.demo.user.exception.ResourceNotFoundException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@SpringBootTest
class AddressServiceIT extends PostgresTestSupport {
    @Autowired UserRepository users;
    @Autowired AddressRepository addresses;
    @Autowired AddressService service;
    @Autowired PlatformTransactionManager transactions;
    @AfterEach void cleanup() {
        addresses.deleteAll();
        users.deleteAll();
    }
    @Test void deleteAddress_shouldPromoteHighestRemainingIdAtRealCommitBoundary() {
        Long id=users.saveAndFlush(UserTestData.user("Owner")).getId();
        AddressResponse first=service.createAddress(id,AddressTestData.request(false));
        service.createAddress(id,AddressTestData.request(false));
        AddressResponse last=service.createAddress(id,AddressTestData.request(false));
        service.deleteAddress(id,first.getId());
        assertThat(service.getAddresses(id).getFirst().getId()).isEqualTo(last.getId());
        assertThat(service.getAddresses(id).getFirst().getIsDefault()).isTrue();
    }
    @Test void createAddress_shouldEnforceCapacityWithoutChangingDefault() {
        Long id=users.saveAndFlush(UserTestData.user("Owner")).getId();
        for(int i=0;i<20;i++) service.createAddress(id,AddressTestData.request(false));
        Long defaultId=service.getAddresses(id).getFirst().getId();
        assertThatThrownBy(()->service.createAddress(id,AddressTestData.request(true))).isInstanceOf(BusinessValidationException.class);
        assertThat(service.getAddresses(id)).hasSize(20);
        assertThat(service.getAddresses(id).getFirst().getId()).isEqualTo(defaultId);
    }
    @Test void getAddress_shouldHideForeignResource() {
        Long owner=users.saveAndFlush(UserTestData.user("Owner")).getId();
        Long foreign=users.saveAndFlush(UserTestData.user("Other")).getId();
        Long address=service.createAddress(owner,AddressTestData.request(null)).getId();
        assertThatThrownBy(()->service.getAddress(foreign,address)).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void createAddress_shouldCoordinateTwoFirstCreates() throws Exception {
        Long id=users.saveAndFlush(UserTestData.user("Owner")).getId();
        CountDownLatch start=new CountDownLatch(1);
        try(java.util.concurrent.ExecutorService executor=Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Future<?> a=executor.submit(()-> {
                await(start);service.createAddress(id,AddressTestData.request(false));
            }
            );
            java.util.concurrent.Future<?> b=executor.submit(()-> {
                await(start);service.createAddress(id,AddressTestData.request(false));
            }
            );
            start.countDown();
            a.get(15,TimeUnit.SECONDS);
            b.get(15,TimeUnit.SECONDS);
        }
        assertThat(service.getAddresses(id)).hasSize(2);
        assertThat(service.getAddresses(id).stream().filter(a->a.getIsDefault()).count()).isEqualTo(1);
    }
    @Test void createAddress_shouldRollbackDefaultSwitchWhenOuterTransactionFails() {
        Long id=users.saveAndFlush(UserTestData.user("Owner")).getId();
        Long old=service.createAddress(id,AddressTestData.request(true)).getId();
        TransactionTemplate tx=new TransactionTemplate(transactions);
        assertThatThrownBy(()->tx.execute(status-> {
            service.createAddress(id,AddressTestData.request(true));throw new IllegalStateException("force rollback after flush");
        }
        )).isInstanceOf(IllegalStateException.class);
        assertThat(service.getAddresses(id)).hasSize(1);
        assertThat(service.getAddresses(id).getFirst().getId()).isEqualTo(old);
        assertThat(service.getAddresses(id).getFirst().getIsDefault()).isTrue();
    }
    @Test void createAddress_shouldNotExceedCapacityUnderConcurrentWrites() throws Exception {
        Long id=users.saveAndFlush(UserTestData.user("Owner")).getId();
        for(int i=0;i<19;i++) service.createAddress(id,AddressTestData.request(false));
        CountDownLatch start=new CountDownLatch(1);
        try(java.util.concurrent.ExecutorService executor=Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> create=()-> {
                await(start);
                try {
                    service.createAddress(id,AddressTestData.request(false));
                    return true;
                }
                catch(BusinessValidationException expected) {
                    return false;
                }
            }
            ;
            java.util.concurrent.Future<Boolean> a=executor.submit(create);
            java.util.concurrent.Future<Boolean> b=executor.submit(create);
            start.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(service.getAddresses(id)).hasSize(20);
    }
    @Test
    void updateAndDelete_shouldHideForeignAddressWithoutMutation() {
        Long owner=users.saveAndFlush(UserTestData.user("Owner")).getId();
        Long other=users.saveAndFlush(UserTestData.user("Other")).getId();
        Long target=service.createAddress(owner,AddressTestData.request(true)).getId();
        com.example.demo.user.dto.request.UpdateAddressRequest update=new com.example.demo.user.dto.request.UpdateAddressRequest();
        update.setRecipientName("Other");update.setRecipientPhone("0123456789");update.setAddressText("Changed");
        assertThatThrownBy(()->service.updateAddress(other,target,update)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->service.deleteAddress(other,target)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.getAddress(owner,target).getRecipientName()).isEqualTo("Recipient");
    }
    @Test
    void updateAddress_shouldOrderConcurrentDefaultSwitches() throws Exception {
        Long owner=users.saveAndFlush(UserTestData.user("Owner")).getId();
        Long a=service.createAddress(owner,AddressTestData.request(true)).getId();
        Long b=service.createAddress(owner,AddressTestData.request(false)).getId();
        CountDownLatch start=new CountDownLatch(1);
        try(java.util.concurrent.ExecutorService executor=Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Future<?> first=executor.submit(()->{await(start);service.updateAddress(owner,a,defaultUpdate());});
            java.util.concurrent.Future<?> second=executor.submit(()->{await(start);service.updateAddress(owner,b,defaultUpdate());});
            start.countDown();first.get(15,TimeUnit.SECONDS);second.get(15,TimeUnit.SECONDS);
        }
        assertThat(service.getAddresses(owner).stream().filter(x->x.getIsDefault()).count()).isEqualTo(1);
    }
    private static com.example.demo.user.dto.request.UpdateAddressRequest defaultUpdate() {
        com.example.demo.user.dto.request.UpdateAddressRequest r=new com.example.demo.user.dto.request.UpdateAddressRequest();r.setRecipientName("Recipient");r.setRecipientPhone("0123456789");r.setAddressText("Street");r.setIsDefault(true);return r;
    }
    @Test
    void deleteAddress_shouldPreserveInvariantDuringConcurrentCreate() throws Exception {
        Long owner=users.saveAndFlush(UserTestData.user("Owner")).getId();
        Long old=service.createAddress(owner,AddressTestData.request(true)).getId();
        CountDownLatch start=new CountDownLatch(1);
        try(java.util.concurrent.ExecutorService executor=Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Future<?> deleting=executor.submit(()->{await(start);service.deleteAddress(owner,old);});
            java.util.concurrent.Future<?> creating=executor.submit(()->{await(start);service.createAddress(owner,AddressTestData.request(null));});
            start.countDown();deleting.get(15,TimeUnit.SECONDS);creating.get(15,TimeUnit.SECONDS);
        }
        assertThat(service.getAddresses(owner)).hasSize(1);
        assertThat(service.getAddresses(owner).getFirst().getIsDefault()).isTrue();
    }
    @Test
    void deleteAddress_shouldRestoreDeletedDefaultAndPromotionOnOuterRollback() {
        Long owner=users.saveAndFlush(UserTestData.user("Owner")).getId();
        Long old=service.createAddress(owner,AddressTestData.request(true)).getId();
        service.createAddress(owner,AddressTestData.request(false));
        TransactionTemplate transaction=new TransactionTemplate(transactions);
        assertThatThrownBy(()->transaction.execute(status->{service.deleteAddress(owner,old);throw new IllegalStateException("Rollback after delete and promotion flush");})).isInstanceOf(IllegalStateException.class);
        assertThat(service.getAddresses(owner)).hasSize(2);
        assertThat(service.getAddresses(owner).getFirst().getId()).isEqualTo(old);
        assertThat(service.getAddresses(owner).getFirst().getIsDefault()).isTrue();
    }
    private static void await(CountDownLatch latch) {
        try {
            if(!latch.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Barrier timeout");
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
