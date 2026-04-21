// 공통 Spring 설정 · 로깅 · 예외 표준 · gRPC 인터셉터를 담는 공유 모듈.
// 각 서비스 모듈이 `implementation` 으로 참조하고 일부 타입은 전이 노출이 필요하므로
// `java-library` 를 적용한다. 실제 구성은 이후 PR-0.3 에서 추가된다.
plugins {
    `java-library`
}
