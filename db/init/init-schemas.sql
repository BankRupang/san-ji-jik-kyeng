-- 각 Spring 서비스 스키마는 Flyway(create-schemas: true)가 자동 생성합니다.
-- 아래는 Flyway 관리 대상이 아닌 외부 서비스용 스키마만 수동 생성합니다.

-- Keycloak: 자체 DB 스키마를 관리하지만 스키마는 미리 생성해야 함
CREATE SCHEMA IF NOT EXISTS keycloak_schema;

-- Langfuse: 자체 DB 스키마를 관리하지만 스키마는 미리 생성해야 함
CREATE SCHEMA IF NOT EXISTS langfuse_schema;
