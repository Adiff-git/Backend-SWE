package com.example.demo.user.repository;
import com.example.demo.user.entity.Address;
import com.example.demo.user.entity.User;
import com.example.demo.user.testdata.UserTestData;
import com.example.demo.user.integration.PostgresTestSupport;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@DataJpaTest(properties="spring.jpa.properties.hibernate.generate_statistics=true") @AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class AddressRepositoryIT extends PostgresTestSupport {
    @Autowired UserRepository users;
    @Autowired AddressRepository addresses;
    @Autowired jakarta.persistence.EntityManager entityManager;
    private Address address(User owner,boolean flag) {
        Address a=new Address();
        a.setUser(owner);
        a.setRecipientName("Recipient");
        a.setRecipientPhone("0123456789");
        a.setAddressText("Street");
        a.setIsDefault(flag);
        return addresses.saveAndFlush(a);
    }
    @Test void findByUserId_shouldSortDefaultFirstThenIdDescendingAndScopeOwnership() {
        User owner=users.saveAndFlush(UserTestData.user("Owner"));
        User other=users.saveAndFlush(UserTestData.user("Other"));
        Address first=address(owner,true);
        Address last=address(owner,false);
        Address foreign=address(other,true);
        assertThat(addresses.findByUserIdOrderByIsDefaultDescIdDesc(owner.getId())).extracting(Address::getId).containsExactly(first.getId(),last.getId());
        assertThat(addresses.findByIdAndUserId(foreign.getId(),owner.getId())).isEmpty();
    }
    @Test void save_shouldEnforcePartialUniqueDefaultIndex() {
        User owner=users.saveAndFlush(UserTestData.user("Owner"));
        address(owner,true);
        assertThatThrownBy(()->address(owner,true)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test
    void findByIdAndUserId_shouldKeepOwnerLazyAndConverterQueryFree() {
        User owner=users.saveAndFlush(UserTestData.user("Owner"));Long ownerId=owner.getId();Long addressId=address(owner,true).getId();entityManager.clear();
        Address loaded=addresses.findByIdAndUserId(addressId,ownerId).orElseThrow();
        assertThat(jakarta.persistence.Persistence.getPersistenceUtil().isLoaded(loaded,"user")).isFalse();
        org.hibernate.stat.Statistics statistics=entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        long before=statistics.getPrepareStatementCount();new com.example.demo.user.converter.AddressConverter().toResponse(loaded);assertThat(statistics.getPrepareStatementCount()).isEqualTo(before);
    }
}
