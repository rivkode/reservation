package com.reservation.common.test;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 각 서비스의 {@code @DataJpaTest} · {@code @SpringBootTest} 에서 재사용하는 공용
 * MySQL Testcontainer.
 *
 * <p>사용: 테스트 클래스에 {@code @ExtendWith(MysqlContainerExtension.class)} 를 달면
 * JVM 당 한 번 MySQL 컨테이너가 기동되고 {@code spring.datasource.*} 속성이 시스템
 * 프로퍼티로 주입된다. 테스트 클래스 종료 시점에 해당 프로퍼티는 {@code clearProperty}
 * 로 제거되어 동일 JVM 의 다른 테스트(예: 로컬 H2 사용 슬라이스 테스트) 에 상태가
 * 새어 나가지 않는다.
 *
 * <p>컨테이너는 reuse 모드 ({@code .withReuse(true)}) 로 동작해 동일 JVM 내 여러 테스트
 * 클래스가 같은 MySQL 인스턴스를 공유한다. Testcontainers 의
 * {@code ~/.testcontainers.properties} 에 {@code testcontainers.reuse.enable=true} 가
 * 설정된 경우 JVM 재시작 간 재사용까지 가능하다 (CI 에서는 통상 비활성).
 *
 * <p>각 테스트는 자체 Flyway · DDL 로 스키마를 준비하고, 테스트간 격리는 트랜잭션
 * 롤백 ({@code @DataJpaTest} 기본 동작) 또는 명시적 cleanup 으로 유지한다.
 */
public class MysqlContainerExtension implements BeforeAllCallback, AfterAllCallback {

    private static final DockerImageName IMAGE = DockerImageName.parse("mysql:8.0.40");

    private static final String[] MANAGED_PROPERTY_KEYS = {
        "spring.datasource.url",
        "spring.datasource.username",
        "spring.datasource.password",
        "spring.datasource.driver-class-name"
    };

    private static final MySQLContainer<?> CONTAINER = new MySQLContainer<>(IMAGE)
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        System.setProperty("spring.datasource.url", CONTAINER.getJdbcUrl());
        System.setProperty("spring.datasource.username", CONTAINER.getUsername());
        System.setProperty("spring.datasource.password", CONTAINER.getPassword());
        System.setProperty("spring.datasource.driver-class-name", CONTAINER.getDriverClassName());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        // 테스트 클래스 종료 시 본 Extension 이 세팅한 System property 를 비워 다음
        // 테스트 클래스가 의도치 않게 MySQL 자격 증명을 상속하지 않게 한다.
        for (String key : MANAGED_PROPERTY_KEYS) {
            System.clearProperty(key);
        }
    }
}
