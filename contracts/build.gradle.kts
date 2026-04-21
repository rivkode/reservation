// 서비스간 공개 계약 모듈. proto 런타임·이벤트 record 를 다른 모듈의 compileClasspath 로
// 전이해야 하므로 `java-library` 를 적용해 `api(...)` configuration 을 확보한다.
// 실제 proto/이벤트 정의는 이후 PR-0.2 에서 추가된다.
plugins {
    `java-library`
}
