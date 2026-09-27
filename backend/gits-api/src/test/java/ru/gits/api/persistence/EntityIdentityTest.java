package ru.gits.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;

import org.hibernate.proxy.HibernateProxy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import ru.gits.api.support.TestData;
import ru.gits.api.support.TestcontainersConfiguration;
import ru.gits.core.task.TaskVariant;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EntityIdentityTest {

    @Autowired private TestData testData;
    @Autowired private EntityManager em;

    @Test
    void entityEqualsItsLazyProxy() {
        var variantId = testData.sessionTask().getVariant().getId();
        em.flush();
        em.clear();

        TaskVariant loaded = em.find(TaskVariant.class, variantId);
        em.clear();  // detach, so the next lookup yields a fresh, uninitialized proxy
        TaskVariant proxy = em.getReference(TaskVariant.class, variantId);

        assertThat(proxy).isInstanceOf(HibernateProxy.class).isNotSameAs(loaded);
        assertThat(proxy.getClass()).isNotEqualTo(loaded.getClass());
        assertThat(proxy).isEqualTo(loaded).hasSameHashCodeAs(loaded);
        assertThat(loaded).isEqualTo(proxy);
        assertThat(new HashSet<>(List.of(loaded, proxy))).hasSize(1);
    }
}
